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
  <li class="rounded-lg border border-ink-muted/35 bg-surface-raised/70">
    <div class="flex flex-col gap-1 p-3">
      <div class="flex items-baseline justify-between gap-3">
        <p class="font-semibold" :class="failed ? 'text-danger' : 'text-ink'">
          <span class="mr-2 text-xs font-normal tabular-nums text-ink-muted"
            >Turn {{ entry.game.turn }}</span
          >
          {{ headline(entry) }}
        </p>
        <p class="shrink-0 text-xs tabular-nums text-ink-muted">
          {{ entry.game.score }} pts · {{ entry.game.gold }}g · {{ entry.game.lives }} lives · lvl
          {{ entry.game.level }}
        </p>
      </div>

      <p class="text-sm">{{ target(entry) }}</p>
      <p class="text-xs text-ink-muted">{{ REASONS[decision.reason] }}</p>
    </div>

    <details class="border-t border-ink-muted/15 px-3 py-2">
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
              <td class="py-1" :class="ad.verdict === 'CHOSEN' ? 'font-semibold text-accent' : ''">
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
              <td class="py-1" :class="item.verdict === 'CHOSEN' ? 'font-semibold text-accent' : ''">
                {{ VERDICTS[item.verdict] }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </details>
  </li>
</template>
