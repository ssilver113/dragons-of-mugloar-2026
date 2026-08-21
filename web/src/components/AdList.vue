<script setup lang="ts">
import { computed } from 'vue'
import AdCard from './AdCard.vue'
import type { AdView } from '../api/types'
import type { RequestStatus } from '../stores/game'

const props = defineProps<{
  ads: AdView[]
  status: RequestStatus
  solvingAdId: string | null
  advisor: boolean
  disabled: boolean
}>()
defineEmits<{ solve: [adId: string]; refresh: []; 'toggle-advisor': [] }>()

// A refetch after a solve leaves the optimistic board on screen; only a board with nothing to
// show falls back to the skeleton, so the list never flashes empty between turns.
const loading = computed(() => props.status === 'pending' && props.ads.length === 0)
const failed = computed(() => props.status === 'error' && props.ads.length === 0)
const empty = computed(() => props.status === 'ready' && props.ads.length === 0)
</script>

<template>
  <section aria-labelledby="board-heading" class="flex flex-col gap-3">
    <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
      <h2 id="board-heading" class="text-lg font-semibold">Message board</h2>
      <div class="flex items-center gap-4">
        <!-- A native checkbox: it carries its own state to a screen reader and to the eye. -->
        <label class="flex items-center gap-2 text-sm text-ink-muted">
          <input
            type="checkbox"
            class="size-4 accent-accent focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
            :checked="advisor"
            @change="$emit('toggle-advisor')"
          />
          Advisor
        </label>
        <button
          type="button"
          class="rounded-md border border-ink-muted/40 px-3 py-1.5 text-sm hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="disabled"
          @click="$emit('refresh')"
        >
          Refresh
        </button>
      </div>
    </div>
    <p class="-mt-1 text-xs text-ink-muted">
      {{
        advisor
          ? 'Jobs are ranked by what the advisor thinks they are worth.'
          : 'Jobs are listed as the board posted them. Turn on the advisor for an estimate.'
      }}
    </p>

    <ul v-if="loading" class="flex flex-col gap-3" aria-hidden="true">
      <li
        v-for="n in 3"
        :key="n"
        class="h-40 rounded-lg bg-surface-raised motion-safe:animate-pulse sm:h-32"
      />
    </ul>
    <p v-if="loading" class="sr-only" role="status">Loading the message board.</p>

    <div v-else-if="failed" class="rounded-lg border border-danger/50 bg-danger/10 p-4" role="alert">
      <p class="font-semibold">The message board could not be loaded.</p>
      <button
        type="button"
        class="mt-2 rounded-md border border-ink-muted/40 px-3 py-1.5 text-sm hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="$emit('refresh')"
      >
        Try again
      </button>
    </div>

    <p v-else-if="empty" class="rounded-lg border border-ink-muted/20 p-6 text-center text-ink-muted">
      No ads on the board right now. Refresh to see what comes in.
    </p>

    <ul v-else class="grid gap-3 sm:grid-cols-2">
      <AdCard
        v-for="ad in ads"
        :key="ad.adId"
        :ad="ad"
        :advisor="advisor"
        :solving="ad.adId === solvingAdId"
        :disabled="disabled"
        @solve="$emit('solve', $event)"
      />
    </ul>
  </section>
</template>
