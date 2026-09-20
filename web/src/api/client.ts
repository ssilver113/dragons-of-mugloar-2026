import { isErrorCode } from './types'
import type {
  AdBoardView,
  AutoPlayStepView,
  ErrorCode,
  GameView,
  InvestigationView,
  MetaView,
  ProblemDetail,
  PurchaseResultView,
  ShopView,
  SolveResultView,
} from './types'

/**
 * Every failed request, however it failed, arrives at the store as one of these. Carrying the
 * server's `ErrorCode` means the UI never has to interpret a status code or a message string.
 */
export class ApiError extends Error {
  readonly code: ErrorCode
  readonly status: number

  constructor(code: ErrorCode, message: string, status: number, options?: ErrorOptions) {
    super(message, options)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

/**
 * Every rejection a store has to cope with, narrowed to the one type the UI knows how to render.
 * Anything that is not already an `ApiError` is a bug on our side rather than a failed request,
 * so it is attributed to us and carried as the cause.
 */
export function asApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError('INTERNAL_ERROR', 'Something went wrong. Try again.', 0, { cause: e })
}

const NETWORK_MESSAGE = 'Could not reach the server. Check your connection and try again.'
const UNREADABLE_MESSAGE = 'The server responded in a way we could not read.'
const TIMEOUT_MESSAGE =
  'The server took too long to answer. Refresh the board to see where things stand.'

/**
 * How long the browser waits before giving up. Set above our own server's worst bounded case —
 * three attempts at a 3s connect and a 10s read, plus its backoff — so this fires only when
 * nothing is answering at all, never on a request that was still going to arrive. Without it a
 * connection that hangs between the browser and the server leaves the store acting for good:
 * every control disabled, the solver stalled, and a reload the only way out.
 *
 * Aborting is not retrying. The request is abandoned, never sent again, so `solve`, `buy` and
 * `investigate` remain single-shot — and because one that timed out may already have landed
 * upstream, the failure offers a board refetch and never the action a second time.
 */
const DEADLINE_MS = 45_000

/** `AbortSignal.timeout` rejects with a `TimeoutError`; a caller's own abort would be `AbortError`. */
function timedOut(cause: unknown): boolean {
  return cause instanceof Error && cause.name === 'TimeoutError'
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(DEADLINE_MS),
      ...init,
    })
  } catch (cause) {
    throw timedOut(cause)
      ? new ApiError('REQUEST_TIMEOUT', TIMEOUT_MESSAGE, 0, { cause })
      : new ApiError('NETWORK_ERROR', NETWORK_MESSAGE, 0, { cause })
  }

  if (!response.ok) {
    throw await problemFrom(response)
  }

  try {
    return (await response.json()) as T
  } catch (cause) {
    throw new ApiError('UPSTREAM_PROTOCOL', UNREADABLE_MESSAGE, response.status, { cause })
  }
}

/**
 * Anything that is not a readable problem body is attributed by status. The code is checked for
 * membership, not presence: an intermediary can answer with a problem document of its own, and an
 * unrecognised code would reach the presentation lookup and become a `TypeError` in a `catch`.
 */
async function problemFrom(response: Response): Promise<ApiError> {
  let problem: ProblemDetail | null = null
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // Not JSON at all: an HTML error page from a proxy, or an empty body. Attributed by status.
  }

  if (isErrorCode(problem?.code) && problem.detail) {
    return new ApiError(problem.code, problem.detail, response.status)
  }
  if (response.status >= 502 && response.status <= 504) {
    return new ApiError('UPSTREAM_UNAVAILABLE', NETWORK_MESSAGE, response.status)
  }
  return new ApiError('INTERNAL_ERROR', 'Something went wrong. Try again.', response.status)
}

const games = '/api/games'

export const api = {
  meta: (): Promise<MetaView> => request<MetaView>('/api/meta'),

  startGame: (): Promise<GameView> => request<GameView>(games, { method: 'POST' }),

  listAds: (gameId: string): Promise<AdBoardView> =>
    request<AdBoardView>(`${games}/${encodeURIComponent(gameId)}/ads`),

  solve: (gameId: string, adId: string): Promise<SolveResultView> =>
    request<SolveResultView>(
      `${games}/${encodeURIComponent(gameId)}/ads/${encodeURIComponent(adId)}/solve`,
      { method: 'POST' },
    ),

  investigate: (gameId: string): Promise<InvestigationView> =>
    request<InvestigationView>(`${games}/${encodeURIComponent(gameId)}/investigate`, {
      method: 'POST',
    }),

  listShop: (gameId: string): Promise<ShopView> =>
    request<ShopView>(`${games}/${encodeURIComponent(gameId)}/shop`),

  buy: (gameId: string, itemId: string): Promise<PurchaseResultView> =>
    request<PurchaseResultView>(
      `${games}/${encodeURIComponent(gameId)}/shop/${encodeURIComponent(itemId)}/buy`,
      { method: 'POST' },
    ),

  autoPlayStep: (gameId: string): Promise<AutoPlayStepView> =>
    request<AutoPlayStepView>(`${games}/${encodeURIComponent(gameId)}/autoplay/step`, {
      method: 'POST',
    }),
}
