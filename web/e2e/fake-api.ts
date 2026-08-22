import type { Page, Route } from '@playwright/test'
import type {
  AdOptionView,
  AdView,
  AutoPlayStepView,
  ErrorCode,
  GameView,
  ShopItemView,
} from '../src/api/types'

/**
 * The backend, small enough to live in the test and honest enough to be worth playing against.
 * It is typed with the same response records the app is, so a change on the Java side that the
 * hand-written TypeScript mirrors have not caught up with breaks the typecheck here too.
 *
 * It is a state machine rather than a bag of canned replies: a move spends a turn, a solved ad
 * leaves the board, every other ad ages, and lives run out. That is what lets a spec drive a
 * whole game to its end through the real stores, instead of asserting that a stub was called.
 */

/** An RFC 9457 problem, as the API renders one. */
export interface Problem {
  status: number
  code: ErrorCode
  detail: string
}

export interface FakeApiOptions {
  lives?: number
  gold?: number
  level?: number
  /**
   * Outcomes for successive auto-play turns, in order; once exhausted, every turn succeeds.
   * Scripted rather than rolled, because "run until the dragon dies" has to end the same way on
   * all three engines or the assertion is a coin toss.
   */
  autoPlay?: boolean[]
}

const BOARD_SIZE = 6

/** Shapes lifted from live responses. This order is the board's order, and the specs rely on it. */
const CATALOGUE: ReadonlyArray<Omit<AdView, 'adId' | 'expiresIn' | 'flags'>> = [
  {
    message: 'Escort a merchant caravan to Ravenhold',
    reward: 42,
    encrypted: false,
    probability: 'Sure thing',
    probabilityTier: 'SAFE',
    successProbability: 0.86,
    expectedValue: 36.1,
  },
  {
    message: 'Retrieve a stolen ledger from the counting house',
    reward: 18,
    encrypted: true,
    probability: 'Piece of cake',
    probabilityTier: 'SAFE',
    successProbability: 0.83,
    expectedValue: 14.9,
  },
  {
    message: 'Settle a debt with the harbour guild',
    reward: 65,
    encrypted: false,
    probability: 'Gamble',
    probabilityTier: 'EVEN',
    successProbability: 0.44,
    expectedValue: 28.6,
  },
  {
    message: 'Clear the sewers under the old quarter',
    reward: 27,
    encrypted: false,
    probability: 'Walk in the park',
    probabilityTier: 'SAFE',
    successProbability: 0.79,
    expectedValue: 21.3,
  },
  {
    message: 'Steal the crown jewels from the state vault',
    reward: 240,
    encrypted: false,
    probability: 'Suicide mission',
    probabilityTier: 'DOOMED',
    successProbability: 0.05,
    expectedValue: 12,
  },
  {
    message: 'Deliver a sealed writ to the magistrate',
    reward: 33,
    encrypted: false,
    probability: 'Quite likely',
    probabilityTier: 'FAVOURABLE',
    successProbability: 0.68,
    expectedValue: 22.4,
  },
]

const SHOP: ShopItemView[] = [
  { id: 'hpot', name: 'Healing potion', cost: 50, livesGained: 1, levelsGained: 0 },
  { id: 'cs', name: 'Claw Sharpening', cost: 100, livesGained: 0, levelsGained: 1 },
]

/** A hand-played solve is judged on the odds the ad advertised. */
const SUCCEEDS_ABOVE = 0.6

export class FakeApi {
  readonly gameId = 'e2eGAME1'

  private game: GameView
  private board: AdView[] = []
  private serial = 0
  private readonly reputation = { people: 4.2, state: -1.5, underworld: 0.8 }
  private readonly autoPlay: boolean[]
  private autoPlayTurn = 0
  private readonly injected: { match: RegExp; problem: Problem; times: number }[] = []

  constructor(options: FakeApiOptions = {}) {
    this.game = {
      gameId: this.gameId,
      lives: options.lives ?? 3,
      gold: options.gold ?? 0,
      level: options.level ?? 0,
      score: 0,
      turn: 0,
      finished: false,
    }
    this.autoPlay = options.autoPlay ?? []
    this.deal()
  }

  /** Serve every `/api` request this page makes. Nothing else is intercepted. */
  async install(page: Page): Promise<void> {
    await page.route('**/api/**', (route) => this.handle(route))
  }

  /**
   * Fail the next `times` requests whose path matches, with a problem body. This is how a spec
   * puts the app in front of an upstream failure without waiting for a real one.
   */
  failNext(match: RegExp, problem: Problem, times = 1): void {
    this.injected.push({ match, problem, times })
  }

  /** The state the fake believes in, for a spec that wants to assert against it. */
  get state(): GameView {
    return { ...this.game }
  }

  private async handle(route: Route): Promise<void> {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const method = request.method()

    const failure = this.injected.find((entry) => entry.times > 0 && entry.match.test(path))
    if (failure) {
      failure.times -= 1
      return this.problem(route, failure.problem)
    }

    if (method === 'POST' && path === '/api/games') {
      return this.json(route, this.game)
    }

    const scoped = path.match(/^\/api\/games\/([^/]+)(.*)$/)
    if (!scoped) {
      return this.unrouted(route, method, path)
    }

    const [, gameId, rest] = scoped
    if (gameId !== this.gameId) {
      return this.problem(route, {
        status: 404,
        code: 'SESSION_EXPIRED',
        detail: 'This game is no longer being tracked.',
      })
    }

    if (method === 'GET' && rest === '/ads') {
      return this.json(route, { game: this.game, ads: this.board })
    }
    if (method === 'GET' && rest === '/shop') {
      return this.json(route, { game: this.game, items: SHOP })
    }
    if (method === 'POST' && rest === '/investigate') {
      this.spendTurn()
      return this.json(route, { game: this.game, reputation: this.reputation })
    }
    if (method === 'POST' && rest === '/autoplay/step') {
      return this.json(route, this.takeSolverTurn())
    }

    const solve = rest.match(/^\/ads\/([^/]+)\/solve$/)
    if (method === 'POST' && solve) {
      return this.solve(route, solve[1])
    }

    const buy = rest.match(/^\/shop\/([^/]+)\/buy$/)
    if (method === 'POST' && buy) {
      return this.buy(route, buy[1])
    }

    return this.unrouted(route, method, path)
  }

  private async solve(route: Route, adId: string): Promise<void> {
    const ad = this.board.find((candidate) => candidate.adId === adId)
    if (!ad) {
      return this.problem(route, {
        status: 409,
        code: 'AD_NOT_AVAILABLE',
        detail: 'That job is no longer on the board.',
      })
    }
    const success = ad.successProbability >= SUCCEEDS_ABOVE
    this.board = this.board.filter((candidate) => candidate.adId !== adId)
    this.resolve(ad.reward, success)
    return this.json(route, {
      game: this.game,
      adId,
      success,
      message: success ? 'You successfully solved the mission!' : 'You failed to solve the mission!',
    })
  }

  private async buy(route: Route, itemId: string): Promise<void> {
    const item = SHOP.find((candidate) => candidate.id === itemId)
    if (!item) {
      return this.problem(route, {
        status: 409,
        code: 'ITEM_NOT_AVAILABLE',
        detail: 'The shop no longer stocks that.',
      })
    }
    // A refused sale is a 200 with the turn spent all the same, exactly as upstream behaves.
    const affordable = item.cost <= this.game.gold
    if (affordable) {
      this.game = {
        ...this.game,
        gold: this.game.gold - item.cost,
        lives: this.game.lives + item.livesGained,
        level: this.game.level + item.levelsGained,
      }
    }
    this.spendTurn()
    return this.json(route, { game: this.game, itemId, success: affordable })
  }

  /** One solver turn: it takes the board's best bet, and the script decides how that went. */
  private takeSolverTurn(): AutoPlayStepView {
    const ranked = [...this.board].sort(
      (a, b) => b.reward * b.successProbability - a.reward * a.successProbability,
    )
    const chosen = ranked[0]
    const success = this.autoPlay[this.autoPlayTurn] ?? true
    this.autoPlayTurn += 1

    const options: AdOptionView[] = ranked.map((ad, index) => ({
      adId: ad.adId,
      message: ad.message,
      reward: ad.reward,
      expiresIn: ad.expiresIn,
      probability: ad.probability,
      probabilityTier: ad.probabilityTier,
      successProbability: ad.successProbability,
      score: Math.round(ad.reward * ad.successProbability * 10) / 10,
      verdict: index === 0 ? 'CHOSEN' : 'OUTRANKED',
    }))

    this.board = this.board.filter((candidate) => candidate.adId !== chosen.adId)
    this.resolve(chosen.reward, success)

    return {
      game: this.game,
      decision: {
        move: 'SOLVE_AD',
        targetId: chosen.adId,
        reason: 'BEST_RISK_ADJUSTED_AD',
        ads: options,
        items: SHOP.map((item) => ({
          itemId: item.id,
          name: item.name,
          cost: item.cost,
          livesGained: item.livesGained,
          levelsGained: item.levelsGained,
          verdict: item.cost <= this.game.gold ? 'NOT_NEEDED' : 'UNAFFORDABLE',
        })),
      },
      succeeded: success,
      message: success ? 'You successfully solved the mission!' : 'You failed to solve the mission!',
      reputation: null,
    }
  }

  /** What an attempted job did to the game, and then what the turn did to the board. */
  private resolve(reward: number, success: boolean): void {
    this.game = success
      ? { ...this.game, gold: this.game.gold + reward, score: this.game.score + reward }
      : { ...this.game, lives: this.game.lives - 1 }
    this.spendTurn()
  }

  private spendTurn(): void {
    this.game = {
      ...this.game,
      turn: this.game.turn + 1,
      finished: this.game.lives <= 0,
    }
    this.age()
    this.deal()
  }

  private age(): void {
    this.board = this.board
      .filter((ad) => ad.expiresIn > 1)
      .map((ad) => {
        const expiresIn = ad.expiresIn - 1
        return { ...ad, expiresIn, flags: expiresIn <= 1 ? ['EXPIRING_NEXT_TURN'] : [] }
      })
  }

  private deal(): void {
    while (this.board.length < BOARD_SIZE) {
      const template = CATALOGUE[this.serial % CATALOGUE.length]
      this.serial += 1
      this.board.push({
        ...template,
        // Numbered, because the catalogue cycles: without it a refill could put the same wording
        // back on the board and "the job we solved is gone" would be untestable.
        message: `${template.message} (job ${this.serial})`,
        adId: `ad-${this.serial}`,
        expiresIn: 3 + (this.serial % 4),
        flags: [],
      })
    }
  }

  private async json(route: Route, body: unknown): Promise<void> {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(body),
    })
  }

  private async problem(route: Route, problem: Problem): Promise<void> {
    await route.fulfill({
      status: problem.status,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'about:blank',
        title: 'Problem',
        status: problem.status,
        detail: problem.detail,
        code: problem.code,
      }),
    })
  }

  /** A path the fake does not know is a bug in the test, so it is loud rather than plausible. */
  private async unrouted(route: Route, method: string, path: string): Promise<void> {
    return this.problem(route, {
      status: 404,
      code: 'GAME_NOT_FOUND',
      detail: `The fake API has no route for ${method} ${path}.`,
    })
  }
}
