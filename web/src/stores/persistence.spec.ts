import { describe, expect, it, vi } from 'vitest'
import { persisted } from './persistence'

interface Kept {
  gameId: string
  turn: number
}

describe('persisted values', () => {
  it('reads back what it wrote', () => {
    const slot = persisted<Kept>('session', 'test.kept')

    slot.write({ gameId: 'kZUyeMSK', turn: 12 })

    expect(slot.read()).toEqual({ gameId: 'kZUyeMSK', turn: 12 })
  })

  it('is empty until something is written, and again once it is cleared', () => {
    const slot = persisted<Kept>('local', 'test.kept')
    expect(slot.read()).toBeNull()

    slot.write({ gameId: 'kZUyeMSK', turn: 1 })
    slot.clear()

    expect(slot.read()).toBeNull()
  })

  it('keeps session and local apart under the same key', () => {
    persisted<Kept>('session', 'test.kept').write({ gameId: 'session', turn: 1 })
    persisted<Kept>('local', 'test.kept').write({ gameId: 'local', turn: 2 })

    expect(persisted<Kept>('session', 'test.kept').read()?.gameId).toBe('session')
    expect(persisted<Kept>('local', 'test.kept').read()?.gameId).toBe('local')
  })

  // The two ways a stored value stops being one this build can use. Both have to read as absent
  // rather than as data, because the alternative is casting a stranger's shape to ours.
  it('discards a value written by a different version of the app', () => {
    sessionStorage.setItem('test.kept', JSON.stringify({ version: 0, value: { turn: 12 } }))

    expect(persisted<Kept>('session', 'test.kept').read()).toBeNull()
  })

  it('discards a value it cannot parse', () => {
    sessionStorage.setItem('test.kept', 'not json')

    expect(persisted<Kept>('session', 'test.kept').read()).toBeNull()
  })

  it('carries on when the browser refuses the write', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError')
    })
    const slot = persisted<Kept>('local', 'test.kept')

    expect(() => slot.write({ gameId: 'kZUyeMSK', turn: 12 })).not.toThrow()
    expect(setItem).toHaveBeenCalled()
    setItem.mockRestore()
  })
})
