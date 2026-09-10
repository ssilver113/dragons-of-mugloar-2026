<script setup lang="ts">
import { computed } from 'vue'
import { dragonArt, type DragonMood } from '../assets/artwork'

const props = defineProps<{ mood: DragonMood; size: number }>()

const src = computed(() => dragonArt(props.mood))
</script>

<template>
  <!-- Decorative: every mood is already stated in words beside it. Keyed on the mood so a change
       remounts and replays the entrance. The poses are cropped to their own outlines and differ in
       shape, so `object-contain` fits each inside the square `size` reserves. -->
  <img
    :key="mood"
    :src="src"
    alt=""
    aria-hidden="true"
    :width="size"
    :height="size"
    decoding="async"
    class="sigil shrink-0 object-contain"
    :class="mood"
  />
</template>

<style scoped>
@media (prefers-reduced-motion: no-preference) {
  .sigil {
    animation: strike 320ms cubic-bezier(0.2, 0.9, 0.3, 1.2);
  }

  /* Only the good news gets a flourish. A defeat that bounced would be tone-deaf, so it fades. */
  .sigil.victorious {
    animation: flare 520ms cubic-bezier(0.2, 0.9, 0.3, 1.2);
  }

  .sigil.defeated {
    animation: fade 600ms ease-out;
  }
}

@keyframes strike {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
}

@keyframes flare {
  from {
    opacity: 0;
    transform: scale(0.7) rotate(-8deg);
  }

  60% {
    transform: scale(1.08) rotate(2deg);
  }
}

@keyframes fade {
  from {
    opacity: 0;
    transform: translateY(-6px);
  }
}
</style>
