<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 14 })
</script>

<template>
  <!--
    Decorative, and deliberately so: it appears only inside a control whose label has already
    changed to say what is happening — "Solving…", "Buying…" — so the wait is stated in words
    whether or not the ring is seen. Under `reduce` the animation rule does not exist and what is
    left is a static gold arc, which is why the arc is drawn open rather than as a full ring.
  -->
  <span
    class="turn-spinner inline-block shrink-0 rounded-full border-2 border-current border-t-transparent"
    aria-hidden="true"
    :style="{ width: `${size}px`, height: `${size}px` }"
  />
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  .turn-spinner {
    animation: turn-spinner 720ms linear infinite;
  }
}

@keyframes turn-spinner {
  to {
    transform: rotate(1turn);
  }
}
</style>
