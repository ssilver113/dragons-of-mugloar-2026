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
      <!--
        The cost moves rather than repeating. A row the player can act on says it here, next to what
        it buys; a row they cannot says it on the stud instead, where the label "Buy" has nothing to
        offer and the price is the only thing left worth reading.
      -->
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
 * A plaque strung from the shop's rail on two ropes.
 *
 * Two ropes rather than one, and that is the whole difference from the ads next to it. A sheet on a
 * single tack hangs crooked, which is why every ad on the board is tilted; a board on two ropes
 * hangs level, so these are not. The same physical logic gives two materials two different resting
 * postures without either needing a rule about it.
 *
 * `--rope-drop` is owned by `ShopPanel`, which uses the same value for the column's gap — the ropes
 * span the gap exactly. It is read with a fallback rather than redeclared here: a custom property
 * set on the element would win over the inherited one, and the shop's gap would silently stop
 * reaching the ropes that are supposed to span it.
 */
.plaque {
  position: relative;
  border-radius: 3px;
  background-color: var(--color-oak);
  /* Figure, in two parts, and the split is a contrast decision rather than a drawing one.
     The bands are symmetric — a light line and a dark line of comparable weight — so they add
     visible grain at no net cost to the surface's luminance, which is the half that has to stay
     measurable.

     The cycle is forty-one pixels with six lines in it at unequal spacing, and the unevenness is
     the point: lines at a constant pitch read as ruled paper rather than as timber, which is a
     material this app is about to use for something else. The stretched noise underneath breaks
     the cycle's own repeat. It only darkens, so it is kept to one faint pass. */
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
  /* The pivot is where the ropes meet the rail, not the plaque's own top edge. A swing about its
     own edge is a plaque bending; a swing about the rail is a plaque hanging. */
  transform-origin: 50% calc(-1 * var(--rope-drop, 20px));
}

/* Two ropes, drawn as one strip: two 3px columns of a single background, so the pair cannot drift
   apart. The gradient runs across each cord rather than down it — a bright core between two dark
   edges is what makes a cylinder out of a line, the same trick the rail uses lying down. Inset from
   the ends rather than at the corners; a rope tied at the very corner of a board would tear it
   out. */
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
 * Unaffordable, and readable while it says so.
 *
 * It used to be `opacity-60` on the row *and* `disabled:opacity-60` on the button. Nested opacity
 * multiplies, so the label the player most needed — how much the thing costs — rendered at 36% and
 * measured about 1.7:1. At the start of a game nothing is affordable, so that was the state the
 * whole shop opened in.
 *
 * One dim now, on the row, and nothing on the stud. The stud loses its brass instead — a stronger
 * signal than fading was, and one that costs the text nothing.
 *
 * The value is measured rather than chosen, and it has been measured three times. Opacity here
 * composites the plaque over the frame behind it, which is nearly black, so dimming does not merely
 * wash the row out — it darkens the ground everything on it is read against. 0.8 put the price at
 * 4.39:1 and 0.85 fixed that, but at 0.85 the row was barely distinguishable from an affordable one
 * at a glance, which is the whole job the dimming exists to do.
 *
 * 0.72 separates them — the faces differ by 0.27 in luminance rather than 0.10 — and it is
 * affordable because the row stops muting its own text at the same time. `ink-muted` is a
 * de-emphasis applied *within* a row; on a row the surface has already de-emphasised, it was being
 * applied twice, and the effect line fell to 3.87:1. One de-emphasis, not two: everything on an
 * unavailable plaque prints in `ink`, and the plaque itself carries the state.
 */
.is-dimmed {
  opacity: 0.72;
}
</style>
