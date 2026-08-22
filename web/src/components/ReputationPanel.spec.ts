import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ReputationPanel from './ReputationPanel.vue'
import type { ReputationView } from '../api/types'

function render(props: { reputation?: ReputationView | null; disabled?: boolean; scouting?: boolean }) {
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
