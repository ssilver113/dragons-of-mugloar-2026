import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AutoPlayControls from './AutoPlayControls.vue'

function render(props: Partial<InstanceType<typeof AutoPlayControls>['$props']> = {}) {
  return mount(AutoPlayControls, {
    props: {
      running: false,
      stepping: false,
      waiting: false,
      speed: 'normal',
      canPlay: true,
      busy: false,
      halt: null,
      turns: 0,
      ...props,
    },
  })
}

const buttonLabelled = (wrapper: ReturnType<typeof render>, label: string) =>
  wrapper.findAll('button').find((button) => button.text() === label)

describe('AutoPlayControls', () => {
  it('offers Run while idle and Pause while running', () => {
    expect(buttonLabelled(render(), 'Run')).toBeDefined()
    expect(buttonLabelled(render(), 'Pause')).toBeUndefined()

    const running = render({ running: true })
    expect(buttonLabelled(running, 'Pause')).toBeDefined()
    expect(buttonLabelled(running, 'Run')).toBeUndefined()
  })

  it('keeps Pause reachable mid-turn, which is the whole point of it', () => {
    const controls = render({ running: true, busy: true })

    expect(buttonLabelled(controls, 'Pause')?.attributes('disabled')).toBeUndefined()
    expect(buttonLabelled(controls, 'Step')?.attributes('disabled')).toBeDefined()
  })

  it('withholds Run and Step while the player has a turn of their own in flight', () => {
    const controls = render({ busy: true })

    expect(buttonLabelled(controls, 'Run')?.attributes('disabled')).toBeDefined()
    expect(buttonLabelled(controls, 'Step')?.attributes('disabled')).toBeDefined()
  })

  it('withholds both when there is no game to play', () => {
    const controls = render({ canPlay: false })

    expect(buttonLabelled(controls, 'Run')?.attributes('disabled')).toBeDefined()
    expect(buttonLabelled(controls, 'Step')?.attributes('disabled')).toBeDefined()
  })

  it('announces a rate limit as a wait rather than a failure', () => {
    expect(render({ running: true, waiting: true }).find('[role="status"]').text()).toContain(
      'Waiting',
    )
  })

  it('reports a speed change without owning the setting', async () => {
    const controls = render()

    await controls.get('select').setValue('max')

    expect(controls.emitted('update:speed')).toEqual([['max']])
  })

  it('keeps the same element when Run becomes Pause, so keyboard focus survives it', async () => {
    const controls = render()
    const before = buttonLabelled(controls, 'Run')?.element

    await controls.setProps({ running: true })

    expect(buttonLabelled(controls, 'Pause')?.element).toBe(before)
  })

  /** A player opening the game is here to play it, not to hand it straight over to the solver. */
  it('starts folded away', () => {
    expect(render().get('details').attributes('open')).toBeUndefined()
  })

  it('says enough while folded to tell working from waiting from stopped', () => {
    const summary = (props: Parameters<typeof render>[0]) => render(props).get('summary').text()

    expect(summary({})).toContain('idle')
    expect(summary({ running: true, turns: 12 })).toContain('running, 12 turns')
    expect(summary({ running: true, waiting: true })).toContain('rate limited, waiting')
    expect(summary({ halt: { kind: 'stalled', passes: 10 } })).toContain('stopped to check in')
  })

  /**
   * The buttons that answer a halt — keep going, try again — are in here, so a run that stops on
   * its own opens the panel rather than leaving the way out behind a twisty.
   */
  it('opens itself when the run stops, and never closes itself', async () => {
    const controls = render()

    await controls.setProps({ halt: { kind: 'stalled', passes: 10 } })
    expect(controls.get('details').attributes('open')).toBeDefined()

    await controls.setProps({ halt: null })
    expect(controls.get('details').attributes('open')).toBeDefined()
  })
})
