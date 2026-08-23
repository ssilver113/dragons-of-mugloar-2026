<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'
import ShopItemRow from './ShopItemRow.vue'
import type { ShopItemView } from '../api/types'
import type { RequestStatus } from '../stores/game'

const props = defineProps<{
  items: ShopItemView[]
  gold: number
  status: RequestStatus
  buyingItemId: string | null
  disabled: boolean
}>()
defineEmits<{ buy: [itemId: string]; refresh: [] }>()

// The catalogue is fetched once per game and never changes, so these are genuinely first-load
// states rather than the between-turns flicker the board has to guard against.
const loading = computed(() => props.status === 'pending' && props.items.length === 0)
const failed = computed(() => props.status === 'error' && props.items.length === 0)
</script>

<template>
  <section aria-labelledby="shop-heading" class="flex flex-col gap-3">
    <div class="flex items-baseline justify-between gap-4">
      <h2
        id="shop-heading"
        class="flex items-center gap-1.5 text-base font-semibold sm:gap-2 sm:text-lg"
      >
        <AppIcon name="shop" :size="20" class="size-4 sm:size-5" />
        Shop
      </h2>
      <p class="flex items-center gap-1.5 text-sm text-ink-muted">
        <AppIcon name="gold" :size="14" />
        <span class="tabular-nums">{{ gold }}</span> gold
      </p>
    </div>
    <p class="-mt-1 text-xs text-ink-muted">Buying costs a turn, and ages every ad by one.</p>

    <ul v-if="loading" class="flex flex-col gap-2" aria-hidden="true">
      <li v-for="n in 4" :key="n" class="h-12 rounded-lg bg-surface-raised motion-safe:animate-pulse" />
    </ul>
    <p v-if="loading" class="sr-only" role="status">Loading the shop.</p>

    <div v-else-if="failed" class="rounded-lg border border-danger/50 bg-danger/10 p-4" role="alert">
      <p class="font-semibold">The shop could not be loaded.</p>
      <button
        type="button"
        class="relief mt-2 rounded-md border border-ink-muted/40 bg-surface-raised/60 px-3 py-1.5 text-sm hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="$emit('refresh')"
      >
        Try again
      </button>
    </div>

    <ul v-else class="flex flex-col gap-2">
      <ShopItemRow
        v-for="item in items"
        :key="item.id"
        :item="item"
        :gold="gold"
        :buying="item.id === buyingItemId"
        :disabled="disabled"
        @buy="$emit('buy', $event)"
      />
    </ul>
  </section>
</template>
