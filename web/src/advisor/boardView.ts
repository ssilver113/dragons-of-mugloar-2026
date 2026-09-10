import { computed, ref, watchEffect, type ComputedRef, type Ref } from 'vue'
import { filterBoard, lifeCost, meanReward, scoreBoard, sortBoard } from './ranking'
import type { AdRead, FilterId, Posture, SortKey } from './ranking'
import type { AdView } from '../api/types'

/** One card, and what the advisor made of it. `read` is null while the advisor is off. */
export interface BoardEntry {
  ad: AdView
  read: AdRead | null
}

export interface BoardView {
  sort: Ref<SortKey>
  posture: Ref<Posture>
  filters: Ref<FilterId[]>
  toggleFilter: (id: FilterId) => void
  clearFilters: () => void
  /** The cards to draw, ranked and filtered. */
  entries: ComputedRef<BoardEntry[]>
  /** How many jobs the board is holding, before any filter. */
  total: ComputedRef<number>
  /** How many survive the filters. */
  shown: ComputedRef<number>
  /** What a life is worth at the chosen posture and the lives the board is scored against. */
  lifeCost: ComputedRef<number>
}

/**
 * How the board is presented: the sort, the posture, the filters, and the ranking they produce.
 *
 * This lived inside `AdList` while the advisor's controls sat above the first card. They are on
 * opposite sides of the page now — the advisor above both columns, the cards inside the left one —
 * and the two need exactly the same four values, including the count of what survives the filters,
 * which cannot be derived without running the whole pipeline. Lifting the pipeline is cheaper than
 * running it twice, and far cheaper than lifting the ranking into `App.vue` by hand.
 *
 * It is deliberately not a store. None of this outlives a game or a tab, nothing outside the board
 * reads it, and it is per-mount state that happens to be shared by two siblings — which is what a
 * composable is for and what a store is not.
 */
export function useBoardView(sources: {
  ads: Ref<AdView[]> | ComputedRef<AdView[]>
  lives: Ref<number> | ComputedRef<number>
  advisor: Ref<boolean> | ComputedRef<boolean>
  /** A turn is in flight and its board has not come back yet. See `board` below. */
  holding: Ref<boolean> | ComputedRef<boolean>
}): BoardView {
  // How the player wants the board presented is theirs, not the game's, and it outlives no game.
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
  const board = ref<AdView[]>(sources.ads.value)
  const boardLives = ref(sources.lives.value)
  watchEffect(() => {
    if (!sources.holding.value) {
      board.value = sources.ads.value
      boardLives.value = sources.lives.value
    }
  })

  const scored = computed(() => scoreBoard(board.value, posture.value, boardLives.value))
  const average = computed(() => meanReward(board.value))

  const entries = computed<BoardEntry[]>(() =>
    sources.advisor.value
      ? sortBoard(filterBoard(scored.value, new Set(filters.value), average.value), sort.value).map(
          (entry) => ({ ad: entry.ad, read: entry }),
        )
      : board.value.map((ad) => ({ ad, read: null })),
  )

  return {
    sort,
    posture,
    filters,
    toggleFilter: (id) => {
      filters.value = filters.value.includes(id)
        ? filters.value.filter((current) => current !== id)
        : [...filters.value, id]
    },
    clearFilters: () => {
      filters.value = []
    },
    entries,
    total: computed(() => board.value.length),
    shown: computed(() => entries.value.length),
    lifeCost: computed(() => lifeCost(posture.value, boardLives.value)),
  }
}
