<script setup lang="ts">
import { computed } from 'vue'
import { REASONS, VERDICTS, headline, isRuledOut, itemEffect, target } from './decisionCopy'
import type { LogEntry } from '../stores/autoplay'

const props = defineProps<{ entry: LogEntry }>()

const failed = computed(() => !props.entry.succeeded)
const decision = computed(() => props.entry.decision)
const weighed = computed(() => {
  const ads = decision.value.ads.length
  const items = decision.value.items.length
  return `Weighed ${ads} job${ads === 1 ? '' : 's'} and ${items} item${items === 1 ? '' : 's'}`
})

// The verdict is the accessible label as well as the row's tint, so the greying-out is never the
// only thing carrying the meaning.
const verdictClass = (ruledOut: boolean) => (ruledOut ? 'text-ink-muted' : 'text-ink')
</script>

<template>
  <li class="ledger-row entry">
    <!--
      The units are written out for a screen reader at every width, and only shown once the columns
      are gone. A ledger says "gold" at the head of a column and never again; a phone has no head to
      say it at, and neither has a reader that meets the figure on its own.
    -->
    <p class="turn tabular-nums"><span class="sr-only">Turn </span>{{ entry.game.turn }}</p>

    <div class="what min-w-0">
      <p class="font-semibold" :class="failed ? 'text-danger' : 'text-ink'">
        {{ headline(entry) }}
      </p>
      <p class="text-sm">{{ target(entry) }}</p>
      <p class="text-xs text-ink-muted">{{ REASONS[decision.reason] }}</p>

      <details class="mt-2 border-t border-dotted border-ink-muted/35 pt-1.5">
        <summary
          class="cursor-pointer text-xs text-ink-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        >
          {{ weighed }}
        </summary>

        <p v-if="entry.message" class="mt-2 text-sm italic text-ink-muted">“{{ entry.message }}”</p>

        <div v-if="decision.ads.length" class="mt-2 overflow-x-auto">
          <table class="w-full min-w-[26rem] border-collapse text-xs">
            <caption class="sr-only">
              Every job on the board, ranked as the solver ranked it
            </caption>
            <thead class="text-ink-muted">
              <tr class="text-left">
                <th scope="col" class="py-1 pr-2 font-medium">Job</th>
                <th scope="col" class="py-1 pr-2 text-right font-medium">Reward</th>
                <th scope="col" class="py-1 pr-2 text-right font-medium">Chance</th>
                <th scope="col" class="py-1 pr-2 text-right font-medium">Score</th>
                <th scope="col" class="py-1 font-medium">Verdict</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="ad in decision.ads"
                :key="ad.adId"
                class="border-t border-ink-muted/10"
                :class="verdictClass(isRuledOut(ad.verdict))"
              >
                <td class="max-w-[14rem] truncate py-1 pr-2" :title="ad.message">
                  {{ ad.message }}
                </td>
                <td class="py-1 pr-2 text-right tabular-nums">{{ ad.reward }}g</td>
                <td class="py-1 pr-2 text-right tabular-nums">
                  {{ Math.round(ad.successProbability * 100) }}%
                </td>
                <td class="py-1 pr-2 text-right tabular-nums">{{ ad.score.toFixed(1) }}</td>
                <td
                  class="py-1"
                  :class="ad.verdict === 'CHOSEN' ? 'font-semibold text-accent' : ''"
                >
                  {{ VERDICTS[ad.verdict] }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="decision.items.length" class="mt-3 overflow-x-auto">
          <table class="w-full min-w-[20rem] border-collapse text-xs">
            <caption class="sr-only">
              The purchases weighed this turn
            </caption>
            <thead class="text-ink-muted">
              <tr class="text-left">
                <th scope="col" class="py-1 pr-2 font-medium">Item</th>
                <th scope="col" class="py-1 pr-2 text-right font-medium">Cost</th>
                <th scope="col" class="py-1 pr-2 font-medium">Effect</th>
                <th scope="col" class="py-1 font-medium">Verdict</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in decision.items"
                :key="item.itemId"
                class="border-t border-ink-muted/10"
                :class="verdictClass(isRuledOut(item.verdict))"
              >
                <td class="py-1 pr-2">{{ item.name }}</td>
                <td class="py-1 pr-2 text-right tabular-nums">{{ item.cost }}g</td>
                <td class="py-1 pr-2">{{ itemEffect(item) }}</td>
                <td
                  class="py-1"
                  :class="item.verdict === 'CHOSEN' ? 'font-semibold text-accent' : ''"
                >
                  {{ VERDICTS[item.verdict] }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
    </div>

    <p class="fig">{{ entry.game.score }}<span class="unit md:sr-only"> pts</span></p>
    <p class="fig">{{ entry.game.gold }}<span class="unit md:sr-only">g</span></p>
    <p class="fig">{{ entry.game.lives }}<span class="unit md:sr-only"> lives</span></p>
    <p class="fig"><span class="unit md:sr-only">lvl </span>{{ entry.game.level }}</p>
  </li>
</template>

<style scoped>
/**
 * An entry is a row of the ruling, not a box on the page. The tracks and the margin rule come from
 * `ledger-row`; this rule places what goes in them.
 *
 * Nothing is centred vertically and nothing is aligned to the bottom. A figure sits at the top of
 * its column however far the entry beneath it runs, which is both what a ledger does and what keeps
 * the column rules running the full height of a row: a cell that shrank to its content would take
 * its own rule with it.
 */
.entry {
  border-bottom: 1px solid color-mix(in oklab, var(--color-ink-muted) 16%, transparent);
}

.entry:last-child {
  border-bottom: 0;
}

/* Faint enough to be a banding rather than a stripe: at ten entries it is what carries the eye from
   an entry across to its figures, and at one it should not be visible as anything. */
.entry:nth-child(even) {
  background-color: color-mix(in oklab, var(--color-ink-muted) 4%, transparent);
}

.turn {
  text-align: right;
  font-size: 0.72rem;
  color: var(--color-ink-muted);
}

.fig {
  text-align: right;
  font-size: 0.86rem;
  font-variant-numeric: tabular-nums;
}

/* Narrow: the figures drop to a line of their own under the entry, left-aligned and each carrying
   its own unit, because there is no head left to carry it for them. */
@media (width < 48rem) {
  .what {
    grid-column: 2 / -1;
  }

  .turn {
    grid-row: 1 / span 2;
  }

  /* The horizontal padding stays, so the first figure starts on the same line as the entry's own
     text rather than a cell's worth to the left of it. */
  .fig {
    grid-row: 2;
    padding-top: 0;
    text-align: left;
    font-size: 0.72rem;
    color: var(--color-ink-muted);
  }
}
</style>
