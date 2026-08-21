import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import App from './App.vue'
import { server } from './mocks/server'
import { aGame, anAd, anItem, problem } from './test/fixtures'

function render() {
  return mount(App, { global: { plugins: [createPinia()] } })
}

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
    expect(app.text()).toContain('Final score 1210')
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

    expect(app.get('[role="status"]').text()).toContain('Bought Claw Sharpening')
    expect(app.get('[aria-label="Dragon status"]').text()).toContain('400')
  })
})
