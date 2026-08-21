import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdList from './AdList.vue'
import { anAd } from '../test/fixtures'
import type { AdView } from '../api/types'
import type { RequestStatus } from '../stores/game'

function render(props: {
  ads?: AdView[]
  status: RequestStatus
  advisor?: boolean
  disabled?: boolean
}) {
  return mount(AdList, {
    props: { ads: [], solvingAdId: null, advisor: false, disabled: false, ...props },
  })
}

describe('AdList', () => {
  it('shows a skeleton and announces the wait while the first board loads', () => {
    const list = render({ status: 'pending' })

    expect(list.find('[role="status"]').text()).toBe('Loading the message board.')
    expect(list.find('[aria-hidden="true"]').exists()).toBe(true)
  })

  it('keeps the board on screen while a refetch is in flight', () => {
    const list = render({ status: 'pending', ads: [anAd({ message: 'Rescue the cat' })] })

    expect(list.text()).toContain('Rescue the cat')
    expect(list.find('[aria-hidden="true"]').exists()).toBe(false)
  })

  it('offers a way out when the board could not be loaded', async () => {
    const list = render({ status: 'error' })

    expect(list.find('[role="alert"]').text()).toContain('could not be loaded')
    await list.get('[role="alert"] button').trigger('click')
    expect(list.emitted('refresh')).toHaveLength(1)
  })

  it('says so plainly when the board is empty', () => {
    const list = render({ status: 'ready' })

    expect(list.text()).toContain('No ads on the board right now')
  })

  it('renders one card per ad and reports which one was picked', async () => {
    const list = render({
      status: 'ready',
      ads: [anAd({ adId: 'a1', message: 'Steal the gold' }), anAd({ adId: 'a2' })],
    })

    expect(list.findAll('li')).toHaveLength(2)
    await list.get('[aria-label="Solve: Steal the gold"]').trigger('click')
    expect(list.emitted('solve')).toEqual([['a1']])
  })

  it('locks every control while a turn is being spent', () => {
    const list = render({ status: 'ready', ads: [anAd()], disabled: true })

    expect(list.findAll('button').every((button) => button.attributes('disabled') !== undefined))
      .toBe(true)
  })

  it('offers the advisor as an opt-in and says what the current order means', async () => {
    const list = render({ status: 'ready', ads: [anAd()] })

    const toggle = list.get('input[type="checkbox"]')
    expect(toggle.attributes('checked')).toBeUndefined()
    expect(list.text()).toContain('as the board posted them')

    await toggle.setValue(true)
    expect(list.emitted('toggle-advisor')).toHaveLength(1)
  })

  it('says the ranking is the advisor’s once it is on', () => {
    const list = render({ status: 'ready', ads: [anAd()], advisor: true })

    expect(list.text()).toContain('ranked by what the advisor thinks')
  })
})
