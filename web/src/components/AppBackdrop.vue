<script setup lang="ts">
import { backdropArt, fogArt } from '../assets/artwork'
</script>

<template>
  <!--
    Decorative and inert: it sits behind everything, is never read out, and never intercepts a
    click. The scrim above it is what guarantees the text keeps its measured contrast — the plate
    itself is dark, but "dark enough" is not something to leave to an image.
  -->
  <div class="pointer-events-none fixed inset-0 -z-10 overflow-hidden" aria-hidden="true">
    <img :src="backdropArt" alt="" class="h-full w-full object-cover opacity-70" />
    <img :src="fogArt" alt="" class="drift absolute inset-x-0 bottom-1/4 h-1/3 w-full opacity-50" />
    <div class="absolute inset-0 bg-surface/70"></div>
  </div>
</template>

<style scoped>
/* Slow enough to be atmosphere rather than movement: one pass takes two minutes, and the fog
   never travels further than a fifth of the viewport. Off entirely when motion is unwelcome. */
@media (prefers-reduced-motion: no-preference) {
  .drift {
    animation: drift 120s ease-in-out infinite alternate;
  }
}

@keyframes drift {
  from {
    transform: translateX(-8%);
  }
  to {
    transform: translateX(8%);
  }
}
</style>
