import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GameStats from './GameStats.vue'
import { aGame } from '../test/fixtures'

const strip = (announce?: boolean) =>
  mount(GameStats, {
    props: { game: aGame({ score: 137, gold: 42, lives: 2, level: 3, turn: 9 }), announce },
  })

const withLives = (lives: number) => mount(GameStats, { props: { game: aGame({ lives }) } })

/** The Lives tile, found by its label rather than by its position in the grid. */
const livesTile = (wrapper: ReturnType<typeof withLives>) =>
  wrapper.findAll('dl > div').find((tile) => tile.text().includes('Lives'))!

describe('GameStats', () => {
  it('shows the five figures the brief asks to be visible', () => {
    const text = strip().text()

    expect(text).toContain('137')
    expect(text).toContain('42')
    expect(text).toContain('Lives')
    expect(text).toContain('Level')
    expect(text).toContain('Turn')
  })

  /** Without `aria-atomic` a polite region reads each figure that moved, which is most of them. */
  it('announces the strip as one reading rather than five', () => {
    const dl = strip().get('dl')

    expect(dl.attributes('aria-live')).toBe('polite')
    expect(dl.attributes('aria-atomic')).toBe('true')
  })

  it('goes quiet while the solver holds the game, which is a turn every few hundred ms', () => {
    expect(strip(false).get('dl').attributes('aria-live')).toBe('off')
  })

  it('draws lives as hearts while a row of them still reads at a glance', () => {
    // One heart per life and nothing else — the mark lives with the figure, so the hearts *are*
    // the tile's mark. The count is still written out: the drawing is what is seen, the text is
    // what the live region says.
    const tile = livesTile(withLives(3))

    expect(tile.findAll('img')).toHaveLength(3)
    expect(tile.get('.sr-only').text()).toBe('3')
  })

  it('goes back to a figure once there are more hearts than a tile can hold', () => {
    // The slot is not provided, so the tile falls back to one mark and a figure — which is to
    // say it reads exactly like Score, Gold, Level and Turn.
    const tile = livesTile(withLives(9))

    expect(tile.findAll('img')).toHaveLength(1)
    expect(tile.text()).toContain('9')
  })

  /** A tile that drew nothing would read as one that failed to load, on the turn it matters most. */
  it('still says zero rather than drawing an empty row', () => {
    const tile = livesTile(withLives(0))

    expect(tile.findAll('img')).toHaveLength(1)
    expect(tile.text()).toContain('0')
  })
})
