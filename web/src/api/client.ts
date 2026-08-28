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

const NETWORK_MESSAGE = 'Could not reach the server. Check your connection and try again.'
const UNREADABLE_MESSAGE = 'The server responded in a way we could not read.'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, { headers: { Accept: 'application/json' }, ...init })
  } catch (cause) {
    throw new ApiError('NETWORK_ERROR', NETWORK_MESSAGE, 0, { cause })
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
 * Anything that is not a readable problem body is attributed by status: a gateway failure is the
 * dev proxy or the container reporting that the backend itself is down, which is a different story
 * for the player than a bug on our side.
 *
 * The code is checked for membership rather than merely for presence. Parsing a body does not
 * make it ours — an intermediary can answer with a problem document of its own — and a code the
 * app has no vocabulary for would travel as far as the presentation lookup before turning into a
 * `TypeError` inside a caller's `catch`. Attributing it by status instead loses nothing: an
 * unrecognised code carries no more meaning here than no code at all.
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
