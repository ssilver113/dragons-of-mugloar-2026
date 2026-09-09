import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import MissionResult from './MissionResult.vue'
import type { PendingKind, ResultTone } from './MissionResult.vue'

function render(
  props: {
    pending?: PendingKind | null
    solverRunning?: boolean
    outcome?: { tone: ResultTone; title: string; body: string } | null
  } = {},
) {
  return mount(MissionResult, {
    props: { pending: null, solverRunning: false, outcome: null, ...props },
  })
}

const WON = { tone: 'success' as const, title: 'Mission accomplished', body: 'It went well.' }

describe('MissionResult', () => {
  /**
   * The whole reason this component exists. The store clears the last outcome the moment a turn
   * starts, so a box rendered behind a `v-if` was absent for the length of every action and the
   * board below it moved twice a turn.
   */
  it('keeps one box, at one size, whatever is happening', () => {
    const boxes = [
      render(),
      render({ pending: 'solve' }),
      render({ solverRunning: true }),
      render({ outcome: WON }),
    ].map((wrapper) => wrapper.get('[role="status"]'))

    for (const box of boxes) {
      expect(box.classes()).toContain('h-24')
    }
  })

  it('says what is waiting, and says it differently for each kind of turn', () => {
    expect(render({ pending: 'solve' }).text()).toContain('Taking the job')
    expect(render({ pending: 'purchase' }).text()).toContain('At the counter')
    expect(render({ pending: 'investigation' }).text()).toContain('scouts are out')
  })

  /** A turn in flight outranks the turn before it; the solver outranks a stale player result. */
  it('reports the newest thing that is true', () => {
    expect(render({ pending: 'solve', outcome: WON }).text()).toContain('Taking the job')
    expect(render({ solverRunning: true, outcome: WON }).text()).toContain(
      'The solver has the game',
    )
    expect(render({ outcome: WON }).text()).toContain('Mission accomplished')
    expect(render().text()).toContain('No job taken yet')
  })

  /**
   * Three announcements a turn is what happens if the placeholder is in the live region too, and
   * two of them would be saying that nothing has happened.
   */
  it('announces a finished turn and nothing else', () => {
    expect(render({ outcome: WON }).get('[role="status"]').attributes('aria-live')).toBe('polite')
    expect(render({ pending: 'solve' }).get('[role="status"]').attributes('aria-live')).toBe('off')
    expect(render().get('[role="status"]').attributes('aria-live')).toBe('off')
  })

  /**
   * The sigil follows the outcome the game judged. A tone it did not judge — a purchase, a
   * scouting report — leaves the dragon standing, because nothing about it went either way.
   */
  it('puts the dragon in the mood the turn earned', () => {
    const lost = render({
      outcome: { tone: 'failure', title: 'Mission failed', body: 'It did not go well.' },
    })

    expect(lost.get('[role="status"]').attributes('data-tone')).toBe('failure')
    expect(lost.get('img').classes()).toContain('defeated')

    const won = render({ outcome: WON })
    expect(won.get('img').classes()).toContain('victorious')

    const bought = render({
      outcome: { tone: 'info', title: 'Bought a potion', body: 'The shop obliged.' },
    })
    expect(bought.get('img').classes()).toContain('idle')
  })

  /**
   * The painted edge is the one thing the tone changes, so an unjudged turn has to leave it unset
   * rather than pick a third colour. A plate the stylesheet cannot find a tone for falls back to
   * its own rim, which is what "no verdict" looks like.
   */
  it('paints an edge only for a turn the game judged', () => {
    for (const wrapper of [
      render(),
      render({ pending: 'solve' }),
      render({ solverRunning: true }),
    ]) {
      expect(wrapper.get('[role="status"]').attributes('data-tone')).toBe('none')
    }

    expect(
      render({ outcome: { tone: 'info', title: 'The scouts are back', body: 'Nothing risked.' } })
        .get('[role="status"]')
        .attributes('data-tone'),
    ).toBe('info')
  })
})
