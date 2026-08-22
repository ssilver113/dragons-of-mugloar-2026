import { describe, expect, it } from 'vitest'
import { headline, isRuledOut, itemEffect, target } from './decisionCopy'
import { aDecision, aPass, aStep, anAdOption, anItemOption } from '../test/fixtures'
import type { LogEntry } from '../stores/autoplay'

function anEntry(step = aStep()): LogEntry {
  return {
    id: 1,
    decision: step.decision,
    succeeded: step.succeeded,
    message: step.message,
    game: step.game,
  }
}

describe('itemEffect', () => {
  it('reads the effect off the numbers, because the names are flavour', () => {
    expect(itemEffect(anItemOption({ levelsGained: 1, livesGained: 0 }))).toBe('+1 level')
    expect(itemEffect(anItemOption({ levelsGained: 2, livesGained: 0 }))).toBe('+2 levels')
    expect(itemEffect(anItemOption({ levelsGained: 0, livesGained: 1 }))).toBe('+1 life')
  })

  /** No item on offer grants two, but the string is assembled rather than looked up. */
  it('pluralises lives properly for an item no shop has offered yet', () => {
    expect(itemEffect(anItemOption({ levelsGained: 0, livesGained: 2 }))).toBe('+2 lives')
  })

  it('says nothing rather than promising something for a price never measured', () => {
    expect(itemEffect(anItemOption({ levelsGained: 0, livesGained: 0 }))).toBe('unmeasured')
  })
})

describe('headline and target', () => {
  it('reports how a job went', () => {
    expect(headline(anEntry())).toBe('Mission accomplished')
    expect(headline(anEntry(aStep({ succeeded: false })))).toBe('Mission failed')
  })

  it('names a purchase by its effect, not by its flavour name', () => {
    const bought = (item: Partial<ReturnType<typeof anItemOption>>) =>
      headline(
        anEntry(
          aStep({
            decision: aDecision({
              move: 'BUY_ITEM',
              targetId: 'cs',
              reason: 'LEVELLING_BEHIND_TARGET',
              items: [anItemOption({ verdict: 'CHOSEN', ...item })],
            }),
          }),
        ),
      )

    expect(bought({ livesGained: 1, levelsGained: 0 })).toBe('Bought a life')
    expect(bought({ livesGained: 0, levelsGained: 2 })).toBe('Bought an upgrade')
  })

  it('reads a pass as a turn spent on nothing', () => {
    expect(headline(anEntry(aPass()))).toBe('Passed the turn')
    expect(target(anEntry(aPass()))).toContain('costs a turn and risks nothing')
  })

  /** A log entry outlives its board, so it has to survive an ad it can no longer point at. */
  it('does not fall over when the chosen option is missing from the entry', () => {
    const orphaned = anEntry(
      aStep({ decision: aDecision({ ads: [anAdOption({ verdict: 'OUTRANKED' })] }) }),
    )

    expect(target(orphaned)).toBe('A job that is no longer on the board')
  })
})

describe('isRuledOut', () => {
  it('separates beaten from excluded, which is what the greying-out means', () => {
    expect(isRuledOut('OUTRANKED')).toBe(false)
    expect(isRuledOut('CHOSEN')).toBe(false)
    expect(isRuledOut('NEVER_ATTEMPT')).toBe(true)
    expect(isRuledOut('UNAFFORDABLE')).toBe(true)
  })
})
