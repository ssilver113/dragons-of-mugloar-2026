import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdList from './AdList.vue'
import { anAd } from '../test/fixtures'
import type { BoardEntry } from '../advisor/boardView'
import type { AdView } from '../api/types'
import type { RequestStatus } from '../stores/game'

/**
 * The list is handed a ranked board now; who ranked it and how is `useBoardView`'s business and is
 * tested there. What is left here is the states the section can be in and the events it raises.
 */
const plain = (ads: AdView[]): BoardEntry[] => ads.map((ad) => ({ ad, read: null }))

function render(props: {
  entries?: BoardEntry[]
  total?: number
  status: RequestStatus
  advisor?: boolean
  disabled?: boolean
}) {
  return mount(AdList, {
    props: {
      entries: [],
      total: props.entries?.length ?? 0,
      solvingAdId: null,
      advisor: false,
      disabled: false,
      ...props,
    },
  })
}

const messages = (list: ReturnType<typeof render>) =>
  list.findAll('[aria-label^="Solve:"]').map((button) => button.attributes('aria-label'))

describe('AdList', () => {
  it('shows a skeleton and announces the wait while the first board loads', () => {
    const list = render({ status: 'pending' })

    expect(list.find('[role="status"]').text()).toBe('Loading the message board.')
    expect(list.find('ul[aria-hidden="true"]').exists()).toBe(true)
  })

  it('keeps the board on screen while a refetch is in flight', () => {
    const list = render({
      status: 'pending',
      entries: plain([anAd({ message: 'Rescue the cat' })]),
    })

    expect(list.text()).toContain('Rescue the cat')
    expect(list.find('ul[aria-hidden="true"]').exists()).toBe(false)
  })

  it('offers a way back when the board could not be loaded at all', async () => {
    const list = render({ status: 'error' })

    expect(list.find('[role="alert"]').text()).toContain('could not be loaded')
    await list.get('[role="alert"] button').trigger('click')
    expect(list.emitted('refresh')).toHaveLength(1)
  })

  it('says an empty board is empty rather than still loading', () => {
    expect(render({ status: 'ready' }).text()).toContain('No ads on the board right now')
  })

  it('tells the player the board is filtered rather than empty', () => {
    const list = render({ status: 'ready', entries: [], total: 3, advisor: true })

    expect(list.text()).toContain('Every job on the board is filtered out')
    expect(list.text()).not.toContain('No ads on the board')
  })

  it('draws the board in the order it is handed, and asks for a job by id', async () => {
    const list = render({
      status: 'ready',
      entries: plain([
        anAd({ adId: 'a1', message: 'Steal the gold' }),
        anAd({ adId: 'a2', message: 'Rescue the cat' }),
      ]),
    })

    expect(messages(list)).toEqual(['Solve: Steal the gold', 'Solve: Rescue the cat'])

    await list.get('[aria-label="Solve: Steal the gold"]').trigger('click')
    expect(list.emitted('solve')).toEqual([['a1']])
  })

  it('locks every control that spends a turn', () => {
    const list = render({ status: 'ready', entries: plain([anAd()]), disabled: true })

    const buttons = list.findAll('button')
    expect(buttons.length).toBeGreaterThan(0)
    expect(buttons.every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })

  it('says whose order the board is in', () => {
    expect(render({ status: 'ready', entries: plain([anAd()]) }).text()).toContain(
      'as the board posted them',
    )
    expect(render({ status: 'ready', entries: plain([anAd()]), advisor: true }).text()).toContain(
      'ranked by what the advisor thinks',
    )
  })
})
