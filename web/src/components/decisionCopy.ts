import type { AdOptionView, DecisionView, ItemOptionView, Reason, Verdict } from '../api/types'
import type { LogEntry } from '../stores/autoplay'

/**
 * The solver sends codes, not sentences, so the log reads in the app's voice and the wording can
 * change without a backend release. Every member is spelled out rather than defaulted: a new code
 * on the Java side should fail the type check here, not quietly render as itself.
 */
export const REASONS: Record<Reason, string> = {
  HEALING_LOW_ON_LIVES: 'Down to the last lives, and a potion was affordable. Survival first.',
  LEVELLING_BEHIND_TARGET: 'Behind the level this board expects by now, so it bought the catch-up.',
  BEST_RISK_ADJUSTED_AD: 'Best reward on the board once the risk to a life is priced in.',
  LEVELLING_NO_AD_WORTH_A_LIFE: 'Nothing was worth a life, so the turn bought a level instead.',
  HEALING_NO_AD_WORTH_A_LIFE: 'Nothing was worth a life and levelling was out of reach, so it bought one.',
  PASSING_NOTHING_WORTH_A_TURN: 'Nothing worth a life, nothing affordable. Passing beats gambling.',
}

export const VERDICTS: Record<Verdict, string> = {
  CHOSEN: 'Chosen',
  OUTRANKED: 'Outranked',
  NOT_WORTH_A_LIFE: 'Not worth a life',
  NEVER_ATTEMPT: 'Never attempt',
  UNREADABLE: 'Unreadable',
  UNAFFORDABLE: 'Too expensive',
  NOT_NEEDED: 'Not needed',
}

/** Verdicts that mean "ruled out", as opposed to merely beaten. Drives the non-colour cue too. */
const RULED_OUT: ReadonlySet<Verdict> = new Set<Verdict>([
  'NEVER_ATTEMPT',
  'NOT_WORTH_A_LIFE',
  'UNREADABLE',
  'UNAFFORDABLE',
  'NOT_NEEDED',
])

export function isRuledOut(verdict: Verdict): boolean {
  return RULED_OUT.has(verdict)
}

export function chosenAd(decision: DecisionView): AdOptionView | null {
  return decision.ads.find((ad) => ad.verdict === 'CHOSEN') ?? null
}

export function chosenItem(decision: DecisionView): ItemOptionView | null {
  return decision.items.find((item) => item.verdict === 'CHOSEN') ?? null
}

/** What the turn did, in three or four words. The detail is one line below it. */
export function headline(entry: LogEntry): string {
  switch (entry.decision.move) {
    case 'SOLVE_AD':
      return entry.succeeded ? 'Mission accomplished' : 'Mission failed'
    case 'BUY_ITEM':
      // Names are flavour and effects travel as numbers, so the headline reads the effect.
      return entry.succeeded ? `Bought ${boughtWhat(entry)}` : 'The shop refused the sale'
    case 'INVESTIGATE_REPUTATION':
      return 'Passed the turn'
  }
}

function boughtWhat(entry: LogEntry): string {
  const item = chosenItem(entry.decision)
  if (item && item.livesGained > 0 && item.levelsGained === 0) {
    return item.livesGained > 1 ? 'some lives' : 'a life'
  }
  return item && item.levelsGained > 0 ? 'an upgrade' : 'an item'
}

/** What the turn was aimed at: the job taken, the item bought, or nothing at all. */
export function target(entry: LogEntry): string {
  switch (entry.decision.move) {
    case 'SOLVE_AD':
      return chosenAd(entry.decision)?.message ?? 'A job that is no longer on the board'
    case 'BUY_ITEM':
      return chosenItem(entry.decision)?.name ?? 'An item the shop no longer lists'
    case 'INVESTIGATE_REPUTATION':
      return 'Asked around town instead — it costs a turn and risks nothing'
  }
}

/** Item effects travel as the numbers they move, so the label is assembled rather than looked up. */
export function itemEffect(item: ItemOptionView): string {
  const effects: string[] = []
  if (item.levelsGained > 0) {
    effects.push(`+${item.levelsGained} level${item.levelsGained > 1 ? 's' : ''}`)
  }
  if (item.livesGained > 0) {
    effects.push(`+${item.livesGained} life${item.livesGained > 1 ? 'ves' : ''}`)
  }
  return effects.join(', ') || 'unmeasured'
}
