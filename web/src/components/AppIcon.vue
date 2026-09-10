<script setup lang="ts">
import { computed } from 'vue'
import { iconArt, type IconName } from '../assets/artwork'

/**
 * A chrome mark, always decorative: every icon sits beside the word it stands for, so a description
 * would make a screen reader read the same noun twice. The palette is baked into each file rather
 * than inherited — these are pictures in the game's hand, not glyphs.
 *
 * `size` writes the width and height *attributes*, not an inline style: a style would beat a
 * `size-*` utility and silently ignore a caller scaling an icon across a breakpoint.
 */
const props = withDefaults(defineProps<{ name: IconName; size?: number }>(), { size: 18 })

const src = computed(() => iconArt(props.name))
</script>

<template>
  <img
    :src="src"
    alt=""
    aria-hidden="true"
    :width="size"
    :height="size"
    loading="lazy"
    decoding="async"
    class="shrink-0"
  />
</template>
