import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ApiError, api } from '../api/client'
import { endsTheSession, present } from '../api/errorPresentation'
import { useCalibrationStore } from './calibration'
import { persisted } from './persistence'
import type { AdView, AutoPlayStepView, GameView, ReputationView, ShopItemView } from '../api/types'

export type RequestStatus = 'idle' | 'pending' | 'ready' | 'error'

/**
 * What a reload needs to pick a game back up. Standing is here because only `investigate` carries
 * it and that costs a turn; the board and shop are not, because refetching them is free.
 */
interface StoredGame {
  gameId: string
  game: GameView
  reputation: ReputationView | null
}

/** Per tab: two tabs sharing one `gameId` would read-modify-write the same session. */
const savedGame = persisted<StoredGame>('session', 'mugloar.game')

/** A preference rather than game state, so it outlives the tab. */
const savedAdvisor = persisted<boolean>('local', 'mugloar.advisor')

/** What the last turn did. One slot, because only one turn can be the most recent. */
export type TurnOutcome =
  | { kind: 'solve'; success: boolean; message: string }
  | { kind: 'purchase'; success: boolean; item: ShopItemView }
  | { kind: 'investigation' }

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
  const investigating = ref(false)
  const autoStepping = ref(false)
  const sessionLost = ref(false)
  const error = ref<ApiError | null>(null)
  const lastOutcome = ref<TurnOutcome | null>(null)
  const advisorEnabled = ref(savedAdvisor.read() ?? false)
  const resuming = ref(false)
  /** A remembered game could not be picked up. A note, not a failed action, so not an `ApiError`. */
  const resumeFailed = ref(false)
  /** The last standing scouted, or null. Never inferred — an unscouted game says so. */
  const reputation = ref<ReputationView | null>(null)

  /** Whether this server simulates. A deployment fact, read once; a failed read leaves it false. */
  const offline = ref(false)

  /** Which build answered. From the server, not compiled in, so a stale server is visible. */
  const version = ref<string | null>(null)
  const builtAt = ref<string | null>(null)

  const started = computed(() => game.value !== null)
  const finished = computed(() => game.value?.finished ?? false)

  /** Why the game is no longer playable. Both endings replace the board rather than banner it. */
  const ending = computed<'lost' | 'finished' | null>(() => {
    if (game.value === null) {
      return null
    }
    return sessionLost.value ? 'lost' : game.value.finished ? 'finished' : null
  })
  const playable = computed(() => started.value && ending.value === null)

  /** A turn is in flight. Only one may be, whichever action started it — solver included. */
  const acting = computed(
    () =>
      solvingAdId.value !== null ||
      buyingItemId.value !== null ||
      investigating.value ||
      autoStepping.value,
  )
  const busy = computed(
    () => startStatus.value === 'pending' || boardStatus.value === 'pending' || acting.value,
  )

  /** Silent on failure: this only feeds a caveat badge, not worth blocking startup for. */
  async function loadMeta(): Promise<void> {
    try {
      const meta = await api.meta()
      offline.value = meta.offline
      version.value = meta.version ?? null
      builtAt.value = meta.builtAt ?? null
    } catch {
      offline.value = false
    }
  }

  /**
   * Pick a remembered game back up. Costs two free GETs and never a turn. Stored state is applied
   * before them so the start screen never flashes. Returns whether a game is now on screen.
   */
  async function resume(): Promise<boolean> {
    const saved = savedGame.read()
    if (!saved || typeof saved.gameId !== 'string' || saved.game?.gameId !== saved.gameId) {
      savedGame.clear()
      return false
    }

    game.value = saved.game
    reputation.value = saved.reputation
    if (saved.game.finished) {
      return true
    }

    resuming.value = true
    boardStatus.value = 'pending'
    try {
      await Promise.all([refreshAds(), refreshShop()])
    } finally {
      resuming.value = false
    }

    // The session went while the tab was closed, which is not an ending the panel may claim.
    if (sessionLost.value) {
      forget()
      resumeFailed.value = true
      return false
    }
    return true
  }

  /** Back to before a game, without touching what is remembered across games. */
  function forget(): void {
    game.value = null
    ads.value = []
    shopItems.value = []
    reputation.value = null
    sessionLost.value = false
    error.value = null
    lastOutcome.value = null
    startStatus.value = 'idle'
    boardStatus.value = 'idle'
    shopStatus.value = 'idle'
  }

  async function startGame(): Promise<void> {
    startStatus.value = 'pending'
    boardStatus.value = 'idle'
    shopStatus.value = 'idle'
    sessionLost.value = false
    resumeFailed.value = false
    error.value = null
    lastOutcome.value = null
    ads.value = []
    shopItems.value = []
    reputation.value = null
    try {
      game.value = await api.startGame()
      startStatus.value = 'ready'
      calibration.noteGame()
      // The shop is static for the game and free to list, so fetch it once up front.
      await Promise.all([refreshAds(), refreshShop()])
    } catch (e) {
      game.value = null
      startStatus.value = 'error'
      error.value = classify(e)
    }
  }

  async function refreshAds(): Promise<void> {
    const current = game.value
    // A forgotten session has nothing to reconcile against; the ending panel is the way out.
    if (!current || sessionLost.value) {
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
      fail(e)
    }
  }

  /** The catalogue only, deliberately: the state riding along is older than a turn in flight. */
  async function refreshShop(): Promise<void> {
    const current = game.value
    if (!current || sessionLost.value) {
      return
    }
    shopStatus.value = 'pending'
    try {
      shopItems.value = (await api.listShop(current.gameId)).items
      shopStatus.value = 'ready'
    } catch (e) {
      shopStatus.value = 'error'
      fail(e)
    }
  }

  /**
   * Predicts only the mechanical half of the turn — the ad leaves, the counter moves, the rest
   * age. Whether it succeeds is unknowable here. Rolls back on failure.
   */
  async function solve(adId: string): Promise<void> {
    const current = game.value
    if (!playable.value || !current || acting.value) {
      return
    }
    const previousGame = current
    const previousAds = ads.value
    // Read before the optimistic update takes the ad off the board.
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
      const failure = fail(e)
      // Both mean a stale board, and refetching one costs no turn.
      if (failure.code === 'AD_NOT_AVAILABLE' || failure.code === 'INVALID_ACTION') {
        await refreshAds()
      }
    } finally {
      solvingAdId.value = null
    }
  }

  /** Predicts the whole turn: price and effect are both known, unlike a solve's outcome. */
  async function buy(itemId: string): Promise<void> {
    const current = game.value
    const item = shopItems.value.find((candidate) => candidate.id === itemId)
    if (!playable.value || !current || acting.value || !item || item.cost > current.gold) {
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
      fail(e)
    } finally {
      buyingItemId.value = null
    }
  }

  /** Scouting: the only move that cannot cost a life, so worth offering when the board is bad. */
  async function investigate(): Promise<void> {
    const current = game.value
    if (!playable.value || !current || acting.value) {
      return
    }
    const previousGame = current
    const previousAds = ads.value

    error.value = null
    lastOutcome.value = null
    investigating.value = true
    game.value = { ...current, turn: current.turn + 1 }
    ads.value = ageBoard(previousAds)

    try {
      const result = await api.investigate(current.gameId)
      game.value = result.game
      reputation.value = result.reputation
      lastOutcome.value = { kind: 'investigation' }
      if (!result.game.finished) {
        await refreshAds()
      }
    } catch (e) {
      game.value = previousGame
      ads.value = previousAds
      fail(e)
    } finally {
      investigating.value = false
    }
  }

  /**
   * One solver turn. Nothing to predict — the move is what the call returns. Throws rather than
   * parking in `error`, because the loop must tell a rate limit (a wait) from a failure (a halt).
   */
  async function autoPlayStep(refreshBoard: boolean): Promise<AutoPlayStepView> {
    const current = game.value
    if (!playable.value || !current || acting.value) {
      throw new ApiError('INVALID_ACTION', 'The dragon cannot take a turn right now.', 0)
    }
    autoStepping.value = true
    try {
      const step = await api.autoPlayStep(current.gameId)
      game.value = step.game
      // An auto-played scout was paid for too, so it fills the crests like a manual one.
      if (step.reputation) {
        reputation.value = step.reputation
      }
      recordSolverAttempt(step)
      if (refreshBoard && !step.game.finished) {
        await refreshAds()
      }
      return step
    } catch (e) {
      // Classified even though it is rethrown: the session-ending codes must land on the state.
      throw classify(e)
    } finally {
      autoStepping.value = false
    }
  }

  /** Reads the ad off the decision, not the board, which at max speed lags by a turn or more. */
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

  // Written as it changes, not on unload: mobile kills suspended tabs without `beforeunload`.
  watch([game, reputation], ([current, standing]) => {
    if (current === null) {
      savedGame.clear()
    } else {
      savedGame.write({ gameId: current.gameId, game: current, reputation: standing })
    }
  })

  watch(advisorEnabled, (on) => savedAdvisor.write(on))

  function dismissError(): void {
    error.value = null
  }

  /**
   * Record what a failure means for the game itself. Callers roll back first, so `game` is
   * already the last state the server actually sent.
   */
  function classify(e: unknown): ApiError {
    const failure = asApiError(e)
    if (endsTheSession(failure.code)) {
      sessionLost.value = true
    } else if (failure.code === 'GAME_OVER' && game.value) {
      game.value = { ...game.value, finished: true }
    }
    return failure
  }

  /** Classify, then show. A terminal failure is left out of `error` — its panel already says it. */
  function fail(e: unknown): ApiError {
    const failure = classify(e)
    error.value = present(failure.code).severity === 'terminal' ? null : failure
    return failure
  }

  return {
    game,
    ads,
    offline,
    version,
    builtAt,
    shopItems,
    startStatus,
    boardStatus,
    shopStatus,
    solvingAdId,
    buyingItemId,
    investigating,
    autoStepping,
    reputation,
    sessionLost,
    error,
    lastOutcome,
    advisorEnabled,
    resuming,
    resumeFailed,
    started,
    finished,
    ending,
    playable,
    acting,
    busy,
    resume,
    startGame,
    refreshAds,
    refreshShop,
    solve,
    buy,
    investigate,
    autoPlayStep,
    loadMeta,
    toggleAdvisor,
    dismissError,
  }
})

function ageBoard(board: AdView[], solvedAdId?: string): AdView[] {
  // The server never returns an ad that has run out, so neither does the prediction.
  return board.filter((ad) => ad.adId !== solvedAdId && ad.expiresIn > 1).map(aged)
}

function aged(ad: AdView): AdView {
  const expiresIn = ad.expiresIn - 1
  // The one server rule mirrored client-side; changing either side means changing both.
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
