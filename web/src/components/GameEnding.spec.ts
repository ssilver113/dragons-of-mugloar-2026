import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GameEnding from './GameEnding.vue'
import { aGame } from '../test/fixtures'

function render(props: { ending?: 'finished' | 'lost'; starting?: boolean } = {}) {
  return mount(GameEnding, {
    props: {
      ending: 'finished',
      game: aGame({ score: 1240, turn: 63, finished: true }),
      starting: false,
      ...props,
    },
    attachTo: document.body,
  })
}

describe('GameEnding', () => {
  it('reports the score the run reached and the turns it took', () => {
    expect(render().text()).toContain('1240 points after 63 turns')
  })

  /**
   * The dragon was fine; the server stopped tracking it. Neither the copy nor the seal may say
   * otherwise, which is why the defeated sigil belongs to one ending and not the other.
   */
  it('does not call a forgotten session a defeat', () => {
    const lost = render({ ending: 'lost' })

    expect(lost.get('h2').text()).toBe('This game was lost')
    expect(lost.text()).toContain('no longer tracking this game')
    expect(lost.text()).toContain('It was worth')
    expect(lost.findAll('img')).toHaveLength(0)
  })

  it('shows the fallen dragon when the run really ended in one', () => {
    const finished = render({ ending: 'finished' })

    expect(finished.get('h2').text()).toBe('The dragon has fallen')
    expect(finished.text()).not.toContain('no longer tracking')
    expect(finished.findAll('img')).toHaveLength(1)
  })

  /** The button that ended the run has unmounted, so focus would otherwise fall to the document. */
  it('takes focus as soon as it replaces the board', () => {
    const ending = render()

    expect(document.activeElement).toBe(ending.element)
    ending.unmount()
  })

  it('offers the way out, and refuses a second press while the next game is being dealt', async () => {
    const ending = render()

    await ending.get('button').trigger('click')

    expect(ending.emitted('restart')).toHaveLength(1)
    expect(render({ starting: true }).get('button').attributes('disabled')).toBeDefined()
    expect(render({ starting: true }).get('button').text()).toBe('Starting…')
  })

  /**
   * The three screens the app speaks on for itself — this one, the start screen and the abandon
   * footer — are plates rather than panels, so the last plain surfaces in the app are furniture
   * like everything around them. The ring is kept: the panel takes focus when it replaces the
   * board, and a plate has no focus treatment of its own.
   */
  it('is a plate, and keeps the ring it takes focus with', () => {
    expect(render().classes()).toEqual(expect.arrayContaining(['oak-plate', 'focus-ring']))
    expect(render().classes()).not.toContain('panel')
  })
})
