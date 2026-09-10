import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ShopItemRow from './ShopItemRow.vue'
import { anItem } from '../test/fixtures'
import type { ShopItemView } from '../api/types'

function render(item: Partial<ShopItemView> = {}, props: Record<string, unknown> = {}) {
  return mount(ShopItemRow, {
    props: { item: anItem(item), gold: 500, buying: false, disabled: false, ...props },
  })
}

const stud = (row: ReturnType<typeof render>) => row.get('button')

describe('ShopItemRow', () => {
  it('puts the price beside the item while it can be pressed', () => {
    const row = render({ cost: 100, levelsGained: 1 })

    expect(row.text()).toContain('100g · +1 level')
    expect(stud(row).text()).toBe('Buy')
  })

  /**
   * The cost moves rather than repeating. On a row that cannot be pressed the word "Buy" has
   * nothing left to offer, so the stud carries the price instead — and the line stops muting
   * itself, because the dim is already on the whole row.
   */
  it('moves the price onto the stud when there is not enough gold', () => {
    const row = render({ cost: 300 }, { gold: 120 })

    expect(stud(row).text()).toBe('300g')
    expect(row.text()).not.toContain('300g · ')
    expect(row.get('li').classes()).toContain('is-dimmed')
  })

  /** A dimmed row cannot convey how far short it is; the label is where that survives. */
  it('spells the shortfall out for a reader who cannot see the dim', () => {
    const row = render({ cost: 300, name: 'Claw Sharpening' }, { gold: 120 })

    expect(stud(row).attributes('aria-label')).toBe(
      'Claw Sharpening costs 300 gold, 180 more than you have',
    )
  })

  it('names the price in the label of a row that can be bought', () => {
    expect(stud(render({ cost: 100, name: 'Claw Sharpening' })).attributes('aria-label')).toBe(
      'Buy Claw Sharpening for 100 gold',
    )
  })

  it('refuses the sale it cannot afford, and any sale at all while a turn is in flight', () => {
    expect(stud(render({ cost: 300 }, { gold: 120 })).attributes('disabled')).toBeDefined()
    expect(stud(render({}, { disabled: true })).attributes('disabled')).toBeDefined()
    expect(stud(render()).attributes('disabled')).toBeUndefined()
  })

  it('emits the id of the item, since that is what the shop is asked for', async () => {
    const row = render({ id: 'hpot' })
    await stud(row).trigger('click')

    expect(row.emitted('buy')).toEqual([['hpot']])
  })

  it('says what it is doing while the purchase is in flight', () => {
    expect(stud(render({}, { buying: true })).text()).toContain('Buying…')
  })

  /**
   * The effect is read off the numbers the server sends rather than off the name, so an item
   * whose effect was never measured admits it instead of promising something.
   */
  it('describes an item by what it does, and admits when it does not know', () => {
    expect(render({ livesGained: 2, levelsGained: 0 }).text()).toContain('+2 lives')
    expect(render({ livesGained: 1, levelsGained: 1 }).text()).toContain('+1 life and +1 level')
    expect(render({ livesGained: 0, levelsGained: 0 }).text()).toContain('effect unknown')
  })
})
