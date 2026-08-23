import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { useAutoPlayStore } from './autoplay'
import { useGameStore } from './game'
import { aGame, aPass, aStep, problem } from '../test/fixtures'
import type { AutoPlayStepView } from '../api/types'

/**
 * What the fake server currently believes. The board and the shop carry the live state upstream,
 * so a step that moves it has to move this too or a refetch would roll the game backwards.
 */
let latest = aGame()

/** Answers the step endpoint from a script, holding the last entry once the script runs out. */
function stubSteps(script: Array<AutoPlayStepView | Response>): { calls: () => number } {
  let calls = 0
  server.use(
    http.post('/api/games/:gameId/autoplay/step', () => {
      const next = script[Math.min(calls, script.length - 1)]
      calls += 1
      if (next instanceof Response) {
        return next.clone()
      }
      latest = next.game
      return HttpResponse.json(next)
    }),
  )
  return { calls: () => calls }
}

function stubGame(game = aGame()): void {
  latest = game
  server.use(
    http.post('/api/games', () => HttpResponse.json(game)),
    http.get('/api/games/:gameId/ads', () => HttpResponse.json({ game: latest, ads: [] })),
    http.get('/api/games/:gameId/shop', () => HttpResponse.json({ game: latest, items: [] })),
  )
}

/** Started, and running at max speed so the loop never waits on a real timer. */
async function playing() {
  const games = useGameStore()
  const autoPlay = useAutoPlayStore()
  stubGame()
  await games.startGame()
  autoPlay.speed = 'max'
  return { games, autoPlay }
}

beforeEach(() => {
  setActivePinia(createPinia())
  latest = aGame()
})
afterEach(() => vi.useRealTimers())

describe('a single step', () => {
  it('records the turn and adopts the state it returns', async () => {
    const { games, autoPlay } = await playing()
    stubSteps([aStep()])

    await autoPlay.step()

    expect(autoPlay.log).toHaveLength(1)
    expect(autoPlay.log[0]?.decision.reason).toBe('BEST_RISK_ADJUSTED_AD')
    expect(games.game?.score).toBe(15)
    expect(autoPlay.halt).toBeNull()
  })

  it('is refused while a run is already under way', async () => {
    const { autoPlay } = await playing()
    const counter = stubSteps([
      aStep({ game: aGame({ turn: 1 }) }),
      aStep({ game: aGame({ turn: 2, finished: true }) }),
    ])

    const running = autoPlay.run()
    await autoPlay.step()
    await running

    expect(counter.calls()).toBe(2)
  })
})

describe('running to completion', () => {
  it('loops until the game ends, then halts on the ending', async () => {
    const { autoPlay } = await playing()
    stubSteps([
      aStep({ game: aGame({ turn: 1 }) }),
      aStep({ game: aGame({ turn: 2 }) }),
      aStep({ game: aGame({ turn: 3, score: 1200, finished: true }) }),
    ])

    await autoPlay.run()

    expect(autoPlay.log).toHaveLength(3)
    expect(autoPlay.halt).toEqual({ kind: 'finished' })
    expect(autoPlay.running).toBe(false)
  })

  it('ignores a second Run rather than starting a second loop', async () => {
    const { autoPlay } = await playing()
    const counter = stubSteps([aStep({ game: aGame({ turn: 1, finished: true }) })])

    await Promise.all([autoPlay.run(), autoPlay.run()])

    expect(counter.calls()).toBe(1)
    expect(autoPlay.log).toHaveLength(1)
  })
})

describe('pausing', () => {
  it('stops the loop but still logs the turn already sent upstream', async () => {
    const { autoPlay } = await playing()
    const counter = stubSteps([aStep({ game: aGame({ turn: 1 }) })])

    const running = autoPlay.run()
    autoPlay.pause()
    await running

    expect(counter.calls()).toBe(1)
    expect(autoPlay.log).toHaveLength(1)
    expect(autoPlay.running).toBe(false)
    // A pause is not a fault: nothing to explain, and Run picks straight back up.
    expect(autoPlay.halt).toBeNull()
  })

  it('does not have to outwait the speed setting', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { autoPlay } = await playing()
    autoPlay.speed = 'slow'
    stubSteps([aStep({ game: aGame({ turn: 1 }) })])

    const running = autoPlay.run()
    await vi.advanceTimersByTimeAsync(0)
    autoPlay.pause()
    await running

    expect(autoPlay.running).toBe(false)
    expect(autoPlay.log).toHaveLength(1)
  })
})

describe('a step that fails', () => {
  it('halts the run rather than spinning on it', async () => {
    const { autoPlay } = await playing()
    const counter = stubSteps([problem(404, 'SESSION_EXPIRED', 'That game is no longer on file.')])

    await autoPlay.run()

    expect(counter.calls()).toBe(1)
    expect(autoPlay.log).toHaveLength(0)
    expect(autoPlay.halt).toMatchObject({ kind: 'error' })
    expect(autoPlay.running).toBe(false)
  })

  it('will not offer to resume a game the server has forgotten', async () => {
    const { autoPlay } = await playing()
    stubSteps([problem(404, 'SESSION_EXPIRED', 'That game is no longer on file.')])

    await autoPlay.run()

    expect(autoPlay.canPlay).toBe(false)
  })

  it('keeps offering to resume when only the request failed', async () => {
    const { autoPlay } = await playing()
    stubSteps([problem(502, 'UPSTREAM_ERROR', 'The game service failed unexpectedly.')])

    await autoPlay.run()

    expect(autoPlay.canPlay).toBe(true)
  })
})

describe('a rate limit', () => {
  it('is a wait, not a failure — the loop backs off and carries on', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { autoPlay } = await playing()
    stubSteps([
      problem(429, 'UPSTREAM_RATE_LIMITED', 'The game is rate limiting us.'),
      aStep({ game: aGame({ turn: 1, finished: true }) }),
    ])

    const running = autoPlay.run()
    await vi.advanceTimersByTimeAsync(5000)
    await running

    expect(autoPlay.log).toHaveLength(1)
    expect(autoPlay.halt).toEqual({ kind: 'finished' })
  })

  it('gives up once the waits run out', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { autoPlay } = await playing()
    stubSteps([problem(429, 'UPSTREAM_RATE_LIMITED', 'The game is rate limiting us.')])

    const running = autoPlay.run()
    await vi.advanceTimersByTimeAsync(30_000)
    await running

    expect(autoPlay.log).toHaveLength(0)
    expect(autoPlay.halt).toMatchObject({ kind: 'error', error: { code: 'UPSTREAM_RATE_LIMITED' } })
    expect(autoPlay.waiting).toBe(false)
  })
})

describe('the stall guard', () => {
  it('stops after ten passes in a row, because a pass never ends the game', async () => {
    const { autoPlay } = await playing()
    const counter = stubSteps([aPass()])

    await autoPlay.run()

    expect(counter.calls()).toBe(10)
    expect(autoPlay.halt).toEqual({ kind: 'stalled', passes: 10 })
  })

  it('restarts the count when the player says keep going, so it checks in again', async () => {
    const { autoPlay } = await playing()
    stubSteps([aPass()])
    await autoPlay.run()

    autoPlay.keepGoing()
    await vi.waitFor(() => expect(autoPlay.running).toBe(false))

    expect(autoPlay.log).toHaveLength(20)
    expect(autoPlay.halt).toEqual({ kind: 'stalled', passes: 10 })
  })

  it('is not tripped by passes broken up by real moves', async () => {
    const { autoPlay } = await playing()
    stubSteps([
      ...Array.from({ length: 9 }, () => aPass()),
      aStep({ game: aGame({ turn: 10 }) }),
      aStep({ game: aGame({ turn: 11, finished: true }) }),
    ])

    await autoPlay.run()

    expect(autoPlay.halt).toEqual({ kind: 'finished' })
    expect(autoPlay.log).toHaveLength(11)
  })
})

describe('a new game', () => {
  it('empties the log left by the last one', async () => {
    const { games, autoPlay } = await playing()
    stubSteps([aStep()])
    await autoPlay.step()
    expect(autoPlay.log).toHaveLength(1)

    stubGame(aGame({ gameId: 'Ab3xQ9Kd' }))
    await games.startGame()

    expect(autoPlay.log).toHaveLength(0)
    expect(autoPlay.halt).toBeNull()
  })
})

describe('a reload mid-run', () => {
  /**
   * A refresh, in the order the app does it: the store exists before the game is restored,
   * because restoring a game id is what resets the log.
   */
  async function reloaded() {
    await nextTick()
    setActivePinia(createPinia())
    const autoPlay = useAutoPlayStore()
    const games = useGameStore()
    stubGame(latest)
    return { games, autoPlay }
  }

  it('puts the interrupted run back beside the game it belongs to', async () => {
    const first = await playing()
    stubSteps([aStep(), aStep()])
    await first.autoPlay.step()
    await first.autoPlay.step()

    const { games, autoPlay } = await reloaded()
    await games.resume()
    autoPlay.restore(games.game?.gameId ?? '')

    expect(autoPlay.log).toHaveLength(2)
    expect(autoPlay.halt).toBeNull()
  })

  it('numbers the turns that follow on from the ones it restored', async () => {
    const first = await playing()
    stubSteps([aStep()])
    await first.autoPlay.step()

    const { games, autoPlay } = await reloaded()
    await games.resume()
    autoPlay.restore(games.game?.gameId ?? '')
    stubSteps([aStep()])
    await autoPlay.step()

    expect(autoPlay.log.map((entry) => entry.id)).toEqual([1, 2])
  })

  it('refuses a log written for a different game', async () => {
    const first = await playing()
    stubSteps([aStep()])
    await first.autoPlay.step()

    const { games, autoPlay } = await reloaded()
    await games.resume()
    autoPlay.restore('someone-elses-game')

    expect(autoPlay.log).toEqual([])
  })

  it('remembers the speed, which is a preference rather than part of a game', async () => {
    const { autoPlay } = await playing()
    autoPlay.speed = 'slow'

    expect((await reloaded()).autoPlay.speed).toBe('slow')
  })
})
