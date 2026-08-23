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
  lives?: number
  disabled?: boolean
  holding?: boolean
}) {
  return mount(AdList, {
    props: {
      ads: [],
      solvingAdId: null,
      advisor: false,
      lives: 3,
      disabled: false,
      holding: false,
      ...props,
    },
  })
}

const messages = (list: ReturnType<typeof render>) =>
  list.findAll('[aria-label^="Solve:"]').map((button) => button.attributes('aria-label'))

/** Three ads that rank differently under every sort the toolbar offers. */
const RICH = anAd({ adId: 'rich', message: 'Rich', reward: 400, successProbability: 0.15, expiresIn: 2 })
const SAFE = anAd({ adId: 'safe', message: 'Safe', reward: 50, successProbability: 0.86, expiresIn: 5 })
const MID = anAd({ adId: 'mid', message: 'Mid', reward: 30, successProbability: 0.8, expiresIn: 9 })

describe('AdList', () => {
  it('shows a skeleton and announces the wait while the first board loads', () => {
    const list = render({ status: 'pending' })

    expect(list.find('[role="status"]').text()).toBe('Loading the message board.')
    expect(list.find('ul[aria-hidden="true"]').exists()).toBe(true)
  })

  it('keeps the board on screen while a refetch is in flight', () => {
    const list = render({ status: 'pending', ads: [anAd({ message: 'Rescue the cat' })] })

    expect(list.text()).toContain('Rescue the cat')
    expect(list.find('ul[aria-hidden="true"]').exists()).toBe(false)
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

  it('locks every control that spends a turn, and leaves the advisor alone', () => {
    const list = render({ status: 'ready', ads: [anAd()], disabled: true })

    const spending = list.findAll('button').filter((b) => b.attributes('role') !== 'switch')
    expect(spending.length).toBeGreaterThan(0)
    expect(spending.every((button) => button.attributes('disabled') !== undefined)).toBe(true)

    // The advisor costs nothing upstream. Turning it on to reconsider the board is exactly what a
    // player wants to do while the dragon is out, so it is the one control that stays live.
    expect(list.get('[role="switch"]').attributes('disabled')).toBeUndefined()
  })

  it('holds the board still for the length of a turn, then changes it once', async () => {
    const list = render({
      status: 'ready',
      ads: [anAd({ adId: 'a1', message: 'Steal the gold' })],
      advisor: true,
      lives: 3,
    })

    // A turn starts: the optimistic board and the figures the response brings both land while
    // this is true, and neither may move a card.
    await list.setProps({
      holding: true,
      ads: [anAd({ adId: 'a2', message: 'Rescue the cat' })],
      lives: 2,
    })
    expect(messages(list)).toEqual(['Solve: Steal the gold'])

    // The turn's own board arrives, and that is the one change the player sees.
    await list.setProps({ holding: false })
    expect(messages(list)).toEqual(['Solve: Rescue the cat'])
  })

  it('offers the advisor as an opt-in and says what the current order means', async () => {
    const list = render({ status: 'ready', ads: [anAd()] })

    const toggle = list.get('[role="switch"]')
    expect(toggle.attributes('aria-checked')).toBe('false')
    expect(list.text()).toContain('as the board posted them')

    await toggle.trigger('click')
    expect(list.emitted('toggle-advisor')).toHaveLength(1)

    expect(
      render({ status: 'ready', ads: [anAd()], advisor: true })
        .get('[role="switch"]')
        .attributes('aria-checked'),
    ).toBe('true')
  })

  it('says the ranking is the advisor’s once it is on', () => {
    const list = render({ status: 'ready', ads: [anAd()], advisor: true })

    expect(list.text()).toContain('ranked by what the advisor thinks')
  })

  it('keeps the toolbar behind the advisor toggle, like the rest of the advice', () => {
    expect(render({ status: 'ready', ads: [anAd()] }).find('#ad-sort').exists()).toBe(false)
    expect(render({ status: 'ready', ads: [anAd()], advisor: true }).find('#ad-sort').exists())
      .toBe(true)
  })
})

describe('AdList ranking', () => {
  const board = { status: 'ready' as const, ads: [RICH, SAFE, MID], advisor: true }

  it('leaves the board as the game posted it while the advisor is off', () => {
    expect(messages(render({ ...board, advisor: false }))).toEqual([
      'Solve: Rich',
      'Solve: Safe',
      'Solve: Mid',
    ])
  })

  it('ranks by what an ad is worth once the risk is priced in, by default', () => {
    expect(messages(render(board))).toEqual(['Solve: Safe', 'Solve: Mid', 'Solve: Rich'])
  })

  it('re-ranks on the sort the player picks', async () => {
    const list = render(board)

    await list.get('#ad-sort').setValue('reward')

    expect(messages(list)).toEqual(['Solve: Rich', 'Solve: Safe', 'Solve: Mid'])
  })

  it('re-orders as turns age the board, so the ad about to vanish rises on its own', async () => {
    const list = render(board)
    await list.get('#ad-sort').setValue('expiry')
    expect(messages(list)).toEqual(['Solve: Rich', 'Solve: Safe', 'Solve: Mid'])

    // A turn has passed: the board comes back a turn older, and MID is now the urgent one.
    await list.setProps({
      ads: [
        { ...RICH, expiresIn: 8 },
        { ...SAFE, expiresIn: 4 },
        { ...MID, expiresIn: 1 },
      ],
    })

    expect(messages(list)).toEqual(['Solve: Mid', 'Solve: Safe', 'Solve: Rich'])
  })

  it('re-ranks under a different risk posture without asking the server anything', async () => {
    const list = render({ ...board, ads: [SAFE, MID], lives: 1 })
    // The advisor's red "No": one job here does not cover its own risk while a life costs 300g.
    expect(list.findAll('dd.text-danger')).toHaveLength(1)

    await list.get('input[name="posture"][value="bold"]').setValue()

    expect(list.findAll('dd.text-danger')).toHaveLength(0)
  })

  it('filters the board down and offers the way back', async () => {
    const list = render(board)

    await list.get('input[type="checkbox"][value="expiring"]').setValue(true)

    expect(messages(list)).toEqual(['Solve: Rich'])
    expect(list.text()).toContain('Showing 1 of 3 jobs, 2 filtered out')

    await list.get('[role="status"] button').trigger('click')

    expect(messages(list)).toHaveLength(3)
  })

  it('tells the player the board is filtered rather than empty', async () => {
    const list = render({ ...board, ads: [RICH] })

    await list.get('input[type="checkbox"][value="worthwhile"]').setValue(true)

    expect(list.text()).toContain('Every job on the board is filtered out')
    expect(list.text()).not.toContain('No ads on the board')
  })
})
