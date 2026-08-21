<script setup lang="ts">
defineProps<{ label: string; value: number | string }>()
</script>

<template>
  <div class="rounded-lg bg-surface-raised px-3 py-2">
    <dt class="text-xs uppercase tracking-wide text-ink-muted">{{ label }}</dt>
    <dd class="text-lg font-semibold tabular-nums sm:text-xl">{{ value }}</dd>
  </div>
</template>
