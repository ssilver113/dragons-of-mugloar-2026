import type { AdView } from '../api/types'

/**
 * How dearly the player holds a life, in gold. The solver keeps one such number and divides it by
 * the lives in hand, so the last life is always the dearest; a posture is that number moved.
 *
 * `balanced` is the figure the backend strategy actually plays with, so the ordering the player
 * sees at `balanced` is the ordering the bot would produce. The other two are what-ifs.
 */
export type Posture = 'cautious' | 'balanced' | 'bold'

export const POSTURES: ReadonlyArray<{ id: Posture; label: string; lifeValueGold: number }> = [
  { id: 'cautious', label: 'Cautious', lifeValueGold: 900 },
  { id: 'balanced', label: 'Balanced', lifeValueGold: 300 },
  { id: 'bold', label: 'Bold', lifeValueGold: 100 },
]

export type SortKey = 'value' | 'reward' | 'chance' | 'expiry'

export const SORTS: ReadonlyArray<{ id: SortKey; label: string }> = [
  { id: 'value', label: 'Worth the risk' },
  { id: 'reward', label: 'Reward' },
  { id: 'chance', label: 'Chance' },
  { id: 'expiry', label: 'Expiring first' },
]

export type FilterId = 'worthwhile' | 'likely' | 'expiring' | 'rich'

export const FILTERS: ReadonlyArray<{ id: FilterId; label: string; hint: string }> = [
  { id: 'worthwhile', label: 'Worth the risk', hint: 'Value above zero at this posture' },
  { id: 'likely', label: 'Good odds', hint: 'At least an even chance' },
  { id: 'expiring', label: 'Expiring soon', hint: 'Two turns left or fewer' },
  { id: 'rich', label: 'Pays above average', hint: 'Reward above the board average' },
]

/**
 * Three bands, because the question a player is asking has three answers: take it, take it if
 * nothing better shows up, leave it. `strong` is relative to the best the board is offering —
 * an absolute cutoff would either praise a weak board or condemn a good one.
 */
export type ValueBand = 'strong' | 'fair' | 'poor'

/** What the advisor concluded about one ad, at the current posture and lives. */
export interface AdRead {
  /** `reward × p − lifeCost × (1 − p)`, in gold. The solver's own formula, at the chosen posture. */
  score: number
  band: ValueBand
  /** Pays well, costs more. The ad a human takes and a bot does not. */
  trap: boolean
}

export interface ScoredAd extends AdRead {
  ad: AdView
}

/** What a life is worth right now: scarcity, not sentiment — the last one is the dearest. */
export function lifeCost(posture: Posture, lives: number): number {
  const { lifeValueGold } = POSTURES.find((p) => p.id === posture) ?? POSTURES[1]
  return lifeValueGold / Math.max(1, lives)
}

export function riskAdjustedScore(ad: AdView, posture: Posture, lives: number): number {
  const p = ad.successProbability
  return ad.reward * p - lifeCost(posture, lives) * (1 - p)
}

/**
 * Among the board's best by reward and among its worst by value, and losing money at that: the ad
 * that looks like the obvious pick and is close to the worst one there.
 *
 * All three conditions earn their place. Reward alone flags the biggest job on every board;
 * a negative score alone flags most of a weak board, where nothing covers its own risk. Requiring
 * the ad to be *relatively* bad as well is what keeps the flag rare enough to be worth reading —
 * a live board of ten ads at level 0 had a 35g job scoring −1g, which the first two conditions
 * branded as the job that ends a run. It is not; it is merely not worth a turn.
 *
 * Judged at `balanced` whatever posture is being browsed: it is a claim about the board, not about
 * the player's nerve, and a flag that vanished on the bold setting would be gone exactly when it
 * is most needed.
 */
function isTrap(score: number, reward: number, cutoffs: Cutoffs): boolean {
  return reward >= cutoffs.richReward && score <= 0 && score <= cutoffs.poorScore
}

interface Cutoffs {
  /** The reward at which an ad counts as one of the board's big jobs. */
  richReward: number
  /** The score at which it counts as one of its worst. */
  poorScore: number
}

/** The value a third of the way in from one end, or the whole board when there is nothing to rank. */
function thirdFrom(values: number[], compare: (a: number, b: number) => number): number {
  const sorted = [...values].sort(compare)
  return sorted[Math.floor((sorted.length - 1) / 3)]
}

export function meanReward(ads: AdView[]): number {
  return ads.length === 0 ? 0 : ads.reduce((sum, ad) => sum + ad.reward, 0) / ads.length
}

export function scoreBoard(ads: AdView[], posture: Posture, lives: number): ScoredAd[] {
  if (ads.length === 0) {
    return []
  }
  const scores = ads.map((ad) => riskAdjustedScore(ad, posture, lives))
  // The trap flag is judged at `balanced`, so it needs its own scores whenever the player is not.
  const balanced =
    posture === 'balanced' ? scores : ads.map((ad) => riskAdjustedScore(ad, 'balanced', lives))
  const cutoffs: Cutoffs = {
    richReward: thirdFrom(
      ads.map((ad) => ad.reward),
      (a, b) => b - a,
    ),
    poorScore: thirdFrom(balanced, (a, b) => a - b),
  }
  const best = Math.max(0, ...scores)
  return ads.map((ad, index) => ({
    ad,
    score: scores[index],
    band: scores[index] <= 0 ? 'poor' : scores[index] >= best * 0.6 ? 'strong' : 'fair',
    trap: isTrap(balanced[index], ad.reward, cutoffs),
  }))
}

export function filterBoard(
  scored: ScoredAd[],
  filters: Set<FilterId>,
  average: number,
): ScoredAd[] {
  if (filters.size === 0) {
    return scored
  }
  // Every filter must hold, so stacking two narrows rather than widens — which is what a reader
  // of a checkbox list expects, and the only reading under which "clear" has an obvious meaning.
  return scored.filter(
    (entry) =>
      (!filters.has('worthwhile') || entry.score > 0) &&
      (!filters.has('likely') || entry.ad.successProbability >= 0.5) &&
      (!filters.has('expiring') || entry.ad.expiresIn <= 2) &&
      (!filters.has('rich') || entry.ad.reward >= average),
  )
}

/**
 * Sorted descending on the chosen key, except expiry, where sooner is more urgent. Every key
 * breaks its ties on expiry and then on ad id, so an equal board keeps a stable order across
 * re-renders instead of shuffling under the cursor each turn.
 */
export function sortBoard(scored: ScoredAd[], key: SortKey): ScoredAd[] {
  const primary: Record<SortKey, (entry: ScoredAd) => number> = {
    value: (entry) => -entry.score,
    reward: (entry) => -entry.ad.reward,
    chance: (entry) => -entry.ad.successProbability,
    expiry: (entry) => entry.ad.expiresIn,
  }
  const rank = primary[key]
  return [...scored].sort(
    (a, b) =>
      rank(a) - rank(b) || a.ad.expiresIn - b.ad.expiresIn || a.ad.adId.localeCompare(b.ad.adId),
  )
}
