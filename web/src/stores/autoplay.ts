import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../api/client'
import { useGameStore } from './game'
import { persisted } from './persistence'
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
  { kind: 'finished' } | { kind: 'stalled'; passes: number } | { kind: 'error'; error: ApiError }

/**
 * Passes in a row before the loop stops to ask. A pass risks nothing, so a solver that correctly
 * declines every ad would otherwise never end the game.
 */
const STALL_LIMIT = 10

/** Waits after a rate limit, in order. Running out of them is what turns a wait into a halt. */
const RATE_LIMIT_WAITS_MS = [5000, 10000, 15000]

type TurnResult = 'continue' | 'halt' | 'rate-limited'

/**
 * The log of the game a reload interrupted, stamped with the game it belongs to so a restored
 * log can never be shown against a different one. Per tab, like the game it describes.
 */
interface StoredLog {
  gameId: string
  entries: LogEntry[]
  nextId: number
}

const savedLog = persisted<StoredLog>('session', 'mugloar.log')

/** A preference, and one worth keeping: a run at Max is a different thing to watch than at Slow. */
const savedSpeed = persisted<SpeedId>('local', 'mugloar.speed')

export const useAutoPlayStore = defineStore('autoplay', () => {
  const games = useGameStore()

  /** Read before the reset watcher exists: restoring a game id is what empties the stored log. */
  const remembered = savedLog.read()

  const log = ref<LogEntry[]>([])
  const speed = ref<SpeedId>(rememberedSpeed())
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
  const canPlay = computed(() => games.playable)

  /** Run until the game ends, the solver stalls, or something breaks. Client-side, so it can be
   * stopped between any two turns. */
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

  /** A turn already sent is already spent, so it settles and is logged rather than dropped. */
  function pause(): void {
    running.value = false
    wake?.()
  }

  /** The streak restarts rather than switching off, so a still-stuck game asks again. */
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
    savedLog.clear()
  }

  /**
   * Put the interrupted run's log back, only if the game on screen is the one it was written for.
   * The halt is not restored: the reload already answered whatever stopped the loop.
   */
  function restore(gameId: string): void {
    if (remembered?.gameId !== gameId) {
      return
    }
    log.value = remembered.entries
    nextId = remembered.nextId
  }

  // A new game is a new log. Synchronous, so a reset cannot land after a turn recorded in the
  // same tick and swallow it.
  watch(() => games.game?.gameId, reset, { flush: 'sync' })

  /**
   * Called rather than watched. The log grows by `push`, which leaves the ref's own value
   * identity alone, so a shallow watcher would never fire and a deep one would walk every entry
   * of a long run to learn what the caller already knows.
   *
   * The whole log is re-serialised each turn, which is the one thing this shape cannot avoid:
   * Web Storage has no append. Measured before accepting it — at 300 turns the stored payload is
   * about 1 MB and a write costs 3ms, roughly 440ms spread across a whole game. A run long enough
   * to exhaust the quota loses only its restorability, which `persisted` already swallows.
   */
  function remember(): void {
    const current = games.game
    if (current && log.value.length) {
      savedLog.write({ gameId: current.gameId, entries: log.value, nextId })
    }
  }

  watch(speed, (chosen) => savedSpeed.write(chosen))

  async function takeTurn(refreshBoard: boolean): Promise<TurnResult> {
    try {
      record(await games.autoPlayStep(refreshBoard))
    } catch (e) {
      const failure = asApiError(e)
      // The edge refused it, so no turn was spent upstream and there is nothing to undo.
      if (failure.code === 'UPSTREAM_RATE_LIMITED') {
        return 'rate-limited'
      }
      halt.value = { kind: 'error', error: failure }
      return 'halt'
    }

    // Only reached when the turn landed, so the only way to stop being playable is the dragon dying.
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
    // Appended rather than rebuilt: a spread copies every earlier turn, which is O(n²) over a
    // run that can reach three hundred of them.
    log.value.push({
      id: nextId,
      decision: turn.decision,
      succeeded: turn.succeeded,
      message: turn.message,
      game: turn.game,
    })
    remember()
    passStreak = turn.decision.reason === 'PASSING_NOTHING_WORTH_A_TURN' ? passStreak + 1 : 0
  }

  /** At max speed the loop skips the per-turn board refresh, so the board is caught up here. */
  async function settle(): Promise<void> {
    if (delayMs.value === 0 && games.playable) {
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
    restore,
  }
})

/** A speed written by a build that named them differently is not one this build can select. */
function rememberedSpeed(): SpeedId {
  const saved = savedSpeed.read()
  return SPEEDS.some((option) => option.id === saved) ? (saved as SpeedId) : 'normal'
}

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
