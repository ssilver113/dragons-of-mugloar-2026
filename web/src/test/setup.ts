import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest'
import { server } from '../mocks/server'

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())

// Storage outlives a test the way it outlives a reload, which is the point of it — but a spec
// that inherited the last one's remembered game would be testing something nobody wrote.
beforeEach(() => {
  sessionStorage.clear()
  localStorage.clear()
})
