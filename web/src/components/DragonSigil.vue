<script setup lang="ts">
import { computed } from 'vue'
import { dragonArt, type DragonMood } from '../assets/artwork'

const props = defineProps<{ mood: DragonMood; size: number }>()

const src = computed(() => dragonArt(props.mood))
</script>

<template>
  <!--
    Decorative. Every mood it can be in is already stated in words next to it — the banner after a
    turn, the heading on the end panel — so describing the picture as well would only make a
    screen reader say the same thing twice.

    Keyed on the mood so a change remounts the image and replays the entrance, rather than
    swapping the source underneath a static element.

    Each pose is cropped to its own outline, so the three differ in shape. `size` reserves the
    square they are laid into and `object-contain` fits the drawing inside it — a pose that is
    wider than it is tall simply leaves the box short, rather than stretching to fill it.
  -->
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
