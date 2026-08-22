import { describe, expect, it } from 'vitest'
import {
  filterBoard,
  lifeCost,
  meanReward,
  riskAdjustedScore,
  scoreBoard,
  sortBoard,
} from './ranking'
import { anAd } from '../test/fixtures'
import type { FilterId, ScoredAd } from './ranking'

/** Three ads that disagree with each other on every axis the toolbar sorts by. */
const RICH = anAd({ adId: 'rich', reward: 400, successProbability: 0.15, expiresIn: 2 })
const SAFE = anAd({ adId: 'safe', reward: 50, successProbability: 0.86, expiresIn: 5 })
const MID = anAd({ adId: 'mid', reward: 30, successProbability: 0.8, expiresIn: 9 })
const BOARD = [RICH, SAFE, MID]

const ids = (scored: ScoredAd[]) => scored.map((entry) => entry.ad.adId)
const of = (scored: ScoredAd[], adId: string) => scored.find((entry) => entry.ad.adId === adId)!
const only = (...selected: FilterId[]) => new Set(selected)

describe('lifeCost', () => {
  it('makes the last life the dearest, so the same ad is judged differently as lives run out', () => {
    expect(lifeCost('balanced', 3)).toBeCloseTo(100)
    expect(lifeCost('balanced', 1)).toBeCloseTo(300)
  })

  it('treats a dead dragon as having one life rather than dividing by zero', () => {
    expect(lifeCost('balanced', 0)).toBeCloseTo(300)
  })
})

describe('riskAdjustedScore', () => {
  it('prices the reward against the risk of losing a life', () => {
    // 50 × 0.86 − 100 × 0.14
    expect(riskAdjustedScore(SAFE, 'balanced', 3)).toBeCloseTo(29)
  })

  it('is the same ad seen through a different nerve, and can change sign', () => {
    const ad = anAd({ reward: 15, successProbability: 0.86 })

    expect(riskAdjustedScore(ad, 'cautious', 3)).toBeLessThan(0)
    expect(riskAdjustedScore(ad, 'balanced', 3)).toBeLessThan(0)
    expect(riskAdjustedScore(ad, 'bold', 3)).toBeGreaterThan(0)
  })
})

describe('scoreBoard', () => {
  it('bands against the best the board is offering, not an absolute cutoff', () => {
    const scored = scoreBoard(BOARD, 'balanced', 3)

    expect(of(scored, 'safe').band).toBe('strong')
    expect(of(scored, 'mid').band).toBe('fair')
    expect(of(scored, 'rich').band).toBe('poor')
  })

  it('bands everything poor when nothing on the board covers its own risk', () => {
    const scored = scoreBoard(BOARD, 'cautious', 1)

    expect(scored.every((entry) => entry.band === 'poor')).toBe(true)
  })

  it('flags the big reward the odds do not back', () => {
    const scored = scoreBoard(BOARD, 'balanced', 3)

    expect(of(scored, 'rich').trap).toBe(true)
    expect(of(scored, 'safe').trap).toBe(false)
  })

  it('keeps a trap flagged however boldly the player is browsing', () => {
    // The flag describes the board, so a posture that would take the bet must not erase it.
    expect(of(scoreBoard(BOARD, 'bold', 3), 'rich').trap).toBe(true)
  })

  it('does not flag a rich ad the odds do back', () => {
    const board = [anAd({ adId: 'rich', reward: 400, successProbability: 0.9 }), SAFE, MID]

    expect(of(scoreBoard(board, 'balanced', 3), 'rich').trap).toBe(false)
  })

  it('does not brand an ad that merely fails to earn its turn', () => {
    // A real board at level 0: the third-richest job scored −1g, which is not a job that ends a
    // run. A trap has to be among the board's worst, not just barely under water.
    const board = [
      anAd({ adId: 'best', reward: 60, successProbability: 0.867 }),
      anAd({ adId: 'good', reward: 64, successProbability: 0.72 }),
      anAd({ adId: 'marginal', reward: 35, successProbability: 0.74 }),
      anAd({ adId: 'weak', reward: 19, successProbability: 0.4 }),
      anAd({ adId: 'worst', reward: 8, successProbability: 0.75 }),
    ]

    const scored = scoreBoard(board, 'balanced', 3)
    expect(of(scored, 'marginal').score).toBeLessThan(0)
    expect(scored.some((entry) => entry.trap)).toBe(false)
  })

  it('copes with an empty board', () => {
    expect(scoreBoard([], 'balanced', 3)).toEqual([])
  })
})

describe('sortBoard', () => {
  const scored = scoreBoard(BOARD, 'balanced', 3)

  it('ranks by what each ad is worth once the risk is priced in', () => {
    expect(ids(sortBoard(scored, 'value'))).toEqual(['safe', 'mid', 'rich'])
  })

  it('ranks by the raw reward, which is where the trap comes top', () => {
    expect(ids(sortBoard(scored, 'reward'))).toEqual(['rich', 'safe', 'mid'])
  })

  it('ranks by the chance of pulling it off', () => {
    expect(ids(sortBoard(scored, 'chance'))).toEqual(['safe', 'mid', 'rich'])
  })

  it('ranks the ad about to vanish first', () => {
    expect(ids(sortBoard(scored, 'expiry'))).toEqual(['rich', 'safe', 'mid'])
  })

  it('breaks a tie on the ad that expires soonest, then on id, so the order never shuffles', () => {
    const tied = scoreBoard(
      [
        anAd({ adId: 'b-late', reward: 60, successProbability: 0.5, expiresIn: 8 }),
        anAd({ adId: 'c-soon', reward: 60, successProbability: 0.5, expiresIn: 2 }),
        anAd({ adId: 'a-soon', reward: 60, successProbability: 0.5, expiresIn: 2 }),
      ],
      'balanced',
      3,
    )

    expect(ids(sortBoard(tied, 'reward'))).toEqual(['a-soon', 'c-soon', 'b-late'])
  })

  it('leaves the board it was given alone', () => {
    sortBoard(scored, 'reward')

    expect(ids(scored)).toEqual(['rich', 'safe', 'mid'])
  })
})

describe('filterBoard', () => {
  const scored = scoreBoard(BOARD, 'balanced', 3)
  const average = meanReward(BOARD)

  it('keeps the whole board when nothing is asked of it', () => {
    expect(filterBoard(scored, only(), average)).toHaveLength(3)
  })

  it('drops what does not cover its own risk', () => {
    expect(ids(filterBoard(scored, only('worthwhile'), average))).toEqual(['safe', 'mid'])
  })

  it('drops the long shots', () => {
    expect(ids(filterBoard(scored, only('likely'), average))).toEqual(['safe', 'mid'])
  })

  it('keeps only what is about to expire', () => {
    expect(ids(filterBoard(scored, only('expiring'), average))).toEqual(['rich'])
  })

  it('keeps only what pays above the board average', () => {
    expect(ids(filterBoard(scored, only('rich'), average))).toEqual(['rich'])
  })

  it('narrows rather than widens when two are stacked', () => {
    expect(filterBoard(scored, only('rich', 'worthwhile'), average)).toEqual([])
  })
})

describe('meanReward', () => {
  it('averages the board', () => {
    expect(meanReward(BOARD)).toBeCloseTo(160)
  })

  it('is zero for an empty board rather than NaN', () => {
    expect(meanReward([])).toBe(0)
  })
})
