import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useCalibrationStore } from './calibration'
import type { Observation } from './calibration'
import type { ProbabilityTier } from '../api/types'

function attempt(overrides: Partial<Observation> = {}): Observation {
  return { label: 'Piece of cake', tier: 'SAFE', predicted: 0.8, success: true, ...overrides }
}

function record(store: ReturnType<typeof useCalibrationStore>, ...outcomes: boolean[]): void {
  outcomes.forEach((success) => store.record(attempt({ success })))
}

describe('calibration', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('starts with nothing to say', () => {
    const store = useCalibrationStore()

    expect(store.rows).toEqual([])
    expect(store.attempts).toBe(0)
  })

  it('counts what happened against what was predicted', () => {
    const store = useCalibrationStore()

    store.record(attempt({ predicted: 0.9, success: true }))
    store.record(attempt({ predicted: 0.7, success: false }))

    const [row] = store.rows
    expect(row.attempts).toBe(2)
    expect(row.successes).toBe(1)
    expect(row.observed).toBeCloseTo(0.5)
    expect(row.predicted).toBeCloseTo(0.8)
    expect(row.delta).toBeCloseTo(-0.3)
  })

  it('keeps each label apart, because whether a tier holds together is the thing being watched', () => {
    const store = useCalibrationStore()

    store.record(attempt({ label: 'Piece of cake' }))
    store.record(attempt({ label: 'Sure thing' }))

    expect(store.rows.map((row) => row.label)).toEqual(['Piece of cake', 'Sure thing'])
    expect(store.attempts).toBe(2)
  })

  it('reads from safest to most hopeless, whatever order the labels arrived in', () => {
    const store = useCalibrationStore()
    const tiers: [string, ProbabilityTier][] = [
      ['Risky', 'EVEN'],
      ['Impossible', 'DOOMED'],
      ['Sure thing', 'SAFE'],
      ['Quite likely', 'FAVOURABLE'],
    ]

    tiers.forEach(([label, tier]) => store.record(attempt({ label, tier })))

    expect(store.rows.map((row) => row.tier)).toEqual(['SAFE', 'FAVOURABLE', 'EVEN', 'DOOMED'])
  })

  it('says when a rate rests on too few attempts to mean anything', () => {
    const store = useCalibrationStore()

    record(store, true, true)
    expect(store.rows[0].enough).toBe(false)

    record(store, true)
    expect(store.rows[0].enough).toBe(true)
  })

  it('accumulates across games, which is what makes the estimate visibly settle', () => {
    const store = useCalibrationStore()

    store.noteGame()
    record(store, true, false)
    store.noteGame()
    record(store, true, true)

    expect(store.games).toBe(2)
    expect(store.attempts).toBe(4)
    expect(store.successes).toBe(3)
  })

  it('empties on request, games included', () => {
    const store = useCalibrationStore()
    store.noteGame()
    record(store, true)

    store.reset()

    expect(store.rows).toEqual([])
    expect(store.games).toBe(0)
  })
})
