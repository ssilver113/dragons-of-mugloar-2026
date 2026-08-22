import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DecisionLog from './DecisionLog.vue'
import { ApiError } from '../api/client'
import { aDecision, aGame, aPass, aStep } from '../test/fixtures'
import type { Halt, LogEntry } from '../stores/autoplay'
import type { AutoPlayStepView } from '../api/types'

let nextId = 0
const entry = (step: AutoPlayStepView): LogEntry => ({ id: (nextId += 1), ...step })

function render(entries: LogEntry[], halt: Halt | null = null) {
  return mount(DecisionLog, { props: { entries, halt } })
}

describe('DecisionLog', () => {
  it('explains what the buttons are for before any turn is taken', () => {
    expect(render([]).text()).toContain('No turns taken yet')
  })

  it('puts the newest turn first, because a fast run outpaces reading', () => {
    const log = render([
      entry(aStep({ game: aGame({ turn: 1 }) })),
      entry(aStep({ game: aGame({ turn: 2 }) })),
    ])

    const turns = log.findAll('li').map((item) => item.text())
    expect(turns[0]).toContain('Turn 2')
    expect(turns[1]).toContain('Turn 1')
  })

  it('phrases the solver reason code rather than showing it', () => {
    const log = render([entry(aPass())])

    expect(log.text()).toContain('Passing beats gambling')
    expect(log.text()).not.toContain('PASSING_NOTHING_WORTH_A_TURN')
  })

  it('names the failure and the fallen job when a mission goes wrong', () => {
    const log = render([entry(aStep({ succeeded: false, message: 'You failed.' }))])

    expect(log.text()).toContain('Mission failed')
    expect(log.text()).toContain('Help Robin Webster to steal a shipment of gold')
  })

  it('shows what else was on the table, with the verdict each option earned', () => {
    const log = render([
      entry(
        aStep({
          decision: aDecision({
            ads: [
              { ...aDecision().ads[0]!, verdict: 'CHOSEN' },
              { ...aDecision().ads[0]!, adId: 'other', message: 'Slay a dragon', verdict: 'NEVER_ATTEMPT' },
            ],
          }),
        }),
      ),
    ])

    const rows = log.get('details').text()
    expect(rows).toContain('Slay a dragon')
    expect(rows).toContain('Never attempt')
    expect(rows).toContain('Chosen')
  })

  it('reads a purchase by its effect, since the item names are flavour', () => {
    const bought = (levelsGained: number, livesGained: number) =>
      render([
        entry(
          aStep({
            decision: aDecision({
              move: 'BUY_ITEM',
              targetId: 'hpot',
              reason: 'HEALING_LOW_ON_LIVES',
              items: [{ ...aDecision().items[0]!, levelsGained, livesGained, verdict: 'CHOSEN' }],
            }),
          }),
        ),
      ]).text()

    expect(bought(0, 1)).toContain('Bought a life')
    expect(bought(1, 0)).toContain('Bought an upgrade')
  })

  it('offers a way past a stall instead of just stopping', async () => {
    const log = render([entry(aPass())], { kind: 'stalled', passes: 10 })

    expect(log.text()).toContain('passed 10 turns in a row')
    await log.get('[role="status"] button').trigger('click')
    expect(log.emitted('keep-going')).toHaveLength(1)
  })

  it('reports a halt in the server’s own words, and offers to resume', async () => {
    const halt: Halt = {
      kind: 'error',
      error: new ApiError('UPSTREAM_UNAVAILABLE', 'The game is not answering.', 503),
    }
    const log = render([], halt)

    expect(log.get('[role="alert"]').text()).toContain('The game is not answering.')
    await log.get('[role="alert"] button').trigger('click')
    expect(log.emitted('retry')).toHaveLength(1)
  })

  it('does not offer to resume a run that cannot be resumed', () => {
    const halt: Halt = {
      kind: 'error',
      error: new ApiError('SESSION_EXPIRED', 'This game is no longer being tracked.', 404),
    }
    const log = render([], halt)

    expect(log.get('[role="alert"]').text()).toContain('no longer being tracked')
    expect(log.find('[role="alert"] button').exists()).toBe(false)
  })
})
