import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ReputationPanel from './ReputationPanel.vue'
import type { ReputationView } from '../api/types'

function render(props: {
  reputation?: ReputationView | null
  disabled?: boolean
  scouting?: boolean
}) {
  return mount(ReputationPanel, {
    props: { reputation: null, disabled: false, scouting: false, ...props },
  })
}

describe('ReputationPanel', () => {
  /** Three zeroes would look like a measurement, and nothing has measured anything yet. */
  it('says the dragon has never been scouted rather than showing a reading of nothing', () => {
    const panel = render({ reputation: null })

    expect(panel.text()).toContain('Nobody has scouted')
    expect(panel.findAll('dd')).toHaveLength(0)
  })

  it('names the cost of scouting up front, since it is a turn either way', () => {
    expect(render({ reputation: null }).text()).toContain('costs a turn')
  })

  /** The fixings are there and the shields are not, which is the state said as a picture. */
  it('draws a mount for every faction while none of them has been read', () => {
    const panel = render({ reputation: null })

    expect(panel.findAll('.vacant')).toHaveLength(3)
    expect(panel.findAll('.shield img')).toHaveLength(0)
  })

  it('hangs a crest on every mount once the scouts are back', () => {
    const panel = render({ reputation: { people: 1, state: 2, underworld: 3 } })

    expect(panel.findAll('.vacant')).toHaveLength(0)
    expect(panel.findAll('.shield img')).toHaveLength(3)
  })

  /**
   * The sign is drawn out of the flow so the figures line up across the three plates. It has to
   * stay in the reading, though: lifted out of the text it would be a minus nobody hears.
   */
  it('keeps the sign in the reading even though it is set apart from the figure', () => {
    const panel = render({ reputation: { people: 2, state: -0.5, underworld: 0 } })

    expect(panel.findAll('dd .sign').map((s) => s.text())).toEqual(['+', '-', ''])
    expect(panel.findAll('dd').map((d) => d.text())).toEqual(['+2.0', '-0.5', '0.0'])
  })

  it('reads each faction out in signed tenths, so standing lost is visible as such', () => {
    const panel = render({ reputation: { people: 12.5, state: -3.25, underworld: 0 } })

    expect(panel.findAll('dd').map((cell) => cell.text())).toEqual(['+12.5', '-3.3', '0.0'])
  })

  it('names every faction in text, so the crests are never the only cue', () => {
    const panel = render({ reputation: { people: 1, state: 2, underworld: 3 } })

    expect(panel.findAll('dt').map((cell) => cell.text())).toEqual([
      'People',
      'State',
      'Underworld',
    ])
  })

  it('asks for scouts', async () => {
    const panel = render({ reputation: null })

    await panel.get('button').trigger('click')

    expect(panel.emitted('scout')).toHaveLength(1)
  })

  it('cannot be asked twice while the scouts are out, or while a turn is in flight', () => {
    expect(render({ scouting: true }).get('button').attributes('disabled')).toBeDefined()
    expect(render({ disabled: true }).get('button').attributes('disabled')).toBeDefined()
  })
})
