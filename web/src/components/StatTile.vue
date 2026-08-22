<script setup lang="ts">
defineProps<{ label: string; value: number | string; emphasis?: boolean }>()
</script>

<template>
  <div class="rounded-lg bg-surface-raised px-3 py-2" :class="{ flourish: emphasis }">
    <dt class="font-display text-xs uppercase tracking-wide text-ink-muted">{{ label }}</dt>
    <dd class="text-lg font-semibold tabular-nums sm:text-xl">{{ value }}</dd>
  </div>
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
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
