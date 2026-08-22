import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import GameStats from './GameStats.vue'
import { aGame } from '../test/fixtures'

const strip = (announce?: boolean) =>
  mount(GameStats, {
    props: { game: aGame({ score: 137, gold: 42, lives: 2, level: 3, turn: 9 }), announce },
  })

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
})
