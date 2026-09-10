<script setup lang="ts">
import { backdropArt, backdropSrcset } from '../assets/artwork'
</script>

<template>
  <!--
    Decorative and inert: it sits behind everything, is never read out, and never intercepts a
    click. The scrim above it is what guarantees the text keeps its measured contrast — the plate
    is already pale, but "pale enough" is not something to leave to an image, and the panels above
    it draw their own opaque surface.
  -->
  <div class="pointer-events-none fixed inset-0 -z-10 overflow-hidden" aria-hidden="true">
    <!-- Deprioritised on purpose: the wordmark above it is what the page is measured on, and
         the body already paints an opaque surface, so arriving late costs nothing to read. -->
    <img
      :src="backdropArt"
      :srcset="backdropSrcset"
      sizes="100vw"
      alt=""
      fetchpriority="low"
      decoding="async"
      class="h-full w-full object-cover"
    />
    <div class="absolute inset-0 bg-surface/45"></div>
  </div>
</template>
