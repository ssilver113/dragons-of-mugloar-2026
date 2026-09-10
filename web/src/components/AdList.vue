<script setup lang="ts">
import { computed } from 'vue'
import AdCard from './AdCard.vue'
import AppIcon from './AppIcon.vue'
import type { BoardEntry } from '../advisor/boardView'
import type { RequestStatus } from '../stores/game'

/**
 * The cards, drawn in the order they arrive in.
 *
 * The ranking itself is not here any more, and neither are the controls that set it. The advisor
 * moved above both columns, so the sort, the posture and the filters left with it — into
 * `useBoardView`, which both this and the panel read. What is left is a section that renders the
 * board it is handed and knows the four states it can be in.
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
 * The board the jobs are pinned to. Its surface is the `timber` utility, which the shop's
 * shopfront also uses — this rule only sets what is particular to a board full of cards.
 *
 * It used to be cork, chosen because the whole conceit is paper *pinned up* and cork is the thing
 * you can push a pin into. That reasoning was sound about the tack and wrong about the page: at the
 * width the board actually renders, most of the cork shows as a margin between cards, where the
 * crumb is too fine to register and it read as one more brown frame beside the shop's. Two
 * materials that are almost the same are worse than one, so there is now one.
 *
 * A timber frame and four brass corner brackets were built for it once and taken out again. Each
 * was defensible alone and together they were three materials and a piece of hardware between the
 * reader and ten sheets of paper. The surface and the tack are still the whole idea.
 *
 * Nothing but the cards is ever laid on it, so no text is ever measured against this surface. The
 * heading and the intro stay above it on the page; the advisor's own controls are on their own
 * surface above the whole layout.
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

  /* Out of the flow before the survivors are measured, or they would glide towards the gap the
     departing card is still holding open and then snap when it goes. */
  .card-leave-active {
    position: absolute;
    visibility: hidden;
  }

  /* Never from nothing, and barely delayed. A card that is invisible still holds its grid cell, so
     a turn that replaces most of the board used to punch a hole in it for the length of the delay
     — invisible while the empty cell showed the page behind it, and a dark void once there was a
     plank back there. Entering at a quarter opacity means there is always a sheet in the slot.

     The delay it replaces was there so a new job did not land on top of a survivor still gliding
     into the same slot. Sixty milliseconds and a ghost rather than two hundred and a hole: the
     overlap that remains is between a moving card and a faint one, which is not what the eye goes
     to. */
  .card-enter-active {
    transition: opacity 240ms ease-out 60ms;
  }

  .card-enter-from {
    opacity: 0.25;
  }
}
</style>
