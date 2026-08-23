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
  <div class="parchment px-3 py-2" :class="{ flourish: emphasis }">
    <dt class="truncate font-display text-xs uppercase tracking-wide text-ink-muted">
      {{ label }}
    </dt>
    <!--
      The mark sits with the figure rather than with the label: at the figure's size it is a thing
      to be read, and it leaves the Lives tile free to draw its hearts here instead of carrying a
      heart in both rows.

      The slot is what Lives fills. It is provided conditionally, so a count too large to draw
      falls through to the fallback and reads exactly like every other tile.
    -->
    <dd class="flex h-8 items-center gap-2 text-lg font-semibold tabular-nums sm:text-xl">
      <slot name="figure">
        <AppIcon :name="icon" :size="20" class="size-5" />
        <span>{{ value }}</span>
      </slot>
    </dd>
  </div>
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  .flourish::before {
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
