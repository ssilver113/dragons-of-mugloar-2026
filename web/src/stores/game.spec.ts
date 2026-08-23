import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { useGameStore } from './game'
import { useCalibrationStore } from './calibration'
import { aDecision, aGame, aStep, anAd, anAdOption, anItem, problem } from '../test/fixtures'
import type { AdView, AutoPlayStepView, GameView, ShopItemView, SolveResultView } from '../api/types'

/** What the fake server currently believes; tests move it to set up the next response. */
const upstream = {
  game: aGame(),
  ads: [] as AdView[],
  items: [] as ShopItemView[],
}

function stubGame(): void {
  server.use(
    http.post('/api/games', () => HttpResponse.json(upstream.game)),
    http.get('/api/games/:gameId/ads', () =>
      HttpResponse.json({ game: upstream.game, ads: upstream.ads }),
    ),
    http.get('/api/games/:gameId/shop', () =>
      HttpResponse.json({ game: upstream.game, items: upstream.items }),
    ),
  )
}

function stubBuy(game: GameView, success = true): void {
  server.use(
    http.post('/api/games/:gameId/shop/:itemId/buy', ({ params }) =>
      HttpResponse.json({ game, itemId: params.itemId, success }),
    ),
  )
}

function stubSolve(result: Partial<SolveResultView> & { game: GameView }): void {
  server.use(
    http.post('/api/games/:gameId/ads/:adId/solve', ({ params }) =>
      HttpResponse.json({
        adId: params.adId,
        success: true,
        message: 'You successfully solved the mission!',
        ...result,
      }),
    ),
  )
}

function stubAutoPlayStep(step: AutoPlayStepView): void {
  server.use(http.post('/api/games/:gameId/autoplay/step', () => HttpResponse.json(step)))
}

async function startedStore() {
  const store = useGameStore()
  stubGame()
  await store.startGame()
  return store
}

beforeEach(() => {
  setActivePinia(createPinia())
  upstream.game = aGame()
  upstream.ads = [anAd()]
  upstream.items = [anItem()]
})

describe('starting a game', () => {
  it('loads the state and the board in one action', async () => {
    upstream.ads = [anAd({ adId: 'a1' }), anAd({ adId: 'a2' })]
    const store = await startedStore()

    expect(store.started).toBe(true)
    expect(store.game).toMatchObject({ gameId: 'kZUyeMSK', lives: 3 })
    expect(store.ads).toHaveLength(2)
    expect(store.startStatus).toBe('ready')
    expect(store.boardStatus).toBe('ready')
  })

  it('loads the shop up front, because listing it costs no turn', async () => {
    upstream.items = [anItem({ id: 'hpot' }), anItem({ id: 'cs' })]
    const store = await startedStore()

    expect(store.shopItems.map((item) => item.id)).toEqual(['hpot', 'cs'])
    expect(store.shopStatus).toBe('ready')
  })

  it('leaves no half-started game behind when the request fails', async () => {
    const store = useGameStore()
    server.use(
      http.post('/api/games', () => problem(503, 'UPSTREAM_UNAVAILABLE', 'Not responding.')),
    )

    await store.startGame()

    expect(store.started).toBe(false)
    expect(store.startStatus).toBe('error')
    expect(store.error).toMatchObject({ code: 'UPSTREAM_UNAVAILABLE' })
  })

  it('clears the previous game before starting the next one', async () => {
    const store = await startedStore()
    server.use(
      http.post('/api/games', () => problem(503, 'UPSTREAM_UNAVAILABLE', 'Not responding.')),
    )

    await store.startGame()

    expect(store.ads).toEqual([])
    expect(store.game).toBeNull()
  })
})

describe('solving an ad', () => {
  beforeEach(() => {
    upstream.ads = [
      anAd({ adId: 'target', expiresIn: 7 }),
      anAd({ adId: 'ages', expiresIn: 3 }),
      anAd({ adId: 'runs-out', expiresIn: 1 }),
    ]
  })

  it('predicts the mechanical half of the turn before the server answers', async () => {
    const store = await startedStore()
    stubSolve({ game: aGame({ gold: 15, score: 15, turn: 1 }) })

    const pending = store.solve('target')

    expect(store.game?.turn).toBe(1)
    expect(store.solvingAdId).toBe('target')
    expect(store.ads.map((ad) => ad.adId)).toEqual(['ages'])
    expect(store.ads[0]?.expiresIn).toBe(2)
    await pending
  })

  it('flags an ad that the predicted turn pushes onto its last life', async () => {
    upstream.ads = [anAd({ adId: 'target' }), anAd({ adId: 'ages', expiresIn: 2, flags: [] })]
    const store = await startedStore()
    stubSolve({ game: aGame({ turn: 1 }) })

    const pending = store.solve('target')

    expect(store.ads[0]?.flags).toEqual(['EXPIRING_NEXT_TURN'])
    await pending
  })

  it('replaces the prediction with the server state and refetches the board', async () => {
    const store = await startedStore()
    stubSolve({ game: aGame({ gold: 15, score: 15, turn: 1 }) })
    upstream.game = aGame({ gold: 15, score: 15, turn: 1 })
    upstream.ads = [anAd({ adId: 'fresh' })]

    await store.solve('target')

    expect(store.game).toMatchObject({ gold: 15, score: 15, turn: 1 })
    expect(store.ads.map((ad) => ad.adId)).toEqual(['fresh'])
    expect(store.lastOutcome).toEqual({
      kind: 'solve',
      success: true,
      message: 'You successfully solved the mission!',
    })
    expect(store.solvingAdId).toBeNull()
  })

  it('rolls the board and the counters back when the attempt fails to reach the server', async () => {
    const store = await startedStore()
    const before = [...store.ads]
    server.use(http.post('/api/games/:gameId/ads/:adId/solve', () => HttpResponse.error()))

    await store.solve('target')

    expect(store.game?.turn).toBe(0)
    expect(store.ads).toEqual(before)
    expect(store.error).toMatchObject({ code: 'NETWORK_ERROR' })
    expect(store.solvingAdId).toBeNull()
  })

  it('refetches the board when the ad turns out to be gone from it', async () => {
    const store = await startedStore()
    server.use(
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        problem(409, 'AD_NOT_AVAILABLE', 'That ad is no longer on the board.'),
      ),
    )
    upstream.ads = [anAd({ adId: 'fresh' })]

    await store.solve('target')

    expect(store.error).toMatchObject({ code: 'AD_NOT_AVAILABLE' })
    expect(store.ads.map((ad) => ad.adId)).toEqual(['fresh'])
  })

  it('stops at the final state instead of fetching a board no one can play', async () => {
    const store = await startedStore()
    stubSolve({ game: aGame({ lives: 0, turn: 1, finished: true }), success: false })
    upstream.ads = [anAd({ adId: 'never-fetched' })]

    await store.solve('target')

    expect(store.finished).toBe(true)
    expect(store.ads.map((ad) => ad.adId)).not.toContain('never-fetched')
  })

  it('ignores a second attempt while one is still in flight', async () => {
    const store = await startedStore()
    stubSolve({ game: aGame({ turn: 1 }) })

    const pending = store.solve('target')
    await store.solve('ages')

    expect(store.ads.map((ad) => ad.adId)).toEqual(['ages'])
    await pending
  })

  it('refuses to spend a turn on a game that is already over', async () => {
    const store = await startedStore()
    upstream.game = aGame({ lives: 0, finished: true })
    await store.refreshAds()

    await store.solve('target')

    expect(store.solvingAdId).toBeNull()
    expect(store.lastOutcome).toBeNull()
  })
})

describe('refreshing the board', () => {
  it('records the failure without discarding the board already on screen', async () => {
    const store = await startedStore()
    const before = [...store.ads]
    server.use(
      http.get('/api/games/:gameId/ads', () =>
        problem(503, 'UPSTREAM_UNAVAILABLE', 'Not responding.'),
      ),
    )

    await store.refreshAds()

    expect(store.boardStatus).toBe('error')
    expect(store.ads).toEqual(before)
    expect(store.error).toMatchObject({ code: 'UPSTREAM_UNAVAILABLE' })
  })

  it('does nothing before a game exists', async () => {
    const store = useGameStore()

    await store.refreshAds()

    expect(store.boardStatus).toBe('idle')
  })
})

describe('buying an item', () => {
  beforeEach(() => {
    upstream.game = aGame({ gold: 120 })
    upstream.ads = [anAd({ adId: 'ages', expiresIn: 3 }), anAd({ adId: 'runs-out', expiresIn: 1 })]
    upstream.items = [
      anItem({ id: 'cs', cost: 100, levelsGained: 1 }),
      anItem({ id: 'hpot', cost: 50, livesGained: 1, levelsGained: 0 }),
      anItem({ id: 'wingpotmax', cost: 300, levelsGained: 2 }),
    ]
  })

  it('predicts the spend, the effect and the aged board before the server answers', async () => {
    const store = await startedStore()
    stubBuy(aGame({ gold: 20, level: 1, turn: 1 }))

    const pending = store.buy('cs')

    expect(store.game).toMatchObject({ gold: 20, level: 1, lives: 3, turn: 1 })
    expect(store.buyingItemId).toBe('cs')
    expect(store.ads.map((ad) => ad.adId)).toEqual(['ages'])
    expect(store.ads[0]?.expiresIn).toBe(2)
    await pending
  })

  it('predicts a life rather than a level for the potion', async () => {
    const store = await startedStore()
    stubBuy(aGame({ gold: 70, lives: 4, turn: 1 }))

    const pending = store.buy('hpot')

    expect(store.game).toMatchObject({ gold: 70, lives: 4, level: 0 })
    await pending
  })

  it('replaces the prediction with the server state and refetches the board', async () => {
    const store = await startedStore()
    stubBuy(aGame({ gold: 20, level: 1, turn: 1 }))
    upstream.game = aGame({ gold: 20, level: 1, turn: 1 })
    upstream.ads = [anAd({ adId: 'fresh' })]

    await store.buy('cs')

    expect(store.game).toMatchObject({ gold: 20, level: 1, turn: 1 })
    expect(store.ads.map((ad) => ad.adId)).toEqual(['fresh'])
    expect(store.lastOutcome).toMatchObject({ kind: 'purchase', success: true })
    expect(store.buyingItemId).toBeNull()
  })

  it('reports a refusal the shop charged a turn for, without rolling anything back', async () => {
    const store = await startedStore()
    stubBuy(aGame({ gold: 120, turn: 1 }), false)
    upstream.game = aGame({ gold: 120, turn: 1 })

    await store.buy('cs')

    expect(store.lastOutcome).toMatchObject({ kind: 'purchase', success: false })
    expect(store.game).toMatchObject({ gold: 120, turn: 1 })
    expect(store.error).toBeNull()
  })

  it('rolls the purse and the board back when the request fails', async () => {
    const store = await startedStore()
    const before = [...store.ads]
    server.use(http.post('/api/games/:gameId/shop/:itemId/buy', () => HttpResponse.error()))

    await store.buy('cs')

    expect(store.game).toMatchObject({ gold: 120, level: 0, turn: 0 })
    expect(store.ads).toEqual(before)
    expect(store.error).toMatchObject({ code: 'NETWORK_ERROR' })
    expect(store.buyingItemId).toBeNull()
  })

  it('never sends a purchase the purse cannot cover', async () => {
    const store = await startedStore()

    await store.buy('wingpotmax')

    expect(store.game).toMatchObject({ gold: 120, turn: 0 })
    expect(store.buyingItemId).toBeNull()
    expect(store.lastOutcome).toBeNull()
  })

  it('ignores an item the shop never listed', async () => {
    const store = await startedStore()

    await store.buy('sword')

    expect(store.game).toMatchObject({ turn: 0 })
  })

  it('will not buy while a solve is still in flight', async () => {
    const store = await startedStore()
    server.use(
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        HttpResponse.json({ game: aGame({ gold: 120, turn: 1 }), adId: 'ages', success: true, message: 'Done' }),
      ),
    )

    const pending = store.solve('ages')
    await store.buy('cs')

    expect(store.buyingItemId).toBeNull()
    expect(store.game?.gold).toBe(120)
    await pending
  })

  it('refuses to spend a turn on a game that is already over', async () => {
    const store = await startedStore()
    upstream.game = aGame({ gold: 120, lives: 0, finished: true })
    await store.refreshAds()

    await store.buy('cs')

    expect(store.buyingItemId).toBeNull()
    expect(store.lastOutcome).toBeNull()
  })
})

describe('loading the shop', () => {
  it('records the failure without claiming an empty catalogue', async () => {
    const store = useGameStore()
    server.use(
      http.post('/api/games', () => HttpResponse.json(upstream.game)),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: upstream.game, ads: upstream.ads }),
      ),
      http.get('/api/games/:gameId/shop', () =>
        problem(503, 'UPSTREAM_UNAVAILABLE', 'Not responding.'),
      ),
    )

    await store.startGame()

    expect(store.shopStatus).toBe('error')
    expect(store.shopItems).toEqual([])
    expect(store.error).toMatchObject({ code: 'UPSTREAM_UNAVAILABLE' })
    expect(store.ads).toHaveLength(1)
  })
})

describe('calibration', () => {
  it('records what the advisor predicted against how the job actually went', async () => {
    const store = await startedStore()
    const calibration = useCalibrationStore()
    upstream.ads = [
      anAd({ adId: 'job', probability: 'Gamble', probabilityTier: 'EVEN', successProbability: 0.4 }),
    ]
    await store.refreshAds()
    stubSolve({ game: aGame({ score: 30 }), success: false })

    await store.solve('job')

    expect(calibration.rows).toMatchObject([
      { label: 'Gamble', tier: 'EVEN', attempts: 1, successes: 0, predicted: 0.4 },
    ])
  })

  it('counts the solver’s turns into the same tally as the player’s', async () => {
    const store = await startedStore()
    const calibration = useCalibrationStore()
    stubAutoPlayStep(
      aStep({
        decision: aDecision({
          targetId: 'chosen',
          ads: [
            anAdOption({ adId: 'chosen', probability: 'Risky', probabilityTier: 'EVEN', successProbability: 0.37 }),
            anAdOption({ adId: 'other', verdict: 'OUTRANKED' }),
          ],
        }),
        succeeded: true,
      }),
    )

    await store.autoPlayStep(false)

    expect(calibration.rows).toMatchObject([
      { label: 'Risky', attempts: 1, successes: 1, predicted: 0.37 },
    ])
  })

  it('records nothing for a turn that attempted no ad', async () => {
    const store = await startedStore()
    const calibration = useCalibrationStore()
    stubAutoPlayStep(
      aStep({
        decision: aDecision({ move: 'INVESTIGATE_REPUTATION', targetId: null, ads: [] }),
        message: null,
      }),
    )

    await store.autoPlayStep(false)

    expect(calibration.rows).toEqual([])
  })

  it('records nothing when the attempt never reached the server', async () => {
    const store = await startedStore()
    const calibration = useCalibrationStore()
    server.use(
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        problem(503, 'UPSTREAM_UNAVAILABLE', 'Not responding.'),
      ),
    )

    await store.solve(upstream.ads[0].adId)

    expect(calibration.rows).toEqual([])
  })

  it('counts each game it has seen, so the tally can say what it spans', async () => {
    const calibration = useCalibrationStore()
    const store = await startedStore()
    await store.startGame()

    expect(calibration.games).toBe(2)
  })
})

describe('a failure that ends the run', () => {
  it('ends the game rather than reporting a fault when the server has forgotten the session', async () => {
    const store = await startedStore()
    server.use(
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        problem(404, 'SESSION_EXPIRED', 'This game is no longer being tracked.'),
      ),
    )

    await store.solve(upstream.ads[0].adId)

    expect(store.ending).toBe('lost')
    expect(store.playable).toBe(false)
    // The panel that replaces the board says it; a banner above one would only repeat itself.
    expect(store.error).toBeNull()
  })

  it('treats an unknown game upstream the same way', async () => {
    const store = await startedStore()
    server.use(
      http.get('/api/games/:gameId/ads', () =>
        problem(404, 'GAME_NOT_FOUND', 'The game service does not recognise this game.'),
      ),
    )

    await store.refreshAds()

    expect(store.ending).toBe('lost')
  })

  it('marks the game finished when the upstream says it already is', async () => {
    const store = await startedStore()
    server.use(
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        problem(410, 'GAME_OVER', 'This game is over.'),
      ),
    )

    await store.solve(upstream.ads[0].adId)

    expect(store.ending).toBe('finished')
    expect(store.game?.finished).toBe(true)
    expect(store.error).toBeNull()
  })

  it('stops fetching against a session the server has forgotten', async () => {
    const store = await startedStore()
    server.use(
      http.get('/api/games/:gameId/ads', () =>
        problem(404, 'SESSION_EXPIRED', 'This game is no longer being tracked.'),
      ),
    )
    await store.refreshAds()

    let calls = 0
    server.use(
      http.get('/api/games/:gameId/ads', () => {
        calls += 1
        return HttpResponse.json({ game: upstream.game, ads: upstream.ads })
      }),
    )
    await store.refreshAds()

    expect(calls).toBe(0)
  })

  it('lets a new game out of a lost one', async () => {
    const store = await startedStore()
    server.use(
      http.get('/api/games/:gameId/ads', () =>
        problem(404, 'SESSION_EXPIRED', 'This game is no longer being tracked.'),
      ),
    )
    await store.refreshAds()

    stubGame()
    await store.startGame()

    expect(store.ending).toBeNull()
    expect(store.playable).toBe(true)
  })

  it('still parks a fault in the banner, where the game survives it', async () => {
    const store = await startedStore()
    server.use(
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        problem(503, 'UPSTREAM_UNAVAILABLE', 'The game service is not responding.'),
      ),
    )

    await store.solve(upstream.ads[0].adId)

    expect(store.error?.code).toBe('UPSTREAM_UNAVAILABLE')
    expect(store.ending).toBeNull()
    expect(store.playable).toBe(true)
  })
})

describe('investigating reputation', () => {
  const standing = { people: 12.5, state: -3.25, underworld: 40 }

  function stubInvestigate(game: GameView, reputation = standing): void {
    server.use(
      http.post('/api/games/:gameId/investigate', () => HttpResponse.json({ game, reputation })),
    )
  }

  it('starts with nothing, because nothing on the wire reports standing until it is paid for', async () => {
    const store = await startedStore()

    expect(store.reputation).toBeNull()
  })

  it('spends a turn, ages the board and keeps what the scouts found', async () => {
    upstream.ads = [anAd({ adId: 'a', expiresIn: 5 }), anAd({ adId: 'b', expiresIn: 4 })]
    const store = await startedStore()
    upstream.game = aGame({ turn: 1 })
    stubInvestigate(upstream.game)

    await store.investigate()

    expect(store.game?.turn).toBe(1)
    expect(store.reputation).toEqual(standing)
    expect(store.lastOutcome).toEqual({ kind: 'investigation' })
  })

  it('leaves the game exactly as it was when the call fails', async () => {
    upstream.ads = [anAd({ adId: 'a', expiresIn: 5 })]
    const store = await startedStore()
    const before = store.game
    server.use(
      http.post('/api/games/:gameId/investigate', () =>
        problem(503, 'UPSTREAM_UNAVAILABLE', 'The game service is not responding.'),
      ),
    )

    await store.investigate()

    expect(store.game).toEqual(before)
    expect(store.ads[0].expiresIn).toBe(5)
    expect(store.reputation).toBeNull()
    expect(store.error?.code).toBe('UPSTREAM_UNAVAILABLE')
  })

  it('fills the crests in from a solver pass too, since that turn was paid for as well', async () => {
    const store = await startedStore()
    stubAutoPlayStep(
      aStep({
        decision: aDecision({ move: 'INVESTIGATE_REPUTATION', targetId: null, ads: [] }),
        message: null,
        reputation: standing,
      }),
    )

    await store.autoPlayStep(false)

    expect(store.reputation).toEqual(standing)
  })

  it('keeps the last reading when a later turn reports none', async () => {
    const store = await startedStore()
    stubInvestigate(upstream.game)
    await store.investigate()
    stubAutoPlayStep(aStep({ reputation: null }))

    await store.autoPlayStep(false)

    expect(store.reputation).toEqual(standing)
  })

  it("forgets the previous game's standing when a new one starts", async () => {
    const store = await startedStore()
    stubInvestigate(upstream.game)
    await store.investigate()

    stubGame()
    await store.startGame()

    expect(store.reputation).toBeNull()
  })
})

describe('resuming after a reload', () => {
  /** What a refresh actually produces: a new app, against the same tab's storage. */
  async function reloaded() {
    await nextTick()
    setActivePinia(createPinia())
    return useGameStore()
  }

  it('has nothing to resume when no game was in progress', async () => {
    const store = useGameStore()

    expect(await store.resume()).toBe(false)
    expect(store.started).toBe(false)
    expect(store.resumeFailed).toBe(false)
  })

  // Nothing that spends a turn is stubbed in this suite, so a resume that cost one would fail
  // the request rather than quietly charge the player for reloading.
  it('picks the game back up from the board and the shop, which cost no turn', async () => {
    upstream.game = aGame({ turn: 12, score: 400, gold: 60 })
    upstream.ads = [anAd({ adId: 'a1' }), anAd({ adId: 'a2' })]
    await startedStore()

    const store = await reloaded()
    stubGame()

    expect(await store.resume()).toBe(true)
    expect(store.game).toMatchObject({ gameId: 'kZUyeMSK', turn: 12, score: 400 })
    expect(store.ads).toHaveLength(2)
    expect(store.shopItems).toHaveLength(1)
    expect(store.boardStatus).toBe('ready')
  })

  it('keeps the standing the scouts were already paid for', async () => {
    const standing = { people: 12.5, state: -3.25, underworld: 40 }
    const store = await startedStore()
    server.use(
      http.post('/api/games/:gameId/investigate', () =>
        HttpResponse.json({ game: upstream.game, reputation: standing }),
      ),
    )
    await store.investigate()

    const resumed = await reloaded()
    stubGame()
    await resumed.resume()

    expect(resumed.reputation).toEqual(standing)
  })

  it('restores a game that had already ended from the record alone', async () => {
    const store = await startedStore()
    stubSolve({ game: aGame({ finished: true, score: 810, turn: 44 }), success: false })
    await store.solve('LTyNBlYB')

    const resumed = await reloaded()

    expect(await resumed.resume()).toBe(true)
    expect(resumed.ending).toBe('finished')
    expect(resumed.game).toMatchObject({ score: 810, turn: 44 })
    // Untouched, which is the proof that no board was asked for: the server refuses to list one.
    expect(resumed.boardStatus).toBe('idle')
  })

  it('offers a new game, not a defeat, when the server has already let the session go', async () => {
    await startedStore()

    const store = await reloaded()
    server.use(
      http.get('/api/games/:gameId/ads', () =>
        problem(404, 'SESSION_EXPIRED', 'That game is no longer being tracked.'),
      ),
      http.get('/api/games/:gameId/shop', () =>
        problem(404, 'SESSION_EXPIRED', 'That game is no longer being tracked.'),
      ),
    )

    expect(await store.resume()).toBe(false)
    expect(store.started).toBe(false)
    expect(store.ending).toBeNull()
    expect(store.sessionLost).toBe(false)
    expect(store.error).toBeNull()
    expect(store.resumeFailed).toBe(true)
  })

  it('forgets a game it could not resume, so the next reload starts clean', async () => {
    await startedStore()

    const store = await reloaded()
    server.use(
      http.get('/api/games/:gameId/ads', () =>
        problem(404, 'SESSION_EXPIRED', 'That game is no longer being tracked.'),
      ),
      http.get('/api/games/:gameId/shop', () =>
        problem(404, 'SESSION_EXPIRED', 'That game is no longer being tracked.'),
      ),
    )
    await store.resume()

    const next = await reloaded()

    expect(await next.resume()).toBe(false)
    expect(next.resumeFailed).toBe(false)
  })

  it('remembers the advisor toggle, which is a preference rather than a game', async () => {
    useGameStore().toggleAdvisor()

    expect((await reloaded()).advisorEnabled).toBe(true)
  })
})
