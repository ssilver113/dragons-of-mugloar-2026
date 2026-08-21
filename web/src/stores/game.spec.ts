import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { useGameStore } from './game'
import { aGame, anAd, problem } from '../test/fixtures'
import type { AdView, GameView, SolveResultView } from '../api/types'

/** What the fake server currently believes; tests move it to set up the next response. */
const upstream = {
  game: aGame(),
  ads: [] as AdView[],
}

function stubGame(): void {
  server.use(
    http.post('/api/games', () => HttpResponse.json(upstream.game)),
    http.get('/api/games/:gameId/ads', () =>
      HttpResponse.json({ game: upstream.game, ads: upstream.ads }),
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

describe('ordering', () => {
  beforeEach(() => {
    upstream.ads = [
      anAd({ adId: 'low', expectedValue: 4 }),
      anAd({ adId: 'tie-late', expectedValue: 30, expiresIn: 6 }),
      anAd({ adId: 'tie-soon', expectedValue: 30, expiresIn: 2 }),
    ]
  })

  it('leaves the board as the game posted it while the advisor is off', async () => {
    const store = await startedStore()

    expect(store.orderedAds.map((ad) => ad.adId)).toEqual(['low', 'tie-late', 'tie-soon'])
  })

  it('orders by expected value once advice is asked for, ties going to the ad about to expire', async () => {
    const store = await startedStore()

    store.toggleAdvisor()

    expect(store.orderedAds.map((ad) => ad.adId)).toEqual(['tie-soon', 'tie-late', 'low'])
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
    expect(store.lastOutcome).toMatchObject({ adId: 'target', success: true })
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
