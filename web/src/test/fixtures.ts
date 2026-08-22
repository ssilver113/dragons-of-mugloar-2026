import type {
  AdOptionView,
  AdView,
  AutoPlayStepView,
  DecisionView,
  GameView,
  ItemOptionView,
  ShopItemView,
} from '../api/types'

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

export function anItem(overrides: Partial<ShopItemView> = {}): ShopItemView {
  return {
    id: 'cs',
    name: 'Claw Sharpening',
    cost: 100,
    livesGained: 0,
    levelsGained: 1,
    ...overrides,
  }
}

export function problem(status: number, code: string, detail: string): Response {
  return Response.json(
    { title: 'Problem', status, detail, code },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

export function anAdOption(overrides: Partial<AdOptionView> = {}): AdOptionView {
  return {
    adId: 'LTyNBlYB',
    message: 'Help Robin Webster to steal a shipment of gold',
    reward: 15,
    expiresIn: 7,
    probability: 'Piece of cake',
    probabilityTier: 'SAFE',
    successProbability: 0.86,
    score: 8.9,
    verdict: 'CHOSEN',
    ...overrides,
  }
}

export function anItemOption(overrides: Partial<ItemOptionView> = {}): ItemOptionView {
  return {
    itemId: 'cs',
    name: 'Claw Sharpening',
    cost: 100,
    livesGained: 0,
    levelsGained: 1,
    verdict: 'UNAFFORDABLE',
    ...overrides,
  }
}

export function aDecision(overrides: Partial<DecisionView> = {}): DecisionView {
  return {
    move: 'SOLVE_AD',
    targetId: 'LTyNBlYB',
    reason: 'BEST_RISK_ADJUSTED_AD',
    ads: [anAdOption()],
    items: [anItemOption()],
    ...overrides,
  }
}

export function aStep(overrides: Partial<AutoPlayStepView> = {}): AutoPlayStepView {
  return {
    game: aGame({ turn: 1, gold: 15, score: 15 }),
    decision: aDecision(),
    succeeded: true,
    message: 'You successfully solved the mission!',
    reputation: null,
    ...overrides,
  }
}

/** The move the stall guard counts: a turn spent on nothing, which can repeat forever. */
export function aPass(game: Partial<GameView> = {}): AutoPlayStepView {
  return aStep({
    game: aGame({ turn: 1, ...game }),
    decision: aDecision({
      move: 'INVESTIGATE_REPUTATION',
      targetId: null,
      reason: 'PASSING_NOTHING_WORTH_A_TURN',
      ads: [anAdOption({ verdict: 'NOT_WORTH_A_LIFE' })],
    }),
    message: null,
  })
}
