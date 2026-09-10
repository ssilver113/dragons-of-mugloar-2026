import { describe, expect, it } from 'vitest'
import {
  backdropArt,
  backdropSrcset,
  crestArt,
  dragonArt,
  itemArt,
  wordmarkArt,
  wordmarkSrcset,
} from './artwork'
import ch from './art/items/ch.svg?url'
import cs from './art/items/cs.svg?url'
import gas from './art/items/gas.svg?url'
import hpot from './art/items/hpot.svg?url'
import iron from './art/items/iron.svg?url'
import mtrix from './art/items/mtrix.svg?url'
import rf from './art/items/rf.svg?url'
import tricks from './art/items/tricks.svg?url'
import unknown from './art/items/unknown.svg?url'
import wax from './art/items/wax.svg?url'
import wingpot from './art/items/wingpot.svg?url'
import wingpotmax from './art/items/wingpotmax.svg?url'
import defeated from './art/dragon/defeated.webp?url'
import idle from './art/dragon/idle.webp?url'
import victorious from './art/dragon/victorious.webp?url'
import backdrop from './art/scene/backdrop@1920.webp?url'
import backdropNarrow from './art/scene/backdrop@960.webp?url'
import wordmark from './art/title/wordmark@1580.webp?url'
import wordmarkNarrow from './art/title/wordmark@800.webp?url'

/**
 * Every id recon measured. The shop offers a subset of these and never anything else, so this
 * list is what "the whole set is drawn" means. Compared against the imported file rather than
 * against a path: small SVGs are inlined into the bundle, and a url that no longer carries a
 * filename would make a substring check pass on nothing.
 */
const ITEMS: [string, string][] = [
  ['hpot', hpot],
  ['cs', cs],
  ['gas', gas],
  ['wax', wax],
  ['tricks', tricks],
  ['wingpot', wingpot],
  ['ch', ch],
  ['rf', rf],
  ['iron', iron],
  ['mtrix', mtrix],
  ['wingpotmax', wingpotmax],
]

describe('the artwork registry', () => {
  it.each(ITEMS)('draws %s as itself', (itemId, expected) => {
    expect(itemArt(itemId, 0, 1)).toBe(expected)
  })

  it('gives every item its own drawing, so no two read as the same purchase', () => {
    const drawings = new Set(ITEMS.map(([itemId]) => itemArt(itemId, 0, 1)))

    expect(drawings.size).toBe(ITEMS.length)
  })

  /**
   * The names are flavour, so an id we have never seen is described by what it does — the same
   * thing the shop row says in words beside it — rather than by guessing from its name.
   */
  it('falls back on what an unknown item does', () => {
    expect(itemArt('mystery', 1, 0)).toBe(hpot)
    expect(itemArt('mystery', 0, 1)).toBe(wax)
    expect(itemArt('mystery', 0, 2)).toBe(iron)
  })

  it('admits it knows nothing about an item with no measured effect', () => {
    expect(itemArt('mystery', 0, 0)).toBe(unknown)
  })

  it('has the dragon in all three moods', () => {
    expect(dragonArt('idle')).toBe(idle)
    expect(dragonArt('victorious')).toBe(victorious)
    expect(dragonArt('defeated')).toBe(defeated)
  })

  it('has a crest per faction, and no two alike', () => {
    const crests = new Set(['people', 'state', 'underworld'].map((f) => crestArt(f as 'people')))

    expect(crests.size).toBe(3)
  })

  it('has a scene to sit behind everything', () => {
    expect(backdropArt).toBe(backdrop)
  })

  it('has the wordmark the heading is drawn with', () => {
    expect(wordmarkArt).toBe(wordmark)
  })

  /**
   * The widths are declared by filename, so this asserts the parse as much as the set: a stem
   * drawn at several widths is still one stem, and the widest of them is what `src` resolves to.
   * A stem drawn once has no set at all — a single-format drop-in replacement must not leave an
   * empty `srcset` behind it.
   */
  it('offers every width it was drawn at, widest first', () => {
    expect(wordmarkSrcset).toBe(`${wordmark} 1580w, ${wordmarkNarrow} 800w`)
    expect(backdropSrcset).toBe(`${backdrop} 1920w, ${backdropNarrow} 960w`)
  })
})
