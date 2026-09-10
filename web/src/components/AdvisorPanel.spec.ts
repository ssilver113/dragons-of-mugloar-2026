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

  /**
   * The fold is `v-show`, because a height animated from a measured `scrollHeight` needs something
   * to measure. So the controls stay mounted and shut rather than unmounted — hidden from the
   * accessibility tree by `display: none` either way.
   */
  it('keeps the controls behind the switch, like the rest of the advice', () => {
    expect(render().get('#ad-sort').isVisible()).toBe(false)
    expect(render({ advisor: true }).get('#ad-sort').isVisible()).toBe(true)
  })

  /**
   * The tally goes with the advice rather than outliving it on screen. Off is shut, and a record of
   * how the advisor has done is not something to leave lying open under a switch that is off — the
   * end-of-game panel is where it is read once the run is over.
   */
  it('shows the tally with the table it belongs to', () => {
    const fold = (panel: ReturnType<typeof render>) => panel.get('#advisor-body')

    expect(fold(render()).text()).toContain('How the advice has held up')
    expect(fold(render()).isVisible()).toBe(false)
    expect(fold(render({ advisor: true })).isVisible()).toBe(true)
    expect(fold(render({ rows: [aRow()], attempts: 10 })).isVisible()).toBe(false)
  })

  /**
   * One control, not two. The switch used to sit beside a twisty that opened the same panel, which
   * allowed on-and-shut and off-and-open — four states for a thing that has two.
   */
  it('is the only control on the header, and folds with the advice', () => {
    const shut = render()
    expect(shut.findAll('[aria-expanded]')).toHaveLength(1)

    const swtch = shut.get('[role="switch"]')
    expect(swtch.attributes('aria-expanded')).toBe('false')
    expect(swtch.attributes('aria-controls')).toBe('advisor-body')

    expect(render({ advisor: true }).get('[role="switch"]').attributes('aria-expanded')).toBe(
      'true',
    )
  })

  it('opens and shuts with the switch it is behind', async () => {
    const panel = render()
    await panel.setProps({ advisor: true })
    expect(panel.get('[role="switch"]').attributes('aria-expanded')).toBe('true')

    await panel.setProps({ advisor: false })
    expect(panel.get('[role="switch"]').attributes('aria-expanded')).toBe('false')
  })

  it('hands the tally reset upwards rather than acting on it', async () => {
    const panel = render({ advisor: true, rows: [aRow()], attempts: 10 })

    const clear = panel.findAll('button').find((button) => button.text() === 'Clear the tally')
    await clear?.trigger('click')

    expect(panel.emitted('reset-calibration')).toHaveLength(1)
  })
})
