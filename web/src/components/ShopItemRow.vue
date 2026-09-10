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

const icon = computed(() => itemArt(props.item.id, props.item.livesGained, props.item.levelsGained))

const affordable = computed(() => props.item.cost <= props.gold)
const shortfall = computed(() => props.item.cost - props.gold)
</script>

<template>
  <li
    class="plaque flex items-center justify-between gap-3 px-3 py-2.5"
    :class="{ 'is-dimmed': !affordable }"
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
      <!-- The cost moves rather than repeating: on an unaffordable row it is on the stud, where
           the word "Buy" has nothing left to offer. -->
      <p class="text-xs" :class="affordable ? 'text-ink-muted' : 'text-ink'">
        <template v-if="affordable">
          <span class="tabular-nums">{{ item.cost }}g</span>
          <template v-if="effect"> · {{ effect }}</template>
          <template v-else> · effect unknown</template>
        </template>
        <template v-else>{{ effect || 'Effect unknown' }}</template>
      </p>
    </div>
    <button
      type="button"
      class="relief shrink-0 rounded-md bg-brass px-3 py-1.5 text-sm font-semibold text-ink hover:brightness-105 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:bg-transparent disabled:bg-none disabled:text-ink disabled:shadow-none"
      :disabled="disabled || !affordable"
      :aria-label="
        affordable
          ? `Buy ${item.name} for ${item.cost} gold`
          : `${item.name} costs ${item.cost} gold, ${shortfall} more than you have`
      "
      @click="$emit('buy', item.id)"
    >
      <span class="flex items-center gap-1.5 tabular-nums">
        <TurnSpinner v-if="buying" />
        <template v-if="buying">Buying…</template>
        <template v-else-if="affordable">Buy</template>
        <template v-else>{{ item.cost }}g</template>
      </span>
    </button>
  </li>
</template>

<style scoped>
/**
 * A plaque strung from the shop's rail on two ropes, which is why it hangs level where an ad on one
 * tack hangs crooked. `--rope-drop` is owned by `ShopPanel` and read with a fallback, never
 * redeclared: a property set here would win over the inherited one and the gap would stop matching.
 */
.plaque {
  position: relative;
  border-radius: 3px;
  background-color: var(--color-oak);
  /* Symmetric bands, so the grain costs the surface's luminance nothing and stays measurable.
     Unequal spacing over a 41px cycle: a constant pitch reads as ruled paper rather than timber.
     The noise underneath breaks the repeat, and only darkens, so it is one faint pass. */
  background-image:
    repeating-linear-gradient(
      0deg,
      oklch(100% 0 0 / 0.5) 0 1px,
      transparent 1px 7px,
      oklch(58% 0.05 66 / 0.14) 7px 8px,
      transparent 8px 15px,
      oklch(100% 0 0 / 0.45) 15px 16px,
      transparent 16px 24px,
      oklch(58% 0.05 66 / 0.18) 24px 26px,
      transparent 26px 33px,
      oklch(100% 0 0 / 0.4) 33px 34px,
      transparent 34px 41px
    ),
    var(--parchment-grain);
  background-size:
    auto,
    260px 17px;
  background-blend-mode: normal, multiply;
  box-shadow:
    inset 0 1px 0 oklch(100% 0 0 / 0.5),
    inset 0 -1px 0 oklch(30% 0.03 55 / 0.28),
    inset 0 0 0 1px oklch(52% 0.05 66 / 0.55),
    0 2px 4px oklch(30% 0.028 52 / 0.35);
  /* The pivot is where the ropes meet the rail: a swing about its own edge would be it bending. */
  transform-origin: 50% calc(-1 * var(--rope-drop, 20px));
}

/* Two ropes as one strip, so the pair cannot drift apart. The gradient runs across each cord: a
   bright core between two dark edges is what makes a cylinder out of a line. */
.plaque::before {
  content: '';
  position: absolute;
  top: calc(-1 * var(--rope-drop, 20px));
  right: 1.5rem;
  left: 1.5rem;
  height: var(--rope-drop, 20px);
  background-image:
    linear-gradient(90deg, oklch(42% 0.04 72), oklch(72% 0.05 82) 45%, oklch(40% 0.035 70)),
    linear-gradient(90deg, oklch(42% 0.04 72), oklch(72% 0.05 82) 45%, oklch(40% 0.035 70));
  background-repeat: no-repeat, no-repeat;
  background-position:
    left top,
    right top;
  background-size:
    3px 100%,
    3px 100%;
}

/**
 * Unaffordable, and readable while it says so. One dim, on the row: nested opacity multiplies, and
 * a second on the stud once put the price at 1.7:1. The value is measured — this composites over a
 * near-black frame, so it darkens the ground rather than only washing the row out. 0.85 was legible
 * but indistinguishable from an affordable row; 0.72 separates them by 0.27 in luminance, and is
 * affordable only because the row stops muting its own text at the same time.
 */
.is-dimmed {
  opacity: 0.72;
}
</style>
