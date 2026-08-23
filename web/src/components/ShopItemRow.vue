<script setup lang="ts">
import { computed } from 'vue'
import TurnSpinner from './TurnSpinner.vue'
import { itemArt } from '../assets/artwork'
import type { ShopItemView } from '../api/types'

const props = defineProps<{
  item: ShopItemView
  gold: number
  buying: boolean
  disabled: boolean
}>()
defineEmits<{ buy: [itemId: string] }>()

/**
 * Described from the numbers the server sends rather than from a name, so an item whose effect
 * was never measured says so instead of promising something.
 */
const effect = computed(() => {
  const { livesGained, levelsGained } = props.item
  const parts: string[] = []
  if (livesGained) {
    parts.push(`+${livesGained} ${livesGained === 1 ? 'life' : 'lives'}`)
  }
  if (levelsGained) {
    parts.push(`+${levelsGained} ${levelsGained === 1 ? 'level' : 'levels'}`)
  }
  return parts.join(' and ')
})

const icon = computed(() =>
  itemArt(props.item.id, props.item.livesGained, props.item.levelsGained),
)

const affordable = computed(() => props.item.cost <= props.gold)
const shortfall = computed(() => props.item.cost - props.gold)

// Only a row the player could act on warms under the cursor. One they cannot afford is already
// dimmed, and inviting a click on it would be a promise the Buy button then refuses.
const hoverable = computed(() => affordable.value && !props.disabled)
</script>

<template>
  <li
    class="flex items-center justify-between gap-3 rounded-lg border border-ink-muted/20 bg-surface-raised px-3 py-2"
    :class="[{ 'opacity-60': !affordable }, hoverable ? 'hover:border-ink-muted/60' : '']"
  >
    <!-- Decorative: the item's name and effect are spelled out immediately to its right. -->
    <img
      :src="icon"
      alt=""
      aria-hidden="true"
      width="28"
      height="28"
      loading="lazy"
      decoding="async"
      class="size-7 shrink-0"
    />
    <div class="min-w-0 flex-1">
      <p class="truncate text-sm font-medium">{{ item.name }}</p>
      <p class="text-xs text-ink-muted">
        <span class="tabular-nums">{{ item.cost }}g</span>
        <template v-if="effect"> · {{ effect }}</template>
        <template v-else> · effect unknown</template>
      </p>
    </div>
    <button
      type="button"
      class="shrink-0 rounded-md bg-accent px-3 py-1.5 text-sm font-semibold text-surface hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:bg-transparent disabled:text-ink-muted disabled:opacity-60"
      :disabled="disabled || !affordable"
      :aria-label="
        affordable
          ? `Buy ${item.name} for ${item.cost} gold`
          : `${item.name} costs ${item.cost} gold, ${shortfall} more than you have`
      "
      @click="$emit('buy', item.id)"
    >
      <span class="flex items-center gap-1.5">
        <TurnSpinner v-if="buying" />
        <template v-if="buying">Buying…</template>
        <template v-else-if="affordable">Buy</template>
        <template v-else>{{ shortfall }}g short</template>
      </span>
    </button>
  </li>
</template>
