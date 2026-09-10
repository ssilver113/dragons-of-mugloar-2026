import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdToolbar from './AdToolbar.vue'
import type { FilterId } from '../advisor/ranking'

function render(props: Record<string, unknown> = {}) {
  return mount(AdToolbar, {
    props: {
      sort: 'value',
      posture: 'balanced',
      filters: [] as FilterId[],
      shown: 10,
      total: 10,
      lifeCost: 300,
      ...props,
    },
  })
}

const checkbox = (bar: ReturnType<typeof render>, label: string) =>
  bar
    .findAll('label')
    .find((option) => option.text() === label)!
    .get('input')

describe('AdToolbar', () => {
  it('offers every sort the ranking defines, with the current one selected', () => {
    const select = render({ sort: 'expiry' }).get('select')

    expect(select.findAll('option').map((option) => option.text())).toEqual([
      'Worth the risk',
      'Reward',
      'Chance',
      'Expiring first',
    ])
    expect((select.element as HTMLSelectElement).value).toBe('expiry')
  })

  it('asks for a different sort rather than sorting anything itself', async () => {
    const bar = render()
    await bar.get('select').setValue('reward')

    expect(bar.emitted('update:sort')).toEqual([['reward']])
  })

  /**
   * Three named stances rather than a number, in a real radio group so the choice is announced
   * as a choice. The stance in force is a checked radio, not only a filled label.
   */
  it('marks the posture in force and asks for the one that was pressed', async () => {
    const bar = render({ posture: 'cautious' })
    const radios = bar.findAll('input[type="radio"]')

    expect(radios.map((radio) => (radio.element as HTMLInputElement).checked)).toEqual([
      true,
      false,
      false,
    ])

    await radios[2]!.trigger('change')
    expect(bar.emitted('update:posture')).toEqual([['bold']])
  })

  it('prices a life in whole gold, because a fraction of one means nothing', () => {
    expect(render({ lifeCost: 287.6 }).text()).toContain('prices a life at 288g')
  })

  it('reports what the filters are hiding, and offers to undo them', async () => {
    const bar = render({ filters: ['likely'], shown: 4, total: 10 })

    expect(bar.get('[role="status"]').text()).toContain('Showing 4 of 10 jobs, 6 filtered out')
    await bar.get('[role="status"] button').trigger('click')
    expect(bar.emitted('clear-filters')).toHaveLength(1)
  })

  it('says nothing about filtering when nothing is filtered', () => {
    expect(render().find('[role="status"]').exists()).toBe(false)
  })

  it('reflects the filters in force and asks for the one that was toggled', async () => {
    const bar = render({ filters: ['expiring'] })

    expect((checkbox(bar, 'Expiring soon').element as HTMLInputElement).checked).toBe(true)
    expect((checkbox(bar, 'Good odds').element as HTMLInputElement).checked).toBe(false)

    await checkbox(bar, 'Good odds').trigger('change')
    expect(bar.emitted('toggle-filter')).toEqual([['likely']])
  })
})
