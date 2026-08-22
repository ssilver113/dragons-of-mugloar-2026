import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../api/client'
import { useGameStore } from './game'
import type { AutoPlayStepView, DecisionView, GameView } from '../api/types'

export type SpeedId = 'slow' | 'normal' | 'fast' | 'max'

/**
 * Pacing is a real control, not a cosmetic one: the upstream rate limits on burst, so
 * "run to completion" at zero delay is the mode most likely to hit it.
 */
export const SPEEDS: ReadonlyArray<{ id: SpeedId; label: string; delayMs: number }> = [
  { id: 'slow', label: 'Slow', delayMs: 1500 },
  { id: 'normal', label: 'Normal', delayMs: 600 },
  { id: 'fast', label: 'Fast', delayMs: 150 },
  { id: 'max', label: 'Max', delayMs: 0 },
]

/** One turn, kept whole: the state it left behind outlives the board it was decided against. */
export interface LogEntry {
  /** Monotonic within a game, so a re-rendered list keeps stable keys even at max speed. */
  id: number
  decision: DecisionView
  succeeded: boolean
  message: string | null
  game: GameView
}

/**
 * Why the loop stopped. `finished` is the game ending, which is the goal; the other two are the
 * loop declining to keep going, and both are resumable by the player.
 */
export type Halt =
  | { kind: 'finished' }
  | { kind: 'stalled'; passes: number }
  | { kind: 'error'; error: ApiError }

/**
 * Passes in a row before the loop stops to ask. At one life with too little gold to buy anything,
 * the solver correctly declines every ad and passes instead — which is right for a turn and wrong
 * for a hundred, because a pass risks nothing and so the game never ends on its own.
 */
const STALL_LIMIT = 10

/** Waits after a rate limit, in order. Running out of them is what turns a wait into a halt. */
const RATE_LIMIT_WAITS_MS = [5000, 10000, 15000]

type TurnResult = 'continue' | 'halt' | 'rate-limited'

export const useAutoPlayStore = defineStore('autoplay', () => {
  const games = useGameStore()

  const log = ref<LogEntry[]>([])
  const speed = ref<SpeedId>('normal')
  const running = ref(false)
  const stepping = ref(false)
  const waiting = ref(false)
  const halt = ref<Halt | null>(null)

  let passStreak = 0
  let nextId = 0
  /** Ends the current delay early, so pausing does not have to outwait the speed setting. */
  let wake: (() => void) | null = null

  const delayMs = computed(() => SPEEDS.find((s) => s.id === speed.value)?.delayMs ?? 600)
  /** A turn is in flight or about to be. Manual controls stay out of the way while it is. */
  const active = computed(() => running.value || stepping.value)
  const canPlay = computed(() => games.started && !games.finished)

  /**
   * Run until the game ends, the solver stalls, or something breaks. There is no server-side loop
   * by design: this one shows every turn as it lands and can be stopped between any two of them.
   */
  async function run(): Promise<void> {
    if (active.value || !canPlay.value) {
      return
    }
    halt.value = null
    running.value = true

    let waits = 0
    while (running.value) {
      const result = await takeTurn(delayMs.value > 0)

      if (result === 'rate-limited') {
        const pause = RATE_LIMIT_WAITS_MS[waits]
        if (pause === undefined) {
          halt.value = { kind: 'error', error: rateLimitGaveUp() }
          break
        }
        waits += 1
        waiting.value = true
        await sleep(pause)
        continue
      }

      waits = 0
      waiting.value = false
      if (result === 'halt' || !running.value) {
        break
      }
      await sleep(delayMs.value)
    }

    running.value = false
    waiting.value = false
    await settle()
  }

  /** One turn, on demand. Refuses to overlap a run rather than queueing behind it. */
  async function step(): Promise<void> {
    if (active.value || !canPlay.value) {
      return
    }
    halt.value = null
    stepping.value = true
    try {
      if ((await takeTurn(true)) === 'rate-limited') {
        halt.value = { kind: 'error', error: rateLimitGaveUp() }
      }
    } finally {
      stepping.value = false
    }
  }

  /**
   * Stop the loop. Aborting a run and pausing one are the same operation because there is nothing
   * to roll back: a turn already sent upstream is already spent, so it is allowed to settle and be
   * logged rather than dropped. What differs is only whether the player presses Run again.
   */
  function pause(): void {
    running.value = false
    wake?.()
  }

  /**
   * Carry on past a stall. The streak restarts rather than being switched off, so a game that is
   * still genuinely stuck comes back and says so instead of spinning unwatched.
   */
  function keepGoing(): void {
    if (halt.value?.kind !== 'stalled') {
      return
    }
    passStreak = 0
    void run()
  }

  function reset(): void {
    pause()
    log.value = []
    halt.value = null
    passStreak = 0
    nextId = 0
  }

  // A new game is a new log. Watching the id rather than exposing a reset the view must remember
  // to call keeps the two from drifting apart. Synchronous, so a reset can never land after a turn
  // recorded in the same tick and swallow it.
  watch(() => games.game?.gameId, reset, { flush: 'sync' })

  async function takeTurn(refreshBoard: boolean): Promise<TurnResult> {
    try {
      record(await games.autoPlayStep(refreshBoard))
    } catch (e) {
      const failure = asApiError(e)
      // The edge refused the request, so no turn was spent upstream and there is nothing to undo.
      if (failure.code === 'UPSTREAM_RATE_LIMITED') {
        return 'rate-limited'
      }
      halt.value = { kind: 'error', error: failure }
      return 'halt'
    }

    if (games.finished) {
      halt.value = { kind: 'finished' }
      return 'halt'
    }
    if (passStreak >= STALL_LIMIT) {
      halt.value = { kind: 'stalled', passes: passStreak }
      return 'halt'
    }
    return 'continue'
  }

  function record(turn: AutoPlayStepView): void {
    nextId += 1
    log.value = [
      ...log.value,
      {
        id: nextId,
        decision: turn.decision,
        succeeded: turn.succeeded,
        message: turn.message,
        game: turn.game,
      },
    ]
    passStreak = turn.decision.reason === 'PASSING_NOTHING_WORTH_A_TURN' ? passStreak + 1 : 0
  }

  /** At max speed the loop skips the per-turn board refresh, so the board is caught up here. */
  async function settle(): Promise<void> {
    if (delayMs.value === 0 && games.started && !games.finished) {
      await games.refreshAds()
    }
  }

  function sleep(ms: number): Promise<void> {
    if (ms <= 0) {
      return Promise.resolve()
    }
    return new Promise((resolve) => {
      const finish = (): void => {
        clearTimeout(timer)
        wake = null
        resolve()
      }
      const timer = setTimeout(finish, ms)
      wake = finish
    })
  }

  return {
    log,
    speed,
    running,
    stepping,
    waiting,
    halt,
    delayMs,
    active,
    canPlay,
    run,
    step,
    pause,
    keepGoing,
    reset,
  }
})

function rateLimitGaveUp(): ApiError {
  return new ApiError(
    'UPSTREAM_RATE_LIMITED',
    'The game is still rate limiting us. Give it a minute, then carry on.',
    429,
  )
}

function asApiError(e: unknown): ApiError {
  return e instanceof ApiError
    ? e
    : new ApiError('INTERNAL_ERROR', 'Something went wrong. Try again.', 0, { cause: e })
}
