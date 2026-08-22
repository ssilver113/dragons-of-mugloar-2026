import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import AdCard from './AdCard.vue'
import { anAd } from '../test/fixtures'
import type { AdView } from '../api/types'
import type { AdRead } from '../advisor/ranking'

function aRead(overrides: Partial<AdRead> = {}): AdRead {
  return { score: 12, band: 'fair', trap: false, ...overrides }
}

function render(
  ad: Partial<AdView> = {},
  props: { read?: AdRead | null; solving?: boolean; disabled?: boolean } = {},
) {
  return mount(AdCard, {
    props: { ad: anAd(ad), read: null, solving: false, disabled: false, ...props },
  })
}

describe('AdCard', () => {
  it('shows what the game posted, and none of our analysis, until it is asked for', () => {
    const card = render({ reward: 42, probability: 'Quite likely', expiresIn: 5 })

    expect(card.text()).toContain('42g')
    expect(card.text()).toContain('Quite likely')
    expect(card.text()).toContain('Expires in 5 turns')
    expect(card.text()).not.toContain("Advisor's read")
    expect(card.text()).not.toMatch(/%/)
  })

  it('attributes the estimate to the advisor and hedges it, once switched on', () => {
    const card = render(
      { reward: 42, successProbability: 0.74, expectedValue: 31.1 },
      { read: aRead() },
    )

    expect(card.text()).toContain("Advisor's read")
    expect(card.text()).toContain('~75%')
    expect(card.text()).toContain('~31g')
  })

  it('names the value band as well as colouring it, and signs the number', () => {
    expect(render({}, { read: aRead({ score: 41, band: 'strong' }) }).text()).toContain('+41g')
    expect(render({}, { read: aRead({ score: 41, band: 'strong' }) }).text()).toContain('Strong')

    const losing = render({}, { read: aRead({ score: -18, band: 'poor' }) })
    expect(losing.text()).toContain('-18g')
    expect(losing.text()).toContain('Not worth a life')
  })

  it('calls out a trap, which is the one ad a human is likelier to take than the bot', () => {
    const trap = render({ reward: 400 }, { read: aRead({ score: -120, band: 'poor', trap: true }) })

    expect(trap.text()).toContain('Trap')
    expect(trap.text()).toContain('the job that ends a run')

    const plain = render({ reward: 400 }, { read: aRead() })
    expect(plain.text()).not.toContain('Trap')
  })

  it('keeps the model’s judgement out of the board’s own facts', () => {
    const flags: AdView['flags'] = ['OUT_OF_LEAGUE', 'NEVER_ATTEMPT', 'EXPIRING_NEXT_TURN']

    const plain = render({ flags })
    expect(plain.text()).toContain('Last turn')
    expect(plain.text()).not.toContain('richer than your level')
    expect(plain.text()).not.toContain('Never worth a turn')

    const advised = render({ flags }, { read: aRead() })
    expect(advised.text()).toContain('richer than your level')
    expect(advised.text()).toContain('Never worth a turn')
  })

  it('marks an ad that arrived encrypted, since the text on screen is our decoding', () => {
    expect(render({ encrypted: true }).text()).toContain('Decoded')
    expect(render({ encrypted: false }).text()).not.toContain('Decoded')
  })

  it('still offers a hopeless ad, because which risks to take is the player’s call', async () => {
    const card = render({ adId: 'doomed', flags: ['NEVER_ATTEMPT'] }, { read: aRead() })

    await card.get('button').trigger('click')

    expect(card.emitted('solve')).toEqual([['doomed']])
  })

  it('refuses only the ad it could not decode, whose id the game service would reject', () => {
    const card = render({ flags: ['UNREADABLE'] })

    expect(card.text()).toContain('Unreadable')
    expect(card.get('button').attributes('disabled')).toBeDefined()
  })

  it('reports the attempt in progress on the ad being attempted', () => {
    expect(render({}, { solving: true, disabled: true }).text()).toContain('Solving')
  })

  it('reports it in the accessible name too, which is the only one a screen reader hears', () => {
    const idle = render({ message: 'Steal the gold' })
    expect(idle.get('button').attributes('aria-label')).toBe('Solve: Steal the gold')

    const busy = render({ message: 'Steal the gold' }, { solving: true, disabled: true })
    expect(busy.get('button').attributes('aria-label')).toBe('Solving: Steal the gold')
  })
})
