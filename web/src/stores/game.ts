import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api } from '../api/client'
import type { AdView, GameView, SolveResultView } from '../api/types'

export type RequestStatus = 'idle' | 'pending' | 'ready' | 'error'

export const useGameStore = defineStore('game', () => {
  const game = ref<GameView | null>(null)
  const ads = ref<AdView[]>([])
  const startStatus = ref<RequestStatus>('idle')
  const boardStatus = ref<RequestStatus>('idle')
  const solvingAdId = ref<string | null>(null)
  const error = ref<ApiError | null>(null)
  const lastOutcome = ref<SolveResultView | null>(null)
  const advisorEnabled = ref(false)

  const started = computed(() => game.value !== null)
  const finished = computed(() => game.value?.finished ?? false)
  const busy = computed(
    () =>
      startStatus.value === 'pending' ||
      boardStatus.value === 'pending' ||
      solvingAdId.value !== null,
  )

  /**
   * Ranking by our own estimate is itself advice, so it only applies when the player has asked
   * for advice. Off, the board stays exactly as the game posted it. Phase 8 puts the sort key in
   * the player's hands.
   */
  const orderedAds = computed(() =>
    advisorEnabled.value
      ? [...ads.value].sort((a, b) => b.expectedValue - a.expectedValue || a.expiresIn - b.expiresIn)
      : ads.value,
  )

  async function startGame(): Promise<void> {
    startStatus.value = 'pending'
    boardStatus.value = 'idle'
    error.value = null
    lastOutcome.value = null
    ads.value = []
    try {
      game.value = await api.startGame()
      startStatus.value = 'ready'
      await refreshAds()
    } catch (e) {
      game.value = null
      startStatus.value = 'error'
      error.value = asApiError(e)
    }
  }

  async function refreshAds(): Promise<void> {
    const current = game.value
    if (!current) {
      return
    }
    boardStatus.value = 'pending'
    try {
      const board = await api.listAds(current.gameId)
      game.value = board.game
      ads.value = board.ads
      boardStatus.value = 'ready'
    } catch (e) {
      boardStatus.value = 'error'
      error.value = asApiError(e)
    }
  }

  /**
   * Optimistic in the only way this game allows: whether the ad succeeds is unknowable here, but
   * the mechanical half of a turn is not — the ad leaves the board, the turn counter moves and
   * every other ad ages by one. The authoritative state replaces the guess on the way back, and a
   * failure restores the snapshot untouched.
   */
  async function solve(adId: string): Promise<void> {
    const current = game.value
    if (!current || current.finished || solvingAdId.value !== null) {
      return
    }
    const previousGame = current
    const previousAds = ads.value

    error.value = null
    lastOutcome.value = null
    solvingAdId.value = adId
    game.value = { ...current, turn: current.turn + 1 }
    ads.value = ageBoard(previousAds, adId)

    try {
      const result = await api.solve(current.gameId, adId)
      game.value = result.game
      lastOutcome.value = result
      if (!result.game.finished) {
        await refreshAds()
      }
    } catch (e) {
      game.value = previousGame
      ads.value = previousAds
      const failure = asApiError(e)
      error.value = failure
      // A stale board is what causes both of these, and a board fetch costs no turn upstream.
      if (failure.code === 'AD_NOT_AVAILABLE' || failure.code === 'INVALID_ACTION') {
        await refreshAds()
      }
    } finally {
      solvingAdId.value = null
    }
  }

  function toggleAdvisor(): void {
    advisorEnabled.value = !advisorEnabled.value
  }

  function dismissError(): void {
    error.value = null
  }

  return {
    game,
    ads,
    startStatus,
    boardStatus,
    solvingAdId,
    error,
    lastOutcome,
    advisorEnabled,
    started,
    finished,
    busy,
    orderedAds,
    startGame,
    refreshAds,
    solve,
    toggleAdvisor,
    dismissError,
  }
})

function ageBoard(board: AdView[], solvedAdId: string): AdView[] {
  // The server never returns an ad that has run out, so the prediction does not invent one.
  return board.filter((ad) => ad.adId !== solvedAdId && ad.expiresIn > 1).map(aged)
}

function aged(ad: AdView): AdView {
  const expiresIn = ad.expiresIn - 1
  // The one server-side rule mirrored here, so no card can show its last turn without the badge.
  const expiring = expiresIn <= 1 && !ad.flags.includes('EXPIRING_NEXT_TURN')
  return {
    ...ad,
    expiresIn,
    flags: expiring ? [...ad.flags, 'EXPIRING_NEXT_TURN'] : ad.flags,
  }
}

function asApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError('INTERNAL_ERROR', 'Something went wrong. Try again.', 0, { cause: e })
}
