import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import { useBoardView } from './boardView'
import { anAd } from '../test/fixtures'
import type { AdView } from '../api/types'

/** Three ads that rank differently under every sort the advisor offers. */
const RICH = anAd({
  adId: 'rich',
  message: 'Rich',
  reward: 400,
  successProbability: 0.15,
  expiresIn: 2,
})
const SAFE = anAd({
  adId: 'safe',
  message: 'Safe',
  reward: 50,
  successProbability: 0.86,
  expiresIn: 5,
})
const MID = anAd({ adId: 'mid', message: 'Mid', reward: 30, successProbability: 0.8, expiresIn: 9 })

function view(options: { ads?: AdView[]; lives?: number; advisor?: boolean } = {}) {
  const ads = ref<AdView[]>(options.ads ?? [RICH, SAFE, MID])
  const lives = ref(options.lives ?? 3)
  const advisor = ref(options.advisor ?? true)
  const holding = ref(false)
  return { ...useBoardView({ ads, lives, advisor, holding }), ads, lives, advisor, holding }
}

const order = (board: ReturnType<typeof view>) =>
  board.entries.value.map((entry) => entry.ad.message)

describe('useBoardView', () => {
  it('leaves the board as the game posted it while the advisor is off', () => {
    expect(order(view({ advisor: false }))).toEqual(['Rich', 'Safe', 'Mid'])
  })

  it('reads nothing at all while the advisor is off, so a card has nothing to draw', () => {
    expect(view({ advisor: false }).entries.value.every((entry) => entry.read === null)).toBe(true)
  })

  it('ranks by what an ad is worth once the risk is priced in, by default', () => {
    expect(order(view())).toEqual(['Safe', 'Mid', 'Rich'])
  })

  it('re-ranks on the sort the player picks', () => {
    const board = view()

    board.sort.value = 'reward'

    expect(order(board)).toEqual(['Rich', 'Safe', 'Mid'])
  })

  it('re-orders as turns age the board, so the ad about to vanish rises on its own', async () => {
    const board = view()
    board.sort.value = 'expiry'
    expect(order(board)).toEqual(['Rich', 'Safe', 'Mid'])

    // A turn has passed: the board comes back a turn older, and MID is now the urgent one.
    board.ads.value = [
      { ...RICH, expiresIn: 8 },
      { ...SAFE, expiresIn: 4 },
      { ...MID, expiresIn: 1 },
    ]
    await nextTick()

    expect(order(board)).toEqual(['Mid', 'Safe', 'Rich'])
  })

  it('re-prices the board under a different risk posture without asking the server anything', () => {
    const board = view({ ads: [SAFE, MID], lives: 1 })
    // One job here does not cover its own risk while a life costs 300g.
    expect(board.entries.value.filter((entry) => entry.read?.band === 'poor')).toHaveLength(1)

    board.posture.value = 'bold'

    expect(board.entries.value.filter((entry) => entry.read?.band === 'poor')).toHaveLength(0)
  })

  it('prices a life by scarcity, so the last one is the dearest', () => {
    const board = view({ lives: 1 })
    expect(board.lifeCost.value).toBe(300)

    board.posture.value = 'bold'
    expect(board.lifeCost.value).toBe(100)
  })

  it('filters the board down, counts what it hid, and offers the way back', () => {
    const board = view()

    board.toggleFilter('expiring')

    expect(order(board)).toEqual(['Rich'])
    expect(board.shown.value).toBe(1)
    expect(board.total.value).toBe(3)

    board.clearFilters()

    expect(board.shown.value).toBe(3)
  })

  it('stacks filters as AND, so a second one narrows rather than widens', () => {
    const board = view()

    board.toggleFilter('expiring')
    board.toggleFilter('worthwhile')

    expect(order(board)).toEqual([])
  })

  it('holds the board still for the length of a turn, then changes it once', async () => {
    const board = view({ ads: [anAd({ adId: 'a1', message: 'Steal the gold' })] })

    // A turn starts: the optimistic board and the figures the response brings both land while this
    // is true, and neither may move a card.
    board.holding.value = true
    board.ads.value = [anAd({ adId: 'a2', message: 'Rescue the cat' })]
    board.lives.value = 2
    await nextTick()
    expect(order(board)).toEqual(['Steal the gold'])

    // The turn's own board arrives, and that is the one change the player sees.
    board.holding.value = false
    await nextTick()
    expect(order(board)).toEqual(['Rescue the cat'])
  })

  it('still answers the player while a turn is in flight, since the controls are theirs', async () => {
    const board = view()
    board.holding.value = true
    await nextTick()

    board.sort.value = 'reward'

    expect(order(board)).toEqual(['Rich', 'Safe', 'Mid'])
  })
})
