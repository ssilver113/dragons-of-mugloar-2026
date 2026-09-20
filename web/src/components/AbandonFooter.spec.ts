import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AbandonFooter from './AbandonFooter.vue'
import { aGame } from '../test/fixtures'

function render(props: { canAbandon?: boolean; starting?: boolean; solverActive?: boolean } = {}) {
  return mount(AbandonFooter, {
    props: {
      game: aGame({ score: 615, turn: 40 }),
      canAbandon: true,
      starting: false,
      solverActive: false,
      ...props,
    },
    attachTo: document.body,
  })
}

const buttonLabelled = (footer: ReturnType<typeof render>, label: string) =>
  footer.findAll('button').find((button) => button.text() === label)

describe('AbandonFooter', () => {
  it('names what the run is worth before throwing it away', async () => {
    const footer = render()

    await footer.get('button').trigger('click')

    expect(footer.text()).toContain('It is worth 615 points after 40 turns')
    expect(footer.emitted('abandon')).toBeUndefined()
  })

  it('abandons only on the second press', async () => {
    const footer = render()

    await footer.get('button').trigger('click')
    await buttonLabelled(footer, 'Yes, start a new game')?.trigger('click')

    expect(footer.emitted('abandon')).toHaveLength(1)
  })

  /** The question is a dead end for the keyboard otherwise: no button on it says "never mind". */
  it('takes escape for an answer of no', async () => {
    const footer = render()

    await footer.get('button').trigger('click')
    expect(footer.text()).toContain('Abandon this run?')

    await footer.get('footer').trigger('keydown.esc')

    expect(footer.text()).not.toContain('Abandon this run?')
    expect(footer.emitted('abandon')).toBeUndefined()
  })

  /**
   * Both ends of the dance: the button that asked has been replaced, and so has the one that
   * answered, so focus would fall to the document either way round.
   */
  it('keeps focus on the question, and hands it back when the answer is no', async () => {
    const footer = render()

    await footer.get('button').trigger('click')
    expect(document.activeElement).toBe(buttonLabelled(footer, 'Yes, start a new game')?.element)

    await buttonLabelled(footer, 'Keep playing')?.trigger('click')
    expect(document.activeElement).toBe(buttonLabelled(footer, 'Start a new game')?.element)

    footer.unmount()
  })

  it('cannot be asked mid-turn, and says the solver is why when the solver is why', () => {
    const held = render({ canAbandon: false, solverActive: true })

    expect(held.get('button').attributes('disabled')).toBeDefined()
    expect(held.text()).toContain('Pause the solver first')
    expect(render().text()).toContain('deals a fresh board')
  })

  /** A turn already sent would land on the game that replaced it. */
  it('refuses the confirmation itself while a turn is in flight', async () => {
    const footer = render()
    await footer.get('button').trigger('click')
    await footer.setProps({ canAbandon: false })

    expect(buttonLabelled(footer, 'Yes, start a new game')?.attributes('disabled')).toBeDefined()
  })

  /** A plate, like the other two screens the app speaks on for itself. */
  it('is a plate rather than the app’s plain panel', () => {
    expect(render().get('footer').classes()).toContain('oak-plate')
    expect(render().get('footer').classes()).not.toContain('panel')
  })
})
