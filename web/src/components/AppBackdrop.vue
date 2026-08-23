<script setup lang="ts">
import { backdropArt } from '../assets/artwork'
</script>

<template>
  <!--
    Decorative and inert: it sits behind everything, is never read out, and never intercepts a
    click. The scrim above it is what guarantees the text keeps its measured contrast — the plate
    is already pale, but "pale enough" is not something to leave to an image, and the panels above
    it are parchment on parchment.
  -->
  <div class="pointer-events-none fixed inset-0 -z-10 overflow-hidden" aria-hidden="true">
    <img :src="backdropArt" alt="" class="h-full w-full object-cover" />
    <div class="absolute inset-0 bg-surface/45"></div>
  </div>
</template>
