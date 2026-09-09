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
    <!-- `min-h-8.5` is shared with the board's heading row; see the note there. -->
    <div class="flex min-h-8.5 items-center justify-between gap-4">
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
    <p class="-mt-1 text-sm text-ink-muted">Buying costs a turn, and ages every ad by one.</p>

    <ul v-if="loading" class="flex flex-col gap-2" aria-hidden="true">
      <li
        v-for="n in 4"
        :key="n"
        class="h-12 rounded-lg bg-surface-raised motion-safe:animate-pulse"
      />
    </ul>
    <p v-if="loading" class="sr-only" role="status">Loading the shop.</p>

    <div v-else-if="failed" class="panel panel-danger p-4" role="alert">
      <p class="font-semibold">The shop could not be loaded.</p>
      <button
        type="button"
        class="relief mt-2 rounded-md border border-ink-muted/40 bg-surface-raised/60 px-3 py-1.5 text-sm hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="$emit('refresh')"
      >
        Try again
      </button>
    </div>

    <!--
      The rail is decoration and says nothing, so it is out of the tree rather than merely
      unlabelled: to a screen reader the shop is still a heading, a line of prose and a list.
    -->
    <div v-else class="shopfront timber">
      <div class="rail" aria-hidden="true" />
      <ul class="hangings">
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
    </div>
  </section>
</template>

<style scoped>
/**
 * The shopfront. Its surface is the `timber` utility, shared with the message board next to it —
 * one material, defined once, so the two cannot drift apart again. This rule sets only what is
 * particular to a rail with things hanging off it.
 *
 * The frame is `oak-deep` and the plaques are `oak`. Only the plaques carry text, and only `oak` is
 * bounded against the measured palette; nothing is ever read against the frame.
 */
.shopfront {
  padding: 0.75rem 1rem 1.25rem;
}

/**
 * The rail. A turned bar rather than a flat strip: the highlight is a third of the way down and the
 * shadow runs to the bottom edge, which is what makes a cylinder out of a rectangle.
 *
 * It runs a quarter-rem proud of the padding on each side so it reads as spanning the frame and
 * socketed into it, rather than as one more thing resting inside the box.
 */
.rail {
  height: 9px;
  margin: 0 -0.25rem;
  border-radius: 9999px;
  background-image: linear-gradient(
    oklch(58% 0.055 70),
    oklch(78% 0.06 78) 32%,
    oklch(48% 0.05 66) 72%,
    oklch(34% 0.04 60)
  );
  box-shadow: 0 2px 3px oklch(18% 0.02 50 / 0.5);
}

/**
 * The drop is the shop's, not the item's: the gap between two plaques and the length of the ropes
 * spanning it are the same measurement, so it is declared once here and read by `ShopItemRow`.
 * Changing the gap without the rope would leave the ropes ending in mid-air.
 */
.hangings {
  --rope-drop: 11px;

  display: flex;
  flex-direction: column;
  gap: var(--rope-drop);
  margin-top: var(--rope-drop);
}
</style>
