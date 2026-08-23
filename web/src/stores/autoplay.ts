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
 * Passes in a row before the loop stops to ask. At one life with too little gold to buy anything,
 * the solver correctly declines every ad and passes instead — which is right for a turn and wrong
 * for a hundred, because a pass risks nothing and so the game never ends on its own.
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

  /**
   * Snapshotted here, before the watcher below is registered. Restoring a game assigns a game id,
   * which resets this store and so empties the stored log — so the only safe time to read it is
   * before that can happen.
   */
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
    savedLog.clear()
  }

  /**
   * Put the interrupted run's log back, if the game now on screen is the one it was written for.
   * Called after the game itself is restored, never before: a log beside the wrong board would
   * be worse than no log.
   *
   * The halt is deliberately not restored. Whatever stopped the loop was answered by the reload —
   * Run is available again, and a stale "the solver has stopped to check in" would be describing
   * a moment that is now over.
   */
  function restore(gameId: string): void {
    if (remembered?.gameId !== gameId) {
      return
    }
    log.value = remembered.entries
    nextId = remembered.nextId
  }

  // A new game is a new log. Watching the id rather than exposing a reset the view must remember
  // to call keeps the two from drifting apart. Synchronous, so a reset can never land after a turn
  // recorded in the same tick and swallow it.
  watch(() => games.game?.gameId, reset, { flush: 'sync' })

  watch(log, (entries) => {
    const current = games.game
    if (current && entries.length) {
      savedLog.write({ gameId: current.gameId, entries, nextId })
    }
  })

  watch(speed, (chosen) => savedSpeed.write(chosen))

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

    // Only reached when the turn landed, so the session cannot have been lost: the one way to
    // stop being playable here is the dragon dying, which is what `finished` means.
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
