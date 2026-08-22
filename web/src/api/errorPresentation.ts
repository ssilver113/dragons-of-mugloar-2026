import type { ErrorCode } from './types'

/**
 * How much of an interruption a failure deserves. Every request that fails arrives at the store
 * as an `ApiError`, but they are not all the same kind of event, and rendering them identically
 * taught the player to ignore the banner.
 *
 * - `terminal` — the game cannot be played on. The board is replaced, not annotated.
 * - `fault` — something broke; the game is intact. Announced as an alert.
 * - `note` — the server refused a move it could see coming, and nothing is broken. Announced
 *   politely, because the app has usually already corrected itself by the time it is read.
 */
export type Severity = 'terminal' | 'fault' | 'note'

export interface ErrorPresentation {
  severity: Severity
  title: string
  /**
   * Whether to offer a board refetch. Never a retry of the failed action: a solve or a purchase
   * that timed out may already have landed upstream, so repeating it can spend a second turn on
   * a move the player only made once. Refetching the board costs nothing and answers the only
   * question worth asking — what is actually true now.
   */
  offerRefresh: boolean
}

const PRESENTATION: Record<ErrorCode, ErrorPresentation> = {
  SESSION_EXPIRED: { severity: 'terminal', title: 'This game was lost', offerRefresh: false },
  GAME_NOT_FOUND: { severity: 'terminal', title: 'This game was lost', offerRefresh: false },
  GAME_OVER: { severity: 'terminal', title: 'The game is over', offerRefresh: false },

  AD_NOT_AVAILABLE: { severity: 'note', title: 'That job was already taken', offerRefresh: false },
  ITEM_NOT_AVAILABLE: { severity: 'note', title: 'The shop no longer stocks that', offerRefresh: true },
  INSUFFICIENT_GOLD: { severity: 'note', title: 'Not enough gold', offerRefresh: false },
  INVALID_ACTION: { severity: 'note', title: 'The board had moved on', offerRefresh: false },

  // Waiting is the fix, and the auto-play loop already waits. A refresh would only add traffic.
  UPSTREAM_RATE_LIMITED: { severity: 'fault', title: 'Going too fast', offerRefresh: false },
  UPSTREAM_UNAVAILABLE: { severity: 'fault', title: 'The game service is down', offerRefresh: true },
  UPSTREAM_PROTOCOL: { severity: 'fault', title: 'The game service answered strangely', offerRefresh: true },
  UPSTREAM_ERROR: { severity: 'fault', title: 'The game service failed', offerRefresh: true },
  NETWORK_ERROR: { severity: 'fault', title: 'No connection to the server', offerRefresh: true },
  INTERNAL_ERROR: { severity: 'fault', title: 'Something went wrong', offerRefresh: true },
  // Only reachable from a bug on our side, so there is nothing for the player to do about it.
  VALIDATION_FAILED: { severity: 'fault', title: 'That request was refused', offerRefresh: false },
}

export function present(code: ErrorCode): ErrorPresentation {
  return PRESENTATION[code]
}

/** The two codes that mean the session itself is gone, as opposed to one action failing. */
export function endsTheSession(code: ErrorCode): boolean {
  return code === 'SESSION_EXPIRED' || code === 'GAME_NOT_FOUND'
}
