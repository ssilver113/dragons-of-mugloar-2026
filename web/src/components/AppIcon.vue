<script setup lang="ts">
import { computed } from 'vue'
import { iconArt, type IconName } from '../assets/artwork'

/**
 * A chrome mark, always decorative. Every icon in this app sits beside the word it stands for —
 * a stat's label, a section's heading, a tab's name — so giving it a description would only make
 * a screen reader read the same noun twice. The one place a mark carries meaning on its own is
 * the row of hearts, and that row states its count in text of its own.
 *
 * The palette is baked into the file rather than inherited, as it is for the crests and the item
 * drawings: these are pictures in the game's hand, not glyphs tinted by whatever they sit next to.
 *
 * `size` sets the width and height *attributes*, which reserve the box before the file arrives and
 * are what the icon renders at by default. It is deliberately not an inline style: a style would
 * win over a `size-*` utility, so a caller that wanted to scale an icon across a breakpoint would
 * be silently ignored.
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
