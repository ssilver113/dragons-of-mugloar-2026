import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { persisted } from './persistence'
import type { ProbabilityTier } from '../api/types'

/**
 * Below this many attempts a label's observed rate is noise, and the table says so rather than
 * printing a number a reader would take seriously. Three is not a statistical threshold; it is
 * the point at which "1 of 1 — 100%" stops being the loudest row on screen.
 */
export const MIN_SAMPLE = 3

/** Tier order as the model ranks them, so the table reads from safest to most hopeless. */
const TIER_ORDER: ProbabilityTier[] = ['SAFE', 'FAVOURABLE', 'EVEN', 'POOR', 'DOOMED', 'UNKNOWN']

/** One attempted ad, recorded the moment its outcome is known. */
export interface Observation {
  label: string
  tier: ProbabilityTier
  /** What the model gave this ad before the attempt, at the level and reward it was seen at. */
  predicted: number
  success: boolean
}

export interface CalibrationRow {
  label: string
  tier: ProbabilityTier
  attempts: number
  successes: number
  /** How often it actually worked. */
  observed: number
  /** What the model expected on average across those attempts. */
  predicted: number
  /** Observed minus predicted. Positive means the model was too pessimistic. */
  delta: number
  enough: boolean
}

interface Tally {
  label: string
  tier: ProbabilityTier
  attempts: number
  successes: number
  predictedSum: number
}

/**
 * A ledger of what the model said would happen against what did, kept per probability label.
 *
 * Per label rather than per tier deliberately: the model's own claim is that the labels inside a
 * tier are interchangeable, and this is the table on which that claim can be seen to hold or not.
 *
 * It accumulates across games and is only emptied on request. One game supplies a few dozen
 * attempts spread over eleven labels, which is too thin to show anything converging; several
 * games are what make the estimate visibly settle.
 */
/**
 * The ledger is the one thing here that is meant to accumulate over many games, so it is kept
 * where a closed tab does not end it — and emptied only by the button that says it will.
 */
const saved = persisted<{ tallies: Record<string, Tally>; games: number }>(
  'local',
  'mugloar.calibration',
)

export const useCalibrationStore = defineStore('calibration', () => {
  const restored = saved.read()
  const tallies = ref<Record<string, Tally>>(restored?.tallies ?? {})
  const games = ref(restored?.games ?? 0)

  watch([tallies, games], ([currentTallies, played]) =>
    saved.write({ tallies: currentTallies, games: played }),
  )

  function record(observation: Observation): void {
    const existing = tallies.value[observation.label]
    const tally: Tally = existing ?? {
      label: observation.label,
      tier: observation.tier,
      attempts: 0,
      successes: 0,
      predictedSum: 0,
    }
    tallies.value = {
      ...tallies.value,
      [observation.label]: {
        ...tally,
        attempts: tally.attempts + 1,
        successes: tally.successes + (observation.success ? 1 : 0),
        predictedSum: tally.predictedSum + observation.predicted,
      },
    }
  }

  /** Counted here rather than in the game store so the table can say what the sample spans. */
  function noteGame(): void {
    games.value += 1
  }

  function reset(): void {
    tallies.value = {}
    games.value = 0
  }

  const rows = computed<CalibrationRow[]>(() =>
    Object.values(tallies.value)
      .map((tally) => {
        const observed = tally.successes / tally.attempts
        const predicted = tally.predictedSum / tally.attempts
        return {
          label: tally.label,
          tier: tally.tier,
          attempts: tally.attempts,
          successes: tally.successes,
          observed,
          predicted,
          delta: observed - predicted,
          enough: tally.attempts >= MIN_SAMPLE,
        }
      })
      .sort(
        (a, b) =>
          TIER_ORDER.indexOf(a.tier) - TIER_ORDER.indexOf(b.tier) ||
          b.attempts - a.attempts ||
          a.label.localeCompare(b.label),
      ),
  )

  const attempts = computed(() => rows.value.reduce((sum, row) => sum + row.attempts, 0))
  const successes = computed(() => rows.value.reduce((sum, row) => sum + row.successes, 0))

  return { tallies, games, rows, attempts, successes, record, noteGame, reset }
})
