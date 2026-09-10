import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdvisorPanel from './AdvisorPanel.vue'
import type { CalibrationRow } from '../stores/calibration'

function aRow(overrides: Partial<CalibrationRow> = {}): CalibrationRow {
  return {
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
}

function render(props: { advisor?: boolean; rows?: CalibrationRow[]; attempts?: number } = {}) {
  return mount(AdvisorPanel, {
    props: {
      advisor: false,
      sort: 'value',
      posture: 'balanced',
      filters: [],
      shown: 10,
      total: 10,
      lifeCost: 100,
      rows: [],
      attempts: 0,
      games: 0,
      ...props,
    },
  })
}

describe('AdvisorPanel', () => {
  it('is opt-in, and says which way round it is in words as well as in state', async () => {
    const panel = render()

    const advisor = panel.get('[role="switch"]')
    expect(advisor.attributes('aria-checked')).toBe('false')
    expect(panel.text()).toContain('the board is listed in the order it was posted')

    await advisor.trigger('click')
    expect(panel.emitted('toggle-advisor')).toHaveLength(1)

    const on = render({ advisor: true })
    expect(on.get('[role="switch"]').attributes('aria-checked')).toBe('true')
    expect(on.text()).toContain('Ranking 10 jobs')
  })

  /**
   * The switch is named from the title beside it, so the accessible name contains the word a voice
   * user can actually see on the control. An `aria-label` would have read better and been unusable.
   */
  it('names the switch from the heading it sits under', () => {
    const panel = render()

    expect(panel.get('[role="switch"]').attributes('aria-labelledby')).toBe(
      'advisor-title advisor-state',
    )
    expect(panel.get('#advisor-title').text()).toBe('The Advisor')
    expect(panel.get('#advisor-state').text()).toBe('Off')
  })

  it('keeps the controls behind the switch, like the rest of the advice', () => {
    expect(render().find('#ad-sort').exists()).toBe(false)
    expect(render({ advisor: true }).find('#ad-sort').exists()).toBe(true)
  })

  /**
   * A record of advice already taken is worth reading with the advice switched off. A record of
   * nothing is not, under a switch that is also off.
   */
  it('shows the tally whenever there is one to show', () => {
    expect(render().text()).not.toContain('How the advice has held up')
    expect(render({ advisor: true }).text()).toContain('How the advice has held up')
    expect(render({ rows: [aRow()], attempts: 10 }).text()).toContain('How the advice has held up')
  })

  it('folds, and starts open only because the advisor was already on', async () => {
    const shut = render()
    const twisty = shut.get('[aria-expanded]')
    expect(twisty.attributes('aria-expanded')).toBe('false')

    await twisty.trigger('click')
    expect(twisty.attributes('aria-expanded')).toBe('true')

    expect(render({ advisor: true }).get('[aria-expanded]').attributes('aria-expanded')).toBe(
      'true',
    )
  })

  /**
   * Switching it on is a request to see what it thinks, so the table opens. Switching it off is
   * not a request to have a panel shut under the cursor, so it stays open.
   */
  it('opens on the way on and stays open on the way off', async () => {
    const panel = render()
    await panel.setProps({ advisor: true })
    expect(panel.get('[aria-expanded]').attributes('aria-expanded')).toBe('true')

    await panel.setProps({ advisor: false })
    expect(panel.get('[aria-expanded]').attributes('aria-expanded')).toBe('true')
  })

  it('hands the tally reset upwards rather than acting on it', async () => {
    const panel = render({ advisor: true, rows: [aRow()], attempts: 10 })

    const clear = panel.findAll('button').find((button) => button.text() === 'Clear the tally')
    await clear?.trigger('click')

    expect(panel.emitted('reset-calibration')).toHaveLength(1)
  })
})
