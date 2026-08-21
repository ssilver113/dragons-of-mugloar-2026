/**
 * Hand-written mirrors of the backend's response records. Kept in one file so a change on the
 * Java side has exactly one place to land on this side.
 */

/** Warnings the server attaches to an ad — see `AdFlag` on the backend. */
export type AdFlag = 'EXPIRING_NEXT_TURN' | 'OUT_OF_LEAGUE' | 'NEVER_ATTEMPT' | 'UNREADABLE'

/** Equivalence class of probability labels. The prior lives on the tier, not on the label. */
export type ProbabilityTier = 'SAFE' | 'FAVOURABLE' | 'EVEN' | 'POOR' | 'DOOMED' | 'UNKNOWN'

export interface GameView {
  gameId: string
  lives: number
  gold: number
  level: number
  score: number
  turn: number
  finished: boolean
}

export interface AdView {
  adId: string
  message: string
  reward: number
  expiresIn: number
  encrypted: boolean
  probability: string
  probabilityTier: ProbabilityTier
  successProbability: number
  expectedValue: number
  flags: AdFlag[]
}

export interface AdBoardView {
  game: GameView
  ads: AdView[]
}

/**
 * One item on offer. The effect arrives as the numbers it moves rather than as a name, so the
 * client can describe the purchase and predict it without knowing the server's vocabulary. Both
 * are zero for a price the server has never measured.
 */
export interface ShopItemView {
  id: string
  name: string
  cost: number
  livesGained: number
  levelsGained: number
}

export interface ShopView {
  game: GameView
  items: ShopItemView[]
}

/** A refused sale is `success: false` on a healthy response — the turn was spent regardless. */
export interface PurchaseResultView {
  game: GameView
  itemId: string
  success: boolean
}

export interface SolveResultView {
  game: GameView
  adId: string
  success: boolean
  message: string
}

/**
 * The `code` property of an RFC 9457 problem body. The UI branches on this rather than on the
 * status, because several situations share a status and the copy differs for each.
 *
 * `NETWORK_ERROR` is the one member the server never sends: it is what a failed `fetch` becomes,
 * so callers have a single vocabulary for every way a request can fail.
 */
export type ErrorCode =
  | 'VALIDATION_FAILED'
  | 'SESSION_EXPIRED'
  | 'GAME_OVER'
  | 'AD_NOT_AVAILABLE'
  | 'ITEM_NOT_AVAILABLE'
  | 'INSUFFICIENT_GOLD'
  | 'GAME_NOT_FOUND'
  | 'INVALID_ACTION'
  | 'UPSTREAM_UNAVAILABLE'
  | 'UPSTREAM_PROTOCOL'
  | 'UPSTREAM_ERROR'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR'

/** RFC 9457 problem body as the backend renders it. */
export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  code?: ErrorCode
}
