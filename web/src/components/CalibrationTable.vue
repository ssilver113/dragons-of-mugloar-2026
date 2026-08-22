<script setup lang="ts">
import { computed } from 'vue'
import { MIN_SAMPLE } from '../stores/calibration'
import type { CalibrationRow } from '../stores/calibration'

const props = defineProps<{ rows: CalibrationRow[]; attempts: number; games: number }>()
defineEmits<{ reset: [] }>()

const percent = (value: number) => `${Math.round(value * 100)}%`

/**
 * The verdict in words, because the delta column is the one a reviewer reads first and a bare
 * signed number does not say which direction is bad. Five points is the width of the band the
 * sample sizes here can actually resolve.
 */
function verdict(delta: number): string {
  if (Math.abs(delta) < 0.05) {
    return 'on the money'
  }
  return delta > 0 ? 'too cautious' : 'too hopeful'
}

const summary = computed(() =>
  props.attempts === 0
    ? 'Advisor calibration — nothing attempted yet'
    : `Advisor calibration — ${props.attempts} jobs across ${props.games} ${
        props.games === 1 ? 'game' : 'games'
      }`,
)
</script>

<template>
  <details class="rounded-lg border border-ink-muted/20 bg-surface-raised/40">
    <summary
      class="cursor-pointer rounded-lg px-4 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
    >
      {{ summary }}
    </summary>

    <div class="flex flex-col gap-3 px-4 pb-4">
      <p class="text-xs text-ink-muted">
        What the advisor predicted before each job, against how those jobs actually went. The tally
        carries across games so the estimate has enough attempts to settle; a label needs
        {{ MIN_SAMPLE }} before its rate is worth reading.
      </p>

      <p v-if="rows.length === 0" class="text-sm text-ink-muted">
        Solve a job and the first row appears here.
      </p>

      <div v-else class="overflow-x-auto">
        <table class="w-full min-w-100 border-collapse text-sm">
          <caption class="sr-only">
            Predicted against observed success rate, by probability label
          </caption>
          <thead>
            <tr class="border-b border-ink-muted/20 text-left text-xs text-ink-muted">
              <th scope="col" class="py-1.5 pr-3 font-medium">Odds</th>
              <th scope="col" class="py-1.5 pr-3 font-medium">Tier</th>
              <th scope="col" class="py-1.5 pr-3 text-right font-medium">Tried</th>
              <th scope="col" class="py-1.5 pr-3 text-right font-medium">Model said</th>
              <th scope="col" class="py-1.5 pr-3 text-right font-medium">Actually</th>
              <th scope="col" class="py-1.5 font-medium">Verdict</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.label"
              class="border-b border-ink-muted/10 last:border-0"
              :class="{ 'text-ink-muted': !row.enough }"
            >
              <th scope="row" class="py-1.5 pr-3 text-left font-normal">{{ row.label }}</th>
              <td class="py-1.5 pr-3 text-xs uppercase tracking-wide text-ink-muted">
                {{ row.tier }}
              </td>
              <td class="py-1.5 pr-3 text-right tabular-nums">{{ row.attempts }}</td>
              <td class="py-1.5 pr-3 text-right tabular-nums">{{ percent(row.predicted) }}</td>
              <td class="py-1.5 pr-3 text-right tabular-nums">
                {{ row.successes }}/{{ row.attempts }} · {{ percent(row.observed) }}
              </td>
              <td class="py-1.5">
                <span v-if="!row.enough" class="text-xs">too few to read</span>
                <span v-else class="flex items-center gap-2">
                  <!-- The bar is decoration; the two percentage columns are the accessible truth. -->
                  <span
                    aria-hidden="true"
                    class="relative h-1.5 w-16 shrink-0 rounded-full bg-ink-muted/20"
                  >
                    <span
                      class="absolute inset-y-0 left-0 rounded-full bg-accent"
                      :style="{ width: percent(row.observed) }"
                    />
                    <span
                      class="absolute inset-y-[-2px] w-0.5 bg-ink"
                      :style="{ left: percent(row.predicted) }"
                    />
                  </span>
                  <span class="text-xs">{{ verdict(row.delta) }}</span>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="rows.length" class="flex justify-end">
        <button
          type="button"
          class="rounded-md border border-ink-muted/40 px-3 py-1.5 text-xs hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          @click="$emit('reset')"
        >
          Clear the tally
        </button>
      </div>
    </div>
  </details>
</template>
