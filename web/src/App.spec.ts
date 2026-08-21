import { describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import App from './App.vue'
import { server } from './mocks/server'
import { aGame, anAd, problem } from './test/fixtures'

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
})
