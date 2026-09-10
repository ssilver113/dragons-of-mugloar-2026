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
 * Lifted out of `AdList` because the advisor's panel needs the same four values, including the
 * filtered count, which cannot be had without running the whole pipeline.
 *
 * Not a store: none of it outlives a game, and nothing outside the board reads it.
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
   * The board a turn began with, held until that turn's own refetch lands. A turn writes the state
   * three times and the cards moved on each; the worst is the middle one, where a lost life
   * reprices an unchanged board and the ranking reshuffles for no visible reason. Only the data is
   * held — the player's own controls still answer immediately.
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
