import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import App from './App.vue'
import { server } from './mocks/server'
import { aDecision, aGame, aStep, anAd, anItem, problem } from './test/fixtures'

function render() {
  return mount(App, { global: { plugins: [createPinia()] } })
}

const buttonLabelled = (app: ReturnType<typeof render>, label: string) =>
  app.findAll('button').find((button) => button.text() === label)

describe('App', () => {
  it('opens on an invitation to start, with no board and no stats', () => {
    const app = render()

    expect(app.get('h1').text()).toBe('Dragons of Mugloar')
    expect(app.get('button').text()).toBe('Start a game')
    expect(app.find('[aria-label="Dragon status"]').exists()).toBe(false)
  })

  it('goes from a click to a scored board', async () => {
    server.use(
      http.post('/api/games', () => HttpResponse.json(aGame())),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({
          game: aGame(),
          ads: [anAd({ message: 'Steal a shipment of gold' })],
        }),
      ),
      http.get('/api/games/:gameId/shop', () =>
        HttpResponse.json({ game: aGame(), items: [anItem()] }),
      ),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()

    expect(app.get('[aria-label="Dragon status"]').text()).toContain('Lives')
    expect(app.text()).toContain('Steal a shipment of gold')
  })

  it('tells the player what went wrong instead of showing an empty page', async () => {
    server.use(
      http.post('/api/games', () =>
        problem(503, 'UPSTREAM_UNAVAILABLE', 'The game service is not responding.'),
      ),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()

    expect(app.get('[role="alert"]').text()).toContain('The game service is not responding.')
    expect(app.findAll('button').map((button) => button.text())).toContain('Start a game')
  })

  it('ends on the final score once the last life is spent', async () => {
    const dying = aGame({ lives: 1, score: 1210, turn: 83 })
    server.use(
      http.post('/api/games', () => HttpResponse.json(dying)),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: dying, ads: [anAd({ adId: 'last', message: 'One last job' })] }),
      ),
      http.get('/api/games/:gameId/shop', () => HttpResponse.json({ game: dying, items: [] })),
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        HttpResponse.json({
          game: aGame({ lives: 0, score: 1210, turn: 84, finished: true }),
          adId: 'last',
          success: false,
          message: 'You failed on the mission!',
        }),
      ),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()
    await app.get('[aria-label="Solve: One last job"]').trigger('click')
    await flushPromises()

    expect(app.text()).toContain('The dragon has fallen')
    expect(app.text()).toContain('Final score 1210 points')
  })

  it('puts the shop next to the board, and lets a small screen pick one', async () => {
    const rich = aGame({ gold: 500 })
    server.use(
      http.post('/api/games', () => HttpResponse.json(rich)),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: rich, ads: [anAd({ message: 'Steal a shipment of gold' })] }),
      ),
      http.get('/api/games/:gameId/shop', () =>
        HttpResponse.json({ game: rich, items: [anItem({ name: 'Claw Sharpening' })] }),
      ),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()

    // Both panels are always mounted; the switch only decides which one a narrow screen hides.
    expect(app.text()).toContain('Steal a shipment of gold')
    expect(app.text()).toContain('Claw Sharpening')

    const [board, shop] = app.findAll('[aria-label="Choose what to show"] button')
    expect(board?.attributes('aria-pressed')).toBe('true')
    expect(shop?.attributes('aria-pressed')).toBe('false')

    await shop?.trigger('click')
    expect(shop?.attributes('aria-pressed')).toBe('true')
  })

  it('buys an item and shows the dragon getting stronger', async () => {
    // One mutable state, so the board refetch that follows a purchase reports the new purse
    // rather than handing back the one the game started with.
    let current = aGame({ gold: 500 })
    server.use(
      http.post('/api/games', () => HttpResponse.json(current)),
      http.get('/api/games/:gameId/ads', () => HttpResponse.json({ game: current, ads: [anAd()] })),
      http.get('/api/games/:gameId/shop', () =>
        HttpResponse.json({ game: current, items: [anItem({ name: 'Claw Sharpening' })] }),
      ),
      http.post('/api/games/:gameId/shop/:itemId/buy', () => {
        current = aGame({ gold: 400, level: 1, turn: 1 })
        return HttpResponse.json({ game: current, itemId: 'cs', success: true })
      }),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()
    await app.get('[aria-label="Buy Claw Sharpening for 100 gold"]').trigger('click')
    await flushPromises()

    // More than one live region is on screen now that auto-play has a status line of its own.
    const announced = app.findAll('[role="status"]').map((region) => region.text())
    expect(announced.some((text) => text.includes('Bought Claw Sharpening'))).toBe(true)
    expect(app.get('[aria-label="Dragon status"]').text()).toContain('400')
  })

  it('hands the game to the solver, and says what it did with each turn', async () => {
    let current = aGame({ gold: 50 })
    let steps = 0
    server.use(
      http.post('/api/games', () => HttpResponse.json(current)),
      http.get('/api/games/:gameId/ads', () => HttpResponse.json({ game: current, ads: [anAd()] })),
      http.get('/api/games/:gameId/shop', () =>
        HttpResponse.json({ game: current, items: [anItem()] }),
      ),
      http.post('/api/games/:gameId/autoplay/step', () => {
        steps += 1
        current =
          steps === 1
            ? aGame({ gold: 65, score: 15, turn: 1 })
            : aGame({ gold: 65, score: 1400, lives: 0, turn: 2, finished: true })
        return HttpResponse.json(
          aStep({
            game: current,
            decision: aDecision({ ads: [{ ...aDecision().ads[0]!, message: 'Steal the gold' }] }),
          }),
        )
      }),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()

    // Max speed so the loop is not held up by a real timer, which is also the mode that skips
    // the per-turn board refetch.
    await app.get('select').setValue('max')
    await buttonLabelled(app, 'Run')?.trigger('click')
    await vi.waitFor(() => expect(app.text()).toContain('The dragon has fallen'))

    expect(app.text()).toContain('Final score 1400 points')
    // The board is gone, but the reasoning that got there survives it.
    expect(app.text()).toContain('Mission accomplished')
    expect(app.text()).toContain('Best reward on the board once the risk to a life is priced in.')
    expect(app.text()).toContain('Steal the gold')
  })

  /**
   * The status strip is a live region, and the solver moves it every turn. At max speed that is a
   * reading every few hundred milliseconds, which is why the decision log is not one either.
   */
  it('stops announcing the status strip while the solver holds the game', async () => {
    server.use(
      http.post('/api/games', () => HttpResponse.json(aGame())),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: aGame(), ads: [anAd()] }),
      ),
      http.get('/api/games/:gameId/shop', () =>
        HttpResponse.json({ game: aGame(), items: [anItem()] }),
      ),
      http.post('/api/games/:gameId/autoplay/step', () => HttpResponse.json(aStep())),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()

    const strip = () => app.get('[aria-label="Dragon status"]')
    expect(strip().attributes('aria-live')).toBe('polite')
    // Atomic, or a polite region reads out each figure that moved rather than the turn.
    expect(strip().attributes('aria-atomic')).toBe('true')

    await buttonLabelled(app, 'Run')?.trigger('click')
    expect(strip().attributes('aria-live')).toBe('off')

    await buttonLabelled(app, 'Pause')?.trigger('click')
    await vi.waitFor(() => expect(strip().attributes('aria-live')).toBe('polite'))
  })

  it('ends the run, rather than annotating a board no longer being tracked', async () => {
    server.use(
      http.post('/api/games', () => HttpResponse.json(aGame({ score: 640, turn: 41 }))),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: aGame({ score: 640, turn: 41 }), ads: [anAd({ adId: 'job' })] }),
      ),
      http.get('/api/games/:gameId/shop', () => HttpResponse.json({ game: aGame(), items: [] })),
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        problem(404, 'SESSION_EXPIRED', 'This game is no longer being tracked.'),
      ),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()
    await app.get('[aria-label="Solve: Help Robin Webster to steal a shipment of gold"]').trigger('click')
    await flushPromises()

    expect(app.text()).toContain('This game was lost')
    expect(app.text()).toContain('It was worth 640 points after 41 turns')
    expect(buttonLabelled(app, 'Start a new game')).toBeDefined()
    // The dragon was fine. Saying it fell would be a lie about the player's own game.
    expect(app.text()).not.toContain('The dragon has fallen')
    expect(app.find('[role="alert"]').exists()).toBe(false)
  })

  it('does not raise an alarm about a refusal the server saw coming', async () => {
    server.use(
      http.post('/api/games', () => HttpResponse.json(aGame())),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: aGame(), ads: [anAd({ adId: 'gone' })] }),
      ),
      http.get('/api/games/:gameId/shop', () => HttpResponse.json({ game: aGame(), items: [] })),
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        problem(409, 'AD_NOT_AVAILABLE', 'That ad is no longer on the board.'),
      ),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()
    await app.get('[aria-label="Solve: Help Robin Webster to steal a shipment of gold"]').trigger('click')
    await flushPromises()

    expect(app.text()).toContain('That job was already taken')
    expect(app.find('[role="alert"]').exists()).toBe(false)
  })

  it('offers a free board refetch after a fault, and never a retry of the spent turn', async () => {
    let solves = 0
    server.use(
      http.post('/api/games', () => HttpResponse.json(aGame())),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: aGame(), ads: [anAd({ adId: 'job' })] }),
      ),
      http.get('/api/games/:gameId/shop', () => HttpResponse.json({ game: aGame(), items: [] })),
      http.post('/api/games/:gameId/ads/:adId/solve', () => {
        solves += 1
        return problem(503, 'UPSTREAM_UNAVAILABLE', 'The game service is not responding.')
      }),
    )
    const app = render()

    await app.get('button').trigger('click')
    await flushPromises()
    await app.get('[aria-label="Solve: Help Robin Webster to steal a shipment of gold"]').trigger('click')
    await flushPromises()

    const refresh = buttonLabelled(app, 'Refresh the board')
    expect(refresh).toBeDefined()
    await refresh?.trigger('click')
    await flushPromises()

    expect(solves).toBe(1)
  })

  it('moves focus to the panel that replaces the board, not back to the top of the page', async () => {
    const dying = aGame({ lives: 1, score: 300, turn: 20 })
    server.use(
      http.post('/api/games', () => HttpResponse.json(dying)),
      http.get('/api/games/:gameId/ads', () =>
        HttpResponse.json({ game: dying, ads: [anAd({ adId: 'last' })] }),
      ),
      http.get('/api/games/:gameId/shop', () => HttpResponse.json({ game: dying, items: [] })),
      http.post('/api/games/:gameId/ads/:adId/solve', () =>
        HttpResponse.json({
          game: aGame({ lives: 0, score: 300, turn: 21, finished: true }),
          adId: 'last',
          success: false,
          message: 'You failed on the mission!',
        }),
      ),
    )
    const app = mount(App, { global: { plugins: [createPinia()] }, attachTo: document.body })

    await app.get('button').trigger('click')
    await flushPromises()
    await app.get('[aria-label="Solve: Help Robin Webster to steal a shipment of gold"]').trigger('click')
    await flushPromises()

    expect(document.activeElement).toBe(app.get('[role="status"][tabindex="-1"]').element)
    app.unmount()
  })
})
