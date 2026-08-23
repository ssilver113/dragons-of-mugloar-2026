import { describe, expect, it } from 'vitest'
import { http, HttpResponse } from 'msw'
import { server } from '../mocks/server'
import { ApiError, api } from './client'
import { aGame, problem } from '../test/fixtures'

describe('api client', () => {
  it('returns the parsed body of a successful request', async () => {
    server.use(http.post('/api/games', () => HttpResponse.json(aGame())))

    await expect(api.startGame()).resolves.toMatchObject({ gameId: 'kZUyeMSK', lives: 3 })
  })

  it('carries the code and detail of a problem body onto the error', async () => {
    server.use(
      http.get('/api/games/:id/ads', () =>
        problem(404, 'SESSION_EXPIRED', 'This game is no longer being tracked. Start a new one.'),
      ),
    )

    const error = await api.listAds('kZUyeMSK').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({
      code: 'SESSION_EXPIRED',
      status: 404,
      message: 'This game is no longer being tracked. Start a new one.',
    })
  })

  // A code the app has no vocabulary for used to travel as far as the presentation lookup, which
  // answers with nothing and turns a handled failure into a TypeError inside the caller's catch.
  it('does not adopt a problem code it does not recognise', async () => {
    server.use(
      http.get('/api/games/:id/ads', () =>
        problem(503, 'CDN_EDGE_UNREACHABLE', 'Our edge could not reach the origin.'),
      ),
    )

    const error = await api.listAds('kZUyeMSK').catch((e: unknown) => e)

    // Attributed by status instead, which is where a body we cannot read already lands.
    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ code: 'UPSTREAM_UNAVAILABLE', status: 503 })
  })

  it('attributes a gateway failure with no problem body to the upstream being down', async () => {
    server.use(
      http.post('/api/games', () => new HttpResponse('<html>Bad Gateway</html>', { status: 502 })),
    )

    await expect(api.startGame()).rejects.toMatchObject({ code: 'UPSTREAM_UNAVAILABLE' })
  })

  it('falls back to an internal error for any other unreadable failure', async () => {
    server.use(http.post('/api/games', () => new HttpResponse('boom', { status: 500 })))

    await expect(api.startGame()).rejects.toMatchObject({ code: 'INTERNAL_ERROR', status: 500 })
  })

  it('turns a failed request into a network error rather than letting it escape raw', async () => {
    server.use(http.post('/api/games', () => HttpResponse.error()))

    await expect(api.startGame()).rejects.toMatchObject({ code: 'NETWORK_ERROR', status: 0 })
  })

  it('reports an unreadable success body as a protocol failure', async () => {
    server.use(
      http.post(
        '/api/games',
        () =>
          new HttpResponse('not json', {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
      ),
    )

    await expect(api.startGame()).rejects.toMatchObject({ code: 'UPSTREAM_PROTOCOL' })
  })

  it('escapes ids so a hostile-looking path cannot reshape the request', async () => {
    let seen: string | undefined
    server.use(
      http.post('/api/games/:gameId/ads/:adId/solve', ({ request }) => {
        seen = new URL(request.url).pathname
        return HttpResponse.json({})
      }),
    )

    await api.solve('kZUyeMSK', 'a/../b')

    expect(seen).toBe('/api/games/kZUyeMSK/ads/a%2F..%2Fb/solve')
  })
})
