import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api } from '../api/client'
import { endsTheSession, present } from '../api/errorPresentation'
import { useCalibrationStore } from './calibration'
import { persisted } from './persistence'
import type { AdView, AutoPlayStepView, GameView, ReputationView, ShopItemView } from '../api/types'

export type RequestStatus = 'idle' | 'pending' | 'ready' | 'error'

/**
 * What a reload needs to pick a game back up. Only the parts the server cannot tell us again:
 * the id it is keyed by, the state we last saw so the figures are on screen before the board
 * comes back, and the standing, which is client-side because nothing but an `investigate`
 * response carries it and that response costs a turn.
 *
 * The board and the shop are deliberately absent. Listing either costs no turn upstream, so
 * refetching them is cheaper than trusting a copy that may be a turn out of date.
 */
interface StoredGame {
  gameId: string
  game: GameView
  reputation: ReputationView | null
}

/**
 * Per tab, not per browser: a reload keeps the game, a second tab starts its own. Two tabs on one
 * `gameId` would have both read-modify-writing the same session.
 */
const savedGame = persisted<StoredGame>('session', 'mugloar.game')

/** A preference rather than game state, so it outlives the tab. */
const savedAdvisor = persisted<boolean>('local', 'mugloar.advisor')

/**
 * What the last turn did, in the shape the banner needs. One slot rather than one per action:
 * only one turn can be the most recent, so two slots could only ever disagree.
 */
export type TurnOutcome =
  | { kind: 'solve'; success: boolean; message: string }
  | { kind: 'purchase'; success: boolean; item: ShopItemView }
  | { kind: 'investigation' }

export const useGameStore = defineStore('game', () => {
  const calibration = useCalibrationStore()

  const game = ref<GameView | null>(null)
  const ads = ref<AdView[]>([])
  const shopItems = ref<ShopItemView[]>([])
  const startStatus = ref<RequestStatus>('idle')
  const boardStatus = ref<RequestStatus>('idle')
  const shopStatus = ref<RequestStatus>('idle')
  const solvingAdId = ref<string | null>(null)
  const buyingItemId = ref<string | null>(null)
  const investigating = ref(false)
  const autoStepping = ref(false)
  const sessionLost = ref(false)
  const error = ref<ApiError | null>(null)
  const lastOutcome = ref<TurnOutcome | null>(null)
  const advisorEnabled = ref(savedAdvisor.read() ?? false)
  const resuming = ref(false)
  /**
   * A game was remembered and could not be picked up. Not an `ApiError`: the player did nothing
   * but reload, so it is a note about what is missing rather than a failed action, and the panel
   * that says "this game was lost" would be answering a question nobody asked.
   */
  const resumeFailed = ref(false)
  /**
   * The last standing the scouts reported, or null if this game has never paid for one. It is
   * never inferred: nothing else on the wire carries it, so an unscouted game says so rather
   * than showing three zeroes that would look like a measurement.
   */
  const reputation = ref<ReputationView | null>(null)

  /**
   * Whether this server plays a simulated game. A fact about the deployment, so it is read once
   * and never with a game. A failed read leaves it false: showing no caveat on an offline game is
   * a smaller lie than putting one on a live game the player just spent forty turns on.
   */
  const offline = ref(false)

  const started = computed(() => game.value !== null)
  const finished = computed(() => game.value?.finished ?? false)

  /**
   * Why this game is no longer playable, if it isn't. `lost` is the server having forgotten the
   * session — unrecoverable by design, since a session is never re-adopted — and `finished` is
   * the dragon dying, which is the game working. Both end the run, so both replace the board
   * rather than sitting in a banner above one that can no longer be played.
   */
  const ending = computed<'lost' | 'finished' | null>(() => {
    if (game.value === null) {
      return null
    }
    return sessionLost.value ? 'lost' : game.value.finished ? 'finished' : null
  })
  const playable = computed(() => started.value && ending.value === null)

  /** A turn is in flight. Only one may be, whichever action started it — solver included. */
  const acting = computed(
    () =>
      solvingAdId.value !== null ||
      buyingItemId.value !== null ||
      investigating.value ||
      autoStepping.value,
  )
  const busy = computed(
    () => startStatus.value === 'pending' || boardStatus.value === 'pending' || acting.value,
  )

  /**
   * Read what kind of server this is, once, at startup. Deliberately silent on failure: the badge
   * it feeds is a caveat about the score, and an app that refused to start because it could not
   * fetch a caveat would be worse than one that quietly leaves it off.
   */
  async function loadMeta(): Promise<void> {
    try {
      offline.value = (await api.meta()).offline
    } catch {
      offline.value = false
    }
  }

  /**
   * Pick a remembered game back up after a reload. Nothing here spends a turn: the id is ours
   * already, and listing the board and the shop is free, so the only cost of trying is two GETs.
   *
   * The stored state is applied before the fetches so the figures are on screen immediately and
   * the board shows its skeleton rather than the app flashing the start screen. A game that had
   * already ended is restored from the record alone — the server refuses to list ads for one, and
   * the ending panel is the whole of what is left to show.
   *
   * Returns whether a game is now on screen, which is what tells the caller whether the rest of
   * the run — the decision log — is worth restoring alongside it.
   */
  async function resume(): Promise<boolean> {
    const saved = savedGame.read()
    if (!saved || typeof saved.gameId !== 'string' || saved.game?.gameId !== saved.gameId) {
      savedGame.clear()
      return false
    }

    game.value = saved.game
    reputation.value = saved.reputation
    if (saved.game.finished) {
      return true
    }

    resuming.value = true
    boardStatus.value = 'pending'
    try {
      await Promise.all([refreshAds(), refreshShop()])
    } finally {
      resuming.value = false
    }

    // The server let the session go while the tab was closed. That is the one outcome the ending
    // panel must not claim, so the record is dropped and the player is offered a new game.
    if (sessionLost.value) {
      forget()
      resumeFailed.value = true
      return false
    }
    return true
  }

  /** Back to before a game, without touching what is remembered across games. */
  function forget(): void {
    game.value = null
    ads.value = []
    shopItems.value = []
    reputation.value = null
    sessionLost.value = false
    error.value = null
    lastOutcome.value = null
    startStatus.value = 'idle'
    boardStatus.value = 'idle'
    shopStatus.value = 'idle'
  }

  async function startGame(): Promise<void> {
    startStatus.value = 'pending'
    boardStatus.value = 'idle'
    shopStatus.value = 'idle'
    sessionLost.value = false
    resumeFailed.value = false
    error.value = null
    lastOutcome.value = null
    ads.value = []
    shopItems.value = []
    reputation.value = null
    try {
      game.value = await api.startGame()
      startStatus.value = 'ready'
      calibration.noteGame()
      // The shop is static for the life of a game and listing it costs no turn, so it is fetched
      // once, up front: affordability is then answerable the moment the player looks.
      await Promise.all([refreshAds(), refreshShop()])
    } catch (e) {
      game.value = null
      startStatus.value = 'error'
      error.value = classify(e)
    }
  }

  async function refreshAds(): Promise<void> {
    const current = game.value
    // Nothing to reconcile against a session the server has forgotten, and the retry would only
    // fail the same way. The ending panel is the way out of that state, not another fetch.
    if (!current || sessionLost.value) {
      return
    }
    boardStatus.value = 'pending'
    try {
      const board = await api.listAds(current.gameId)
      game.value = board.game
      ads.value = board.ads
      boardStatus.value = 'ready'
    } catch (e) {
      boardStatus.value = 'error'
      fail(e)
    }
  }

  /** The catalogue only, deliberately: the state riding along is older than a turn in flight. */
  async function refreshShop(): Promise<void> {
    const current = game.value
    if (!current || sessionLost.value) {
      return
    }
    shopStatus.value = 'pending'
    try {
      shopItems.value = (await api.listShop(current.gameId)).items
      shopStatus.value = 'ready'
    } catch (e) {
      shopStatus.value = 'error'
      fail(e)
    }
  }

  /**
   * Optimistic in the only way this game allows: whether the ad succeeds is unknowable here, but
   * the mechanical half of a turn is not. The ad leaves the board, the turn counter moves and
   * every other ad ages by one. The authoritative state replaces the guess on the way back, and a
   * failure restores the snapshot untouched.
   */
  async function solve(adId: string): Promise<void> {
    const current = game.value
    if (!playable.value || !current || acting.value) {
      return
    }
    const previousGame = current
    const previousAds = ads.value
    // Read before the optimistic update takes the ad off the board: its label and the estimate
    // it carried are half of the calibration record, and the outcome is the other half.
    const attempted = previousAds.find((ad) => ad.adId === adId)

    error.value = null
    lastOutcome.value = null
    solvingAdId.value = adId
    game.value = { ...current, turn: current.turn + 1 }
    ads.value = ageBoard(previousAds, adId)

    try {
      const result = await api.solve(current.gameId, adId)
      game.value = result.game
      lastOutcome.value = { kind: 'solve', success: result.success, message: result.message }
      if (attempted) {
        calibration.record({
          label: attempted.probability,
          tier: attempted.probabilityTier,
          predicted: attempted.successProbability,
          success: result.success,
        })
      }
      if (!result.game.finished) {
        await refreshAds()
      }
    } catch (e) {
      game.value = previousGame
      ads.value = previousAds
      const failure = fail(e)
      // A stale board is what causes both of these, and a board fetch costs no turn upstream.
      if (failure.code === 'AD_NOT_AVAILABLE' || failure.code === 'INVALID_ACTION') {
        await refreshAds()
      }
    } finally {
      solvingAdId.value = null
    }
  }

  /**
   * A purchase predicts more than a solve, because more of it is known: the price is fixed, the
   * effect was measured, and the turn ages the board exactly as a solve does. That leaves only
   * the shop's own refusal unpredictable, and the server refuses everything it can see coming.
   */
  async function buy(itemId: string): Promise<void> {
    const current = game.value
    const item = shopItems.value.find((candidate) => candidate.id === itemId)
    if (!playable.value || !current || acting.value || !item || item.cost > current.gold) {
      return
    }
    const previousGame = current
    const previousAds = ads.value

    error.value = null
    lastOutcome.value = null
    buyingItemId.value = itemId
    game.value = {
      ...current,
      gold: current.gold - item.cost,
      lives: current.lives + item.livesGained,
      level: current.level + item.levelsGained,
      turn: current.turn + 1,
    }
    ads.value = ageBoard(previousAds)

    try {
      const result = await api.buy(current.gameId, itemId)
      game.value = result.game
      lastOutcome.value = { kind: 'purchase', success: result.success, item }
      if (!result.game.finished) {
        await refreshAds()
      }
    } catch (e) {
      game.value = previousGame
      ads.value = previousAds
      fail(e)
    } finally {
      buyingItemId.value = null
    }
  }

  /**
   * Spend a turn on scouting. It is the only move that cannot cost a life, which is what makes it
   * worth offering by hand: when every ad on the board is a bad bet, a fresh board is worth more
   * than the best of them. It ages every ad exactly as any other turn does, so the board is
   * predicted the same way a purchase is.
   */
  async function investigate(): Promise<void> {
    const current = game.value
    if (!playable.value || !current || acting.value) {
      return
    }
    const previousGame = current
    const previousAds = ads.value

    error.value = null
    lastOutcome.value = null
    investigating.value = true
    game.value = { ...current, turn: current.turn + 1 }
    ads.value = ageBoard(previousAds)

    try {
      const result = await api.investigate(current.gameId)
      game.value = result.game
      reputation.value = result.reputation
      lastOutcome.value = { kind: 'investigation' }
      if (!result.game.finished) {
        await refreshAds()
      }
    } catch (e) {
      game.value = previousGame
      ads.value = previousAds
      fail(e)
    } finally {
      investigating.value = false
    }
  }

  /**
   * One turn taken by the solver. There is nothing to predict optimistically here — which move it
   * will pick is exactly what the call returns — so the state is only ever written from the
   * response, and a failure leaves everything as it was.
   *
   * Unlike `solve` and `buy` this throws instead of parking the failure in `error`: the auto-play
   * loop has to tell a rate limit, which is a wait, from a failure, which is a halt, and it shows
   * either one next to the log rather than in the app-wide banner.
   */
  async function autoPlayStep(refreshBoard: boolean): Promise<AutoPlayStepView> {
    const current = game.value
    if (!playable.value || !current || acting.value) {
      throw new ApiError('INVALID_ACTION', 'The dragon cannot take a turn right now.', 0)
    }
    autoStepping.value = true
    try {
      const step = await api.autoPlayStep(current.gameId)
      game.value = step.game
      // The solver passes for the turn rather than for the answer, but the answer was paid for
      // all the same, so the crests fill in from an auto-played scout exactly as from a manual one.
      if (step.reputation) {
        reputation.value = step.reputation
      }
      recordSolverAttempt(step)
      if (refreshBoard && !step.game.finished) {
        await refreshAds()
      }
      return step
    } catch (e) {
      // Classified, not parked: the loop shows the failure next to the log, but the two codes
      // that end the session have to land on the state here or the loop would offer to resume a
      // game that no longer exists.
      throw classify(e)
    } finally {
      autoStepping.value = false
    }
  }

  /**
   * The solver's turns feed the same ledger the player's do. The ad is read off the decision
   * rather than off the board, which at max speed is a turn or more out of date.
   */
  function recordSolverAttempt(step: AutoPlayStepView): void {
    const { move, targetId, ads: weighed } = step.decision
    if (move !== 'SOLVE_AD' || targetId === null) {
      return
    }
    const chosen = weighed.find((option) => option.adId === targetId)
    if (chosen) {
      calibration.record({
        label: chosen.probability,
        tier: chosen.probabilityTier,
        predicted: chosen.successProbability,
        success: step.succeeded,
      })
    }
  }

  function toggleAdvisor(): void {
    advisorEnabled.value = !advisorEnabled.value
  }

  // Remembered as it changes rather than on unload: `beforeunload` is unreliable on mobile, where
  // a tab is suspended and killed without one, and every write here is a few hundred bytes.
  watch([game, reputation], ([current, standing]) => {
    if (current === null) {
      savedGame.clear()
    } else {
      savedGame.write({ gameId: current.gameId, game: current, reputation: standing })
    }
  })

  watch(advisorEnabled, (on) => savedAdvisor.write(on))

  function dismissError(): void {
    error.value = null
  }

  /**
   * Record what a failure means for the game itself. A forgotten session cannot be recovered, and
   * an upstream that says the game is over is describing a fact we had not caught up with — both
   * would otherwise leave the player looking at a board they can no longer play.
   *
   * Callers roll the optimistic state back before calling this, so `game` is already the last
   * state we actually saw from the server.
   */
  function classify(e: unknown): ApiError {
    const failure = asApiError(e)
    if (endsTheSession(failure.code)) {
      sessionLost.value = true
    } else if (failure.code === 'GAME_OVER' && game.value) {
      game.value = { ...game.value, finished: true }
    }
    return failure
  }

  /**
   * Classify, then show. A terminal failure is deliberately not parked in `error`: the panel that
   * replaces the board says the same thing with somewhere to go, and a banner above it would only
   * repeat itself.
   */
  function fail(e: unknown): ApiError {
    const failure = classify(e)
    error.value = present(failure.code).severity === 'terminal' ? null : failure
    return failure
  }

  return {
    game,
    ads,
    offline,
    shopItems,
    startStatus,
    boardStatus,
    shopStatus,
    solvingAdId,
    buyingItemId,
    investigating,
    autoStepping,
    reputation,
    sessionLost,
    error,
    lastOutcome,
    advisorEnabled,
    resuming,
    resumeFailed,
    started,
    finished,
    ending,
    playable,
    acting,
    busy,
    resume,
    startGame,
    refreshAds,
    refreshShop,
    solve,
    buy,
    investigate,
    autoPlayStep,
    loadMeta,
    toggleAdvisor,
    dismissError,
  }
})

function ageBoard(board: AdView[], solvedAdId?: string): AdView[] {
  // The server never returns an ad that has run out, so the prediction does not invent one.
  return board.filter((ad) => ad.adId !== solvedAdId && ad.expiresIn > 1).map(aged)
}

function aged(ad: AdView): AdView {
  const expiresIn = ad.expiresIn - 1
  // The one server-side rule mirrored here, so no card can show its last turn without the badge.
  const expiring = expiresIn <= 1 && !ad.flags.includes('EXPIRING_NEXT_TURN')
  return {
    ...ad,
    expiresIn,
    flags: expiring ? [...ad.flags, 'EXPIRING_NEXT_TURN'] : ad.flags,
  }
}

function asApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError('INTERNAL_ERROR', 'Something went wrong. Try again.', 0, { cause: e })
}
