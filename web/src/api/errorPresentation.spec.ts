import { describe, expect, it } from 'vitest'
import { endsTheSession, present } from './errorPresentation'
import type { ErrorCode } from './types'

const ALL_CODES: ErrorCode[] = [
  'VALIDATION_FAILED',
  'SESSION_EXPIRED',
  'GAME_OVER',
  'AD_NOT_AVAILABLE',
  'ITEM_NOT_AVAILABLE',
  'INSUFFICIENT_GOLD',
  'GAME_NOT_FOUND',
  'INVALID_ACTION',
  'UPSTREAM_RATE_LIMITED',
  'UPSTREAM_UNAVAILABLE',
  'UPSTREAM_PROTOCOL',
  'UPSTREAM_ERROR',
  'INTERNAL_ERROR',
  'NETWORK_ERROR',
]

describe('presenting a failure', () => {
  it('has an answer for every code the client can produce', () => {
    for (const code of ALL_CODES) {
      expect(present(code).title, code).toBeTruthy()
    }
  })

  it('treats a lost session and a finished game as the end of the run, not a fault', () => {
    expect(present('SESSION_EXPIRED').severity).toBe('terminal')
    expect(present('GAME_NOT_FOUND').severity).toBe('terminal')
    expect(present('GAME_OVER').severity).toBe('terminal')
  })

  it('does not raise an alarm about a refusal the server saw coming', () => {
    expect(present('INSUFFICIENT_GOLD').severity).toBe('note')
    expect(present('AD_NOT_AVAILABLE').severity).toBe('note')
    expect(present('ITEM_NOT_AVAILABLE').severity).toBe('note')
    expect(present('INVALID_ACTION').severity).toBe('note')
  })

  it('raises an alarm about something actually breaking', () => {
    expect(present('NETWORK_ERROR').severity).toBe('fault')
    expect(present('UPSTREAM_UNAVAILABLE').severity).toBe('fault')
    expect(present('INTERNAL_ERROR').severity).toBe('fault')
  })

  it('never offers a way forward once the run is over', () => {
    for (const code of ALL_CODES) {
      if (present(code).severity === 'terminal') {
        expect(present(code).offerRefresh, code).toBe(false)
      }
    }
  })

  it('does not offer to refresh out of a rate limit, which only waiting fixes', () => {
    expect(present('UPSTREAM_RATE_LIMITED').offerRefresh).toBe(false)
  })

  it('names the two codes that mean the session itself is gone', () => {
    expect(ALL_CODES.filter(endsTheSession)).toEqual(['SESSION_EXPIRED', 'GAME_NOT_FOUND'])
  })
})
