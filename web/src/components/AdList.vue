<script setup lang="ts">
import { computed } from 'vue'
import AdCard from './AdCard.vue'
import AppIcon from './AppIcon.vue'
import type { BoardEntry } from '../advisor/boardView'
import type { RequestStatus } from '../stores/game'

/**
 * The cards, drawn in the order they arrive in. Ranking and the controls that set it live in
 * `useBoardView`, which this and the advisor's panel both read.
 */
const props = defineProps<{
  /** Already scored, filtered and ranked. `read` is null on every entry while the advisor is off. */
  entries: BoardEntry[]
  /** How many jobs the board is holding before the filters, which is not `entries.length`. */
  total: number
  status: RequestStatus
  solvingAdId: string | null
  advisor: boolean
  disabled: boolean
}>()
defineEmits<{ solve: [adId: string]; refresh: [] }>()

// A refetch leaves the board that is already up on screen; only a board with nothing to show
// falls back to the skeleton, so the list never flashes empty between turns.
const loading = computed(() => props.status === 'pending' && props.total === 0)
const failed = computed(() => props.status === 'error' && props.total === 0)
const empty = computed(() => props.status === 'ready' && props.total === 0)
// Filtered to nothing is a different situation from an empty board, and has a different way out.
const filteredOut = computed(() => props.total > 0 && props.entries.length === 0)
</script>

<template>
  <section
    aria-labelledby="board-heading"
    class="flex flex-col gap-3"
    :aria-busy="solvingAdId !== null || status === 'pending'"
  >
    <!--
      One row at 375px, deliberately. It used to wrap to two, so the board's first card sat forty
      pixels lower than the shop's first row and the log's first entry — and switching between the
      three on a phone moved everything under the tabs.
    -->
    <!--
      `min-h-8.5` is shared with the shop's heading row, and the two are a pair: this row is taller
      than that one because it carries controls, and the six pixels of difference pushed the board's
      surface six pixels below the shopfront's. Two panels side by side, starting at the same
      height, missing it by six. Pinning both rows to the height of a control settles it from
      either side rather than making one column chase the other.
    -->
    <div class="flex min-h-8.5 flex-wrap items-center justify-between gap-x-2 gap-y-2 sm:gap-x-4">
      <h2
        id="board-heading"
        class="flex items-center gap-1.5 text-base font-semibold sm:gap-2 sm:text-lg"
      >
        <AppIcon name="board" :size="20" class="size-4 sm:size-5" />
        Message board
      </h2>
      <!--
        The advisor's switch used to stand here. It is on its own table above both columns now, with
        the controls it governs and the record it has kept — one place instead of three. The heading
        row is left with the one control that acts on the board itself.
      -->
      <div class="flex items-center gap-2 sm:gap-3">
        <button
          type="button"
          class="relief rounded-md border border-ink-muted/40 bg-surface-raised/60 px-2 py-1.5 text-xs hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40 disabled:shadow-none sm:px-3 sm:text-sm"
          :disabled="disabled"
          @click="$emit('refresh')"
        >
          Refresh
        </button>
      </div>
    </div>
    <p class="-mt-1 text-sm text-ink-muted">
      {{
        advisor
          ? 'Jobs are ranked by what the advisor thinks they are worth.'
          : 'Listed as the board posted them.'
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

    <div v-else-if="failed" class="panel panel-danger p-4" role="alert">
      <p class="font-semibold">The message board could not be loaded.</p>
      <button
        type="button"
        class="relief mt-2 rounded-md border border-ink-muted/40 bg-surface-raised/60 px-3 py-1.5 text-sm hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="$emit('refresh')"
      >
        Try again
      </button>
    </div>

    <p v-else-if="empty" class="panel p-6 text-center text-ink-muted">
      No ads on the board right now. Refresh to see what comes in.
    </p>

    <p v-else-if="filteredOut" class="panel p-6 text-center text-ink-muted">
      Every job on the board is filtered out. Loosen the filters to see them.
    </p>

    <!--
      One change a turn, and it is a movement rather than a cut: the cards that survive glide to
      where the new board puts them, and the ones that arrive fade in behind them. A card that
      leaves goes at once — the job was taken, and its Solve button has been saying so.
    -->
    <div v-else class="board timber">
      <TransitionGroup tag="ul" name="card" class="grid gap-3 sm:grid-cols-2">
        <AdCard
          v-for="entry in entries"
          :key="entry.ad.adId"
          :ad="entry.ad"
          :read="entry.read"
          :solving="entry.ad.adId === solvingAdId"
          :disabled="disabled"
          @solve="$emit('solve', $event)"
        />
      </TransitionGroup>
    </div>
  </section>
</template>

<style scoped>
/**
 * The board the jobs are pinned to. `timber` is the surface, shared with the shopfront; this sets
 * what is particular to a board full of cards. Nothing but cards is laid on it, so no text is ever
 * measured against this surface — the heading and the intro stay above it on the page.
 */
.board {
  padding: 1rem;
}

@media (width >= 40rem) {
  .board {
    padding: 1.5rem;
  }
}

@media (prefers-reduced-motion: no-preference) {
  .card-move {
    transition: transform 260ms cubic-bezier(0.22, 0.61, 0.36, 1);
  }

  /* Out of the flow before the survivors are measured, or they glide towards a gap that is about
     to close and then snap. */
  .card-leave-active {
    position: absolute;
    visibility: hidden;
  }

  /* Never from nothing: an invisible card still holds its cell, so a turn replacing most of the
     board punched a hole in the plank behind it. A quarter opacity keeps a sheet in every slot,
     which is what buys the delay down from 200ms to 60. */
  .card-enter-active {
    transition: opacity 240ms ease-out 60ms;
  }

  .card-enter-from {
    opacity: 0.25;
  }
}
</style>
