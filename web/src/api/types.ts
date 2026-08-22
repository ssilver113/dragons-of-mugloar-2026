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
  | 'UPSTREAM_RATE_LIMITED'
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

/** The three things the solver can spend a turn on. */
export type MoveType = 'SOLVE_AD' | 'BUY_ITEM' | 'INVESTIGATE_REPUTATION'

/**
 * Why the turn went where it did. A code, not a sentence — the wording is the client's, so the
 * log reads in the app's voice and the numbers behind it travel alongside.
 */
export type Reason =
  | 'HEALING_LOW_ON_LIVES'
  | 'LEVELLING_BEHIND_TARGET'
  | 'BEST_RISK_ADJUSTED_AD'
  | 'LEVELLING_NO_AD_WORTH_A_LIFE'
  | 'HEALING_NO_AD_WORTH_A_LIFE'
  | 'PASSING_NOTHING_WORTH_A_TURN'

/** What became of one option the solver weighed. */
export type Verdict =
  | 'CHOSEN'
  | 'OUTRANKED'
  | 'NOT_WORTH_A_LIFE'
  | 'NEVER_ATTEMPT'
  | 'UNREADABLE'
  | 'UNAFFORDABLE'
  | 'NOT_NEEDED'

/**
 * One ad as the solver saw it that turn, carried whole rather than by id: by the time anyone
 * reads the entry the ad has usually expired off the board.
 *
 * `score` is `reward × p − lifeCost × (1 − p)`, in gold. Below zero means the expected reward
 * did not cover the risk to a life at the lives held that turn.
 */
export interface AdOptionView {
  adId: string
  message: string
  reward: number
  expiresIn: number
  probability: string
  probabilityTier: ProbabilityTier
  successProbability: number
  score: number
  verdict: Verdict
}

export interface ItemOptionView {
  itemId: string
  name: string
  cost: number
  livesGained: number
  levelsGained: number
  verdict: Verdict
}

/** One turn's choice and everything that lost. `targetId` is null for a pass, which aims at nothing. */
export interface DecisionView {
  move: MoveType
  targetId: string | null
  reason: Reason
  ads: AdOptionView[]
  items: ItemOptionView[]
}

/** `message` is the upstream sentence for a solve, and null for anything else. */
export interface AutoPlayStepView {
  game: GameView
  decision: DecisionView
  succeeded: boolean
  message: string | null
}
