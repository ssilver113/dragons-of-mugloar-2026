import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CalibrationTable from './CalibrationTable.vue'
import type { CalibrationRow } from '../stores/calibration'

function aRow(overrides: Partial<CalibrationRow> = {}): CalibrationRow {
  const row: CalibrationRow = {
    label: 'Piece of cake',
    tier: 'SAFE',
    attempts: 10,
    successes: 9,
    observed: 0.9,
    predicted: 0.87,
    delta: 0.03,
    enough: true,
    ...overrides,
  }
  return row
}

function render(rows: CalibrationRow[], attempts = 10, games = 1) {
  return mount(CalibrationTable, { props: { rows, attempts, games } })
}

describe('CalibrationTable', () => {
  it('says what it has and has not seen before anyone opens it', () => {
    expect(render([], 0, 0).text()).toContain('nothing attempted yet')
    expect(render([aRow()], 24, 3).text()).toContain('24 jobs across 3 games')
  })

  it('puts the prediction next to what happened', () => {
    const table = render([aRow({ attempts: 10, successes: 9, observed: 0.9, predicted: 0.87 })])

    expect(table.text()).toContain('Piece of cake')
    expect(table.text()).toContain('87%')
    expect(table.text()).toContain('9/10')
    expect(table.text()).toContain('90%')
  })

  it('names the direction the model is wrong in, since a signed number does not', () => {
    expect(render([aRow({ delta: 0.02 })]).text()).toContain('on the money')
    expect(render([aRow({ delta: 0.3 })]).text()).toContain('too cautious')
    expect(render([aRow({ delta: -0.3 })]).text()).toContain('too hopeful')
  })

  it('refuses to read a rate off two attempts', () => {
    const table = render([aRow({ attempts: 2, successes: 2, observed: 1, delta: 0.13, enough: false })])

    expect(table.text()).toContain('too few to read')
    expect(table.text()).not.toContain('too cautious')
  })

  it('offers a way to start the tally over, but only once there is one', () => {
    expect(render([], 0, 0).text()).not.toContain('Clear the tally')

    const table = render([aRow()])
    expect(table.text()).toContain('Clear the tally')
  })

  it('reports the reset rather than clearing state it does not own', async () => {
    const table = render([aRow()])

    await table.get('button').trigger('click')

    expect(table.emitted('reset')).toHaveLength(1)
  })
})
