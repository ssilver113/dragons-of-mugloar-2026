import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ShopPanel from './ShopPanel.vue'
import { anItem } from '../test/fixtures'
import type { ShopItemView } from '../api/types'
import type { RequestStatus } from '../stores/game'

function render(props: {
  items?: ShopItemView[]
  gold?: number
  status: RequestStatus
  buyingItemId?: string | null
  disabled?: boolean
}) {
  return mount(ShopPanel, {
    props: { items: [], gold: 0, buyingItemId: null, disabled: false, ...props },
  })
}

describe('ShopPanel', () => {
  it('shows a skeleton and announces the wait while the catalogue loads', () => {
    const shop = render({ status: 'pending' })

    expect(shop.find('[role="status"]').text()).toBe('Loading the shop.')
    expect(shop.find('[aria-hidden="true"]').exists()).toBe(true)
  })

  /**
   * The board has always done this and the shop did not: its live region was mounted alongside
   * its own sentence, which is the one arrangement that is not reliably announced. Both panels
   * share `PanelStatus` now, so this is the assertion that keeps them sharing it.
   */
  it('keeps the live region in the tree when there is nothing to announce', () => {
    const shop = render({ status: 'ready', gold: 500, items: [anItem()] })

    expect(shop.find('[role="status"]').exists()).toBe(true)
    expect(shop.find('[role="status"]').text()).toBe('')
  })

  it('offers a way out when the catalogue could not be loaded', async () => {
    const shop = render({ status: 'error' })

    expect(shop.find('[role="alert"]').text()).toContain('could not be loaded')
    await shop.get('[role="alert"] button').trigger('click')
    expect(shop.emitted('refresh')).toHaveLength(1)
  })

  it('describes what each item does from the numbers, not from its name', () => {
    const shop = render({
      status: 'ready',
      gold: 500,
      items: [
        anItem({ id: 'hpot', name: 'Healing potion', cost: 50, livesGained: 1, levelsGained: 0 }),
        anItem({ id: 'wingpotmax', name: 'Potion of Awesome Wings', cost: 300, levelsGained: 2 }),
      ],
    })

    expect(shop.text()).toContain('50g · +1 life')
    expect(shop.text()).toContain('300g · +2 levels')
  })

  it('says so rather than guessing when an effect was never measured', () => {
    const shop = render({
      status: 'ready',
      gold: 500,
      items: [anItem({ cost: 75, livesGained: 0, levelsGained: 0 })],
    })

    expect(shop.text()).toContain('effect unknown')
  })

  it('will not offer to sell what the purse cannot cover, and shows the price instead of Buy', () => {
    const shop = render({
      status: 'ready',
      gold: 120,
      items: [
        anItem({ id: 'cs', cost: 100, levelsGained: 1 }),
        anItem({ id: 'wingpotmax', cost: 300, levelsGained: 2 }),
      ],
    })

    const buttons = shop.findAll('button')
    expect(buttons[0]?.attributes('disabled')).toBeUndefined()
    expect(buttons[0]?.text()).toBe('Buy')
    expect(buttons[1]?.attributes('disabled')).toBeDefined()
    expect(buttons[1]?.text()).toBe('300g')

    // The cost moves to the stud rather than being repeated beside it, but the shortfall itself is
    // still spelled out for anyone who cannot see which rows are dimmed.
    expect(shop.text()).toContain('100g · +1 level')
    expect(shop.text()).not.toContain('300g · +2 levels')
    expect(buttons[1]?.attributes('aria-label')).toContain('180 more than you have')
  })

  it('spends the purse exactly dry rather than treating equality as short', () => {
    const shop = render({ status: 'ready', gold: 100, items: [anItem({ cost: 100 })] })

    expect(shop.get('button').attributes('disabled')).toBeUndefined()
  })

  it('asks to buy the item that was clicked', async () => {
    const shop = render({ status: 'ready', gold: 500, items: [anItem({ id: 'cs' })] })

    await shop.get('button').trigger('click')

    expect(shop.emitted('buy')).toEqual([['cs']])
  })

  it('marks only the item being bought, and locks the rest of the shop with it', () => {
    const shop = render({
      status: 'ready',
      gold: 500,
      buyingItemId: 'cs',
      disabled: true,
      items: [anItem({ id: 'cs' }), anItem({ id: 'hpot', cost: 50 })],
    })

    const buttons = shop.findAll('button')
    expect(buttons[0]?.text()).toBe('Buying…')
    expect(buttons[1]?.text()).toBe('Buy')
    expect(buttons.every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })

  /**
   * Everything above the shopfront sits directly on the painted backdrop, where muted ink measures
   * 2.26:1 and 2.29:1 against the treeline and no scrim reaches 4.5. The collar is what makes them
   * readable there, so losing it is a contrast regression nothing else in the suite would catch.
   * The plaques below are on oak and need none of it, which is what the count is guarding.
   */
  it('collars everything it puts on the painting, and nothing on the shopfront', () => {
    const shop = render({ status: 'ready', gold: 500, items: [anItem()] })

    const collared = shop.findAll('.collar').map((el) => el.text())

    expect(collared).toHaveLength(3)
    expect(collared[0]).toContain('Shop')
    expect(collared[1]).toContain('gold')
    expect(collared[2]).toContain('Buying costs a turn')
  })
})
