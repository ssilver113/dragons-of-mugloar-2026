import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import DecisionEntry from './DecisionEntry.vue'
import { aDecision, aGame, aPass, aStep, anAdOption, anItemOption } from '../test/fixtures'
import type { AutoPlayStepView } from '../api/types'
import type { LogEntry } from '../stores/autoplay'

const render = (step: AutoPlayStepView = aStep()) =>
  mount(DecisionEntry, { props: { entry: { id: 1, ...step } as LogEntry } })

describe('DecisionEntry', () => {
  it('says what the turn did and what it was aimed at', () => {
    const row = render()

    expect(row.text()).toContain('Mission accomplished')
    expect(row.text()).toContain('Help Robin Webster to steal a shipment of gold')
  })

  /**
   * A ledger names its columns once, at the head. A phone has no head to name them at and neither
   * has a reader meeting a figure on its own, so every figure carries its unit in the tree.
   */
  it('carries the unit of every figure for a reader who never sees the column heads', () => {
    const row = render(
      aStep({ game: aGame({ turn: 7, score: 420, gold: 55, lives: 2, level: 3 }) }),
    )

    expect(row.text()).toContain('Turn 7')
    expect(row.text()).toContain('420 pts')
    expect(row.text()).toContain('55g')
    expect(row.text()).toContain('2 lives')
    expect(row.text()).toContain('lvl 3')
  })

  it('counts what was weighed, in the plural the count calls for', () => {
    const one = render(aStep({ decision: aDecision({ ads: [anAdOption()], items: [] }) }))
    const many = render(
      aStep({
        decision: aDecision({
          ads: [anAdOption(), anAdOption({ adId: 'other' })],
          items: [anItemOption(), anItemOption({ itemId: 'other' })],
        }),
      }),
    )

    expect(one.get('summary').text()).toBe('Weighed 1 job and 0 items')
    expect(many.get('summary').text()).toBe('Weighed 2 jobs and 2 items')
  })

  /**
   * Greying a row out is a colour cue, so the verdict is also spelled out in a column of its own.
   * Both are asserted together: dropping either one leaves the other carrying the meaning alone.
   */
  it('gives every option a verdict in words as well as in tone', () => {
    const row = render(
      aStep({
        decision: aDecision({
          ads: [
            anAdOption({ verdict: 'CHOSEN' }),
            anAdOption({ adId: 'other', message: 'Slay a dragon', verdict: 'NEVER_ATTEMPT' }),
          ],
        }),
      }),
    )

    const ruledOut = row.findAll('tbody tr')[1]!
    expect(ruledOut.text()).toContain('Slay a dragon')
    expect(ruledOut.text()).toContain('Never attempt')
    expect(ruledOut.classes()).toContain('text-ink-muted')
    expect(row.findAll('tbody tr')[0]!.classes()).toContain('text-ink')
  })

  it('shows the odds as a percentage and the score to one place', () => {
    const row = render(
      aStep({
        decision: aDecision({ ads: [anAdOption({ successProbability: 0.864, score: 8.94 })] }),
      }),
    )

    const cells = row
      .findAll('tbody tr')[0]!
      .findAll('td')
      .map((cell) => cell.text())
    expect(cells).toContain('86%')
    expect(cells).toContain('8.9')
  })

  it('quotes the upstream sentence when a mission produced one', () => {
    expect(render(aStep({ message: 'You failed.' })).text()).toContain('“You failed.”')
  })

  /** A pass has no message, and an empty pair of quotation marks would look like a lost line. */
  it('says nothing at all when the turn produced no sentence', () => {
    expect(render(aPass()).text()).not.toContain('“')
  })

  it('marks a failed mission in the danger tone as well as in its headline', () => {
    const row = render(aStep({ succeeded: false, message: 'You failed.' }))

    expect(row.get('.what p').text()).toBe('Mission failed')
    expect(row.get('.what p').classes()).toContain('text-danger')
  })
})
