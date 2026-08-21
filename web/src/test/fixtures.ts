import type { AdView, GameView } from '../api/types'

/** Shapes copied from live responses; overrides keep each test's intent to the fields it cares about. */
export function aGame(overrides: Partial<GameView> = {}): GameView {
  return {
    gameId: 'kZUyeMSK',
    lives: 3,
    gold: 0,
    level: 0,
    score: 0,
    turn: 0,
    finished: false,
    ...overrides,
  }
}

export function anAd(overrides: Partial<AdView> = {}): AdView {
  return {
    adId: 'LTyNBlYB',
    message: 'Help Robin Webster to steal a shipment of gold',
    reward: 15,
    expiresIn: 7,
    encrypted: false,
    probability: 'Piece of cake',
    probabilityTier: 'SAFE',
    successProbability: 0.86,
    expectedValue: 12.9,
    flags: [],
    ...overrides,
  }
}

export function problem(status: number, code: string, detail: string): Response {
  return Response.json(
    { title: 'Problem', status, detail, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}
