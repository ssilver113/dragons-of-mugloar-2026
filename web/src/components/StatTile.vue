<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import type { IconName } from '../assets/artwork'

defineProps<{
  label: string
  value: number | string
  icon: IconName
  emphasis?: boolean
}>()
</script>

<template>
  <div class="panel px-1.5 py-1 sm:px-3 sm:py-2" :class="{ flourish: emphasis }">
    <!--
      Two sizes, and the small one is what lets the strip pin to the top of a phone: five tiles have
      to share 375px there, so the label drops a step and the padding halves. Truncated rather than
      wrapped, because a label on two lines would make the bar a different height on every screen.
    -->
    <dt
      class="truncate font-display text-[0.625rem] uppercase tracking-wide text-ink-muted sm:text-xs"
    >
      {{ label }}
    </dt>
    <!--
      The mark sits with the figure rather than with the label: at the figure's size it is a thing
      to be read, and it leaves the Lives tile free to draw its hearts here instead of carrying a
      heart in both rows.

      It is also the first thing to go on a phone. Five tiles share 375px there, which leaves
      fifty-two pixels inside each one, and a mark and its gap take eighteen of them — enough to
      clip a five-figure score against the tile it is drawn in. The label above already names the
      tile, so the mark is the half that can be spared and the figure is the half that cannot.

      The slot is what Lives fills. It is provided conditionally, so a count too large to draw
      falls through to the fallback and reads exactly like every other tile.
    -->
    <dd
      class="flex h-6 items-center gap-1 text-sm font-semibold tabular-nums sm:h-8 sm:gap-2 sm:text-xl"
    >
      <slot name="figure">
        <AppIcon :name="icon" :size="20" class="hidden size-5 sm:block" />
        <span>{{ value }}</span>
      </slot>
    </dd>
  </div>
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  /* On the element now rather than on a pseudo-element: a panel draws its own surface, where
     parchment drew the sheet behind itself and this had to reach for it. */
  .flourish {
    animation: flourish 1.2s ease-out;
  }
}

/* Only `from` is declared, so the tile settles back onto whatever its resting style is rather
   than onto a copy of it that would then have to be kept in step. */
@keyframes flourish {
  from {
    background-color: var(--color-accent);
  }
}
</style>
