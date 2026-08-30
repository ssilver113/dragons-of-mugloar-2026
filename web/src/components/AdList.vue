<script setup lang="ts">
import { computed, ref, watchEffect } from 'vue'
import AdCard from './AdCard.vue'
import AdToolbar from './AdToolbar.vue'
import AppIcon from './AppIcon.vue'
import { filterBoard, lifeCost, meanReward, scoreBoard, sortBoard } from '../advisor/ranking'
import type { AdRead, FilterId, Posture, SortKey } from '../advisor/ranking'
import type { AdView } from '../api/types'
import type { RequestStatus } from '../stores/game'

const props = defineProps<{
  ads: AdView[]
  status: RequestStatus
  solvingAdId: string | null
  advisor: boolean
  lives: number
  disabled: boolean
  /** A turn is in flight and its board has not come back yet. See `board` below. */
  holding: boolean
}>()
defineEmits<{ solve: [adId: string]; refresh: []; 'toggle-advisor': [] }>()

// How the player wants the board presented is theirs, not the game's, so it lives with the list
// rather than in a store: nothing outside this section acts on it, and it outlives no game.
const sort = ref<SortKey>('value')
const posture = ref<Posture>('balanced')
const filters = ref<FilterId[]>([])

/**
 * The board a turn began with, held until that turn's own refetch lands.
 *
 * A turn writes the state three times — the optimistic board on the click, the new figures when
 * the response arrives, then the real board — and the cards moved on each of them, twice
 * visibly. The middle write is the least obvious and the worst: a life lost reprices every ad on
 * a board that has not changed, so the ranking reshuffles for a reason the player cannot see.
 *
 * Holding the two inputs the ranking reads collapses all three into one change, at the moment
 * there is genuinely something new to show. Only the data is held: sorting, filtering and the
 * posture still answer immediately, because they are the player's own controls.
 */
const board = ref<AdView[]>(props.ads)
const boardLives = ref(props.lives)
watchEffect(() => {
  if (!props.holding) {
    board.value = props.ads
    boardLives.value = props.lives
  }
})

const scored = computed(() => scoreBoard(board.value, posture.value, boardLives.value))
const average = computed(() => meanReward(board.value))

const visible = computed<{ ad: AdView; read: AdRead | null }[]>(() =>
  props.advisor
    ? sortBoard(filterBoard(scored.value, new Set(filters.value), average.value), sort.value).map(
        (entry) => ({ ad: entry.ad, read: entry }),
      )
    : board.value.map((ad) => ({ ad, read: null })),
)

function toggleFilter(id: FilterId): void {
  filters.value = filters.value.includes(id)
    ? filters.value.filter((current) => current !== id)
    : [...filters.value, id]
}

// A refetch leaves the board that is already up on screen; only a board with nothing to show
// falls back to the skeleton, so the list never flashes empty between turns.
const loading = computed(() => props.status === 'pending' && board.value.length === 0)
const failed = computed(() => props.status === 'error' && board.value.length === 0)
const empty = computed(() => props.status === 'ready' && board.value.length === 0)
// Filtered to nothing is a different situation from an empty board, and has a different way out.
const filteredOut = computed(() => board.value.length > 0 && visible.value.length === 0)
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
    <div class="flex flex-wrap items-center justify-between gap-x-2 gap-y-2 sm:gap-x-4">
      <h2
        id="board-heading"
        class="flex items-center gap-1.5 text-base font-semibold sm:gap-2 sm:text-lg"
      >
        <AppIcon name="board" :size="20" class="size-4 sm:size-5" />
        Message board
      </h2>
      <div class="flex items-center gap-2 sm:gap-3">
        <!--
          A switch rather than a checkbox. It is not a field being filled in on the way to a
          submit — it turns a whole layer of the board on and off there and then — and `role`
          plus `aria-checked` carry exactly the state the checkbox used to carry for itself.
        -->
        <button
          type="button"
          role="switch"
          :aria-checked="advisor"
          class="flex items-center gap-1.5 rounded-full border px-2.5 py-1.5 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent sm:gap-2 sm:px-3"
          :class="
            advisor
              ? 'relief-pressed border-accent bg-accent/15 text-accent'
              : 'relief border-ink-muted/40 bg-surface-raised/60 text-ink-muted hover:border-ink hover:text-ink'
          "
          @click="$emit('toggle-advisor')"
        >
          <AppIcon name="advisor" :size="16" :class="advisor ? '' : 'opacity-60'" />
          Advisor
          <!--
            The track is decoration; `aria-checked` is what is actually read out, and the pill's
            own gold fill is what is seen. It is dropped below `sm` to keep this row on one line,
            where the fill is carrying the state on its own anyway.
          -->
          <span
            aria-hidden="true"
            class="relative hidden h-4 w-7 shrink-0 rounded-full sm:block"
            :class="advisor ? 'bg-accent/40' : 'bg-ink-muted/30'"
          >
            <span
              class="absolute top-0.5 size-3 rounded-full"
              :class="advisor ? 'left-3.5 bg-accent' : 'left-0.5 bg-ink-muted'"
            />
          </span>
        </button>
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
          : 'Listed as the board posted them — the advisor will rank them.'
      }}
    </p>

    <AdToolbar
      v-if="advisor"
      :sort="sort"
      :posture="posture"
      :filters="filters"
      :shown="visible.length"
      :total="board.length"
      :life-cost="lifeCost(posture, boardLives)"
      @update:sort="sort = $event"
      @update:posture="posture = $event"
      @toggle-filter="toggleFilter"
      @clear-filters="filters = []"
    />

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
    <TransitionGroup v-else tag="ul" name="card" class="grid gap-3 sm:grid-cols-2">
      <AdCard
        v-for="entry in visible"
        :key="entry.ad.adId"
        :ad="entry.ad"
        :read="entry.read"
        :solving="entry.ad.adId === solvingAdId"
        :disabled="disabled"
        @solve="$emit('solve', $event)"
      />
    </TransitionGroup>
  </section>
</template>

<style scoped>
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

  /* Delayed by the length of the glide, so a new job does not appear in a slot another card is
     still on its way out of. */
  .card-enter-active {
    transition: opacity 200ms ease-out 200ms;
  }

  .card-enter-from {
    opacity: 0;
  }
}
</style>
