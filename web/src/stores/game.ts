import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api } from '../api/client'
import { useCalibrationStore } from './calibration'
import type { AdView, AutoPlayStepView, GameView, ShopItemView } from '../api/types'

export type RequestStatus = 'idle' | 'pending' | 'ready' | 'error'

/**
 * What the last turn did, in the shape the banner needs. One slot rather than one per action:
 * only one turn can be the most recent, so two slots could only ever disagree.
 */
export type TurnOutcome =
  | { kind: 'solve'; success: boolean; message: string }
  | { kind: 'purchase'; success: boolean; item: ShopItemView }

export const useGameStore = defineStore('game', () => {
  const calibration = useCalibrationStore()

  const game = ref<GameView | null>(null)
  const ads = ref<AdView[]>([])
  const shopItems = ref<ShopItemView[]>([])
  const startStatus = ref<RequestStatus>('idle')
  const boardStatus = ref<RequestStatus>('idle')
  const shopStatus = ref<RequestStatus>('idle')
  const solvingAdId = ref<string | null>(null)
  const buyingItemId = ref<string | null>(null)
  const autoStepping = ref(false)
  const error = ref<ApiError | null>(null)
  const lastOutcome = ref<TurnOutcome | null>(null)
  const advisorEnabled = ref(false)

  const started = computed(() => game.value !== null)
  const finished = computed(() => game.value?.finished ?? false)

  /** A turn is in flight. Only one may be, whichever action started it — solver included. */
  const acting = computed(
    () => solvingAdId.value !== null || buyingItemId.value !== null || autoStepping.value,
  )
  const busy = computed(
    () => startStatus.value === 'pending' || boardStatus.value === 'pending' || acting.value,
  )

  async function startGame(): Promise<void> {
    startStatus.value = 'pending'
    boardStatus.value = 'idle'
    shopStatus.value = 'idle'
    error.value = null
    lastOutcome.value = null
    ads.value = []
    shopItems.value = []
    try {
      game.value = await api.startGame()
      startStatus.value = 'ready'
      calibration.noteGame()
      // The shop is static for the life of a game and listing it costs no turn, so it is fetched
      // once, up front: affordability is then answerable the moment the player looks.
      await Promise.all([refreshAds(), refreshShop()])
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

  /** The catalogue only, deliberately: the state riding along is older than a turn in flight. */
  async function refreshShop(): Promise<void> {
    const current = game.value
    if (!current) {
      return
    }
    shopStatus.value = 'pending'
    try {
      shopItems.value = (await api.listShop(current.gameId)).items
      shopStatus.value = 'ready'
    } catch (e) {
      shopStatus.value = 'error'
      error.value = asApiError(e)
    }
  }

  /**
   * Optimistic in the only way this game allows: whether the ad succeeds is unknowable here, but
   * the mechanical half of a turn is not. The ad leaves the board, the turn counter moves and
   * every other ad ages by one. The authoritative state replaces the guess on the way back, and a
   * failure restores the snapshot untouched.
   */
  async function solve(adId: string): Promise<void> {
    const current = game.value
    if (!current || current.finished || acting.value) {
      return
    }
    const previousGame = current
    const previousAds = ads.value
    // Read before the optimistic update takes the ad off the board: its label and the estimate
    // it carried are half of the calibration record, and the outcome is the other half.
    const attempted = previousAds.find((ad) => ad.adId === adId)

    error.value = null
    lastOutcome.value = null
    solvingAdId.value = adId
    game.value = { ...current, turn: current.turn + 1 }
    ads.value = ageBoard(previousAds, adId)

    try {
      const result = await api.solve(current.gameId, adId)
      game.value = result.game
      lastOutcome.value = { kind: 'solve', success: result.success, message: result.message }
      if (attempted) {
        calibration.record({
          label: attempted.probability,
          tier: attempted.probabilityTier,
          predicted: attempted.successProbability,
          success: result.success,
        })
      }
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

  /**
   * A purchase predicts more than a solve, because more of it is known: the price is fixed, the
   * effect was measured, and the turn ages the board exactly as a solve does. That leaves only
   * the shop's own refusal unpredictable, and the server refuses everything it can see coming.
   */
  async function buy(itemId: string): Promise<void> {
    const current = game.value
    const item = shopItems.value.find((candidate) => candidate.id === itemId)
    if (!current || current.finished || acting.value || !item || item.cost > current.gold) {
      return
    }
    const previousGame = current
    const previousAds = ads.value

    error.value = null
    lastOutcome.value = null
    buyingItemId.value = itemId
    game.value = {
      ...current,
      gold: current.gold - item.cost,
      lives: current.lives + item.livesGained,
      level: current.level + item.levelsGained,
      turn: current.turn + 1,
    }
    ads.value = ageBoard(previousAds)

    try {
      const result = await api.buy(current.gameId, itemId)
      game.value = result.game
      lastOutcome.value = { kind: 'purchase', success: result.success, item }
      if (!result.game.finished) {
        await refreshAds()
      }
    } catch (e) {
      game.value = previousGame
      ads.value = previousAds
      error.value = asApiError(e)
    } finally {
      buyingItemId.value = null
    }
  }

  /**
   * One turn taken by the solver. There is nothing to predict optimistically here — which move it
   * will pick is exactly what the call returns — so the state is only ever written from the
   * response, and a failure leaves everything as it was.
   *
   * Unlike `solve` and `buy` this throws instead of parking the failure in `error`: the auto-play
   * loop has to tell a rate limit, which is a wait, from a failure, which is a halt, and it shows
   * either one next to the log rather than in the app-wide banner.
   */
  async function autoPlayStep(refreshBoard: boolean): Promise<AutoPlayStepView> {
    const current = game.value
    if (!current || current.finished || acting.value) {
      throw new ApiError('INVALID_ACTION', 'The dragon cannot take a turn right now.', 0)
    }
    autoStepping.value = true
    try {
      const step = await api.autoPlayStep(current.gameId)
      game.value = step.game
      recordSolverAttempt(step)
      if (refreshBoard && !step.game.finished) {
        await refreshAds()
      }
      return step
    } finally {
      autoStepping.value = false
    }
  }

  /**
   * The solver's turns feed the same ledger the player's do. The ad is read off the decision
   * rather than off the board, which at max speed is a turn or more out of date.
   */
  function recordSolverAttempt(step: AutoPlayStepView): void {
    const { move, targetId, ads: weighed } = step.decision
    if (move !== 'SOLVE_AD' || targetId === null) {
      return
    }
    const chosen = weighed.find((option) => option.adId === targetId)
    if (chosen) {
      calibration.record({
        label: chosen.probability,
        tier: chosen.probabilityTier,
        predicted: chosen.successProbability,
        success: step.succeeded,
      })
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
    shopItems,
    startStatus,
    boardStatus,
    shopStatus,
    solvingAdId,
    buyingItemId,
    autoStepping,
    error,
    lastOutcome,
    advisorEnabled,
    started,
    finished,
    acting,
    busy,
    startGame,
    refreshAds,
    refreshShop,
    solve,
    buy,
    autoPlayStep,
    toggleAdvisor,
    dismissError,
  }
})

function ageBoard(board: AdView[], solvedAdId?: string): AdView[] {
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
