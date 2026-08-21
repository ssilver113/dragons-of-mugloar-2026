import type { RequestHandler } from 'msw'

// Per-suite handlers are registered with server.use(); this baseline stays empty
// so an unmocked request fails loudly rather than falling through to a stub.
export const handlers: RequestHandler[] = []
