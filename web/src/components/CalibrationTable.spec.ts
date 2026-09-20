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
  it('says how much it has seen before it says anything about the model', () => {
    expect(render([], 0, 0).text()).toContain('Nothing attempted yet')
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
    const table = render([
      aRow({ attempts: 2, successes: 2, observed: 1, delta: 0.13, enough: false }),
    ])

    expect(table.text()).toContain('too few to read')
    expect(table.text()).not.toContain('too cautious')
  })

  /**
   * The table was 400px wide inside a 290px sheet, so on a phone it scrolled sideways instead of
   * fitting. Asserted as intent per column rather than as a width, because the width is the
   * browser's answer and this is the question put to it.
   */
  it('stands three columns down below `sm` rather than scrolling six of them sideways', () => {
    const table = render([aRow()])

    const headers = table
      .findAll('thead th')
      .map((th) => ({ column: th.text(), onAPhone: !th.classes('hidden') }))

    expect(headers).toEqual([
      { column: 'Odds', onAPhone: true },
      { column: 'Tier', onAPhone: false },
      { column: 'Tried', onAPhone: false },
      { column: 'Model said', onAPhone: true },
      { column: 'Actually', onAPhone: true },
      { column: 'Verdict', onAPhone: true },
    ])

    // The floor that forced the scroll, now applied from `sm` up only.
    expect(table.get('table').classes()).toContain('sm:min-w-100')
    expect(table.get('table').classes()).not.toContain('min-w-100')

    // Nothing was actually lost with `Tried`: it is the denominator the next column prints.
    expect(table.get('tbody tr').text()).toContain('9/10')
  })

  it('lets a keyboard scroll the table for the widths where it still overflows', () => {
    expect(render([aRow()]).get('.overflow-x-auto').attributes('tabindex')).toBe('0')
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
