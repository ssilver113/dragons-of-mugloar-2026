<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  version: string | null
  builtAt: string | null
}>()

/**
 * One fixed format rather than the reader's locale. Nothing else in the app is localised, and a
 * stamp that reads differently on every machine is a worse stamp than one that always reads the
 * same. An unparseable instant is dropped rather than printed as `Invalid Date`.
 */
const built = computed(() => {
  if (props.builtAt === null) {
    return null
  }
  const at = new Date(props.builtAt)
  if (Number.isNaN(at.getTime())) {
    return null
  }
  return at.toLocaleDateString('en-GB', { day: 'numeric', month: 'long', year: 'numeric' })
})
</script>

<template>
  <!-- The one contentinfo landmark, printed on the painting rather than on paper. It cannot share
       the abandon block's sheet: that block exists only during a game, and two stacked sheets read
       as one panel broken in half. The collar below is what keeps it legible without one. -->
  <footer
    class="colophon flex flex-wrap items-baseline justify-center gap-x-4 text-center text-sm font-medium text-ink"
  >
    <span>
      Dragons of Mugloar
      <span v-if="version" class="font-semibold">v{{ version }}</span>
    </span>
    <!--
      Kept out of the sentence above so a narrow screen wraps between the two facts rather than
      inside either of them. Separated by space alone: a middot set between them survives the one
      line but leads the second one once the footer wraps at 375px, which is worse than the gap it
      saves.
    -->
    <time v-if="built" :datetime="builtAt ?? undefined">{{ built }}</time>
  </footer>
</template>

<style scoped>
/**
 * The collar, and why this line can sit on the backdrop when no other copy does. The painting runs
 * 0.16 to 0.55 in luminance and takes muted ink to 1.5:1, which no scrim fixes, so the ground is
 * brought to the glyph: a stroke of the app's own paper, against which ink measures 9.86:1.
 *
 * `paint-order` is what makes it a collar rather than a hollowing-out — without it the stroke is
 * painted over the fill and eats the letterform from both sides. The `@supports` gate matters
 * because that failure is worse than no stroke at all; the fallback ring is the same idea built
 * from shadows, which paint behind the glyph by definition. The soft pass does no contrast work
 * and only stops the collar reading as a cut-out where the backdrop goes dark.
 */
.colophon {
  text-shadow:
    1px 0 0 var(--color-surface),
    -1px 0 0 var(--color-surface),
    0 1px 0 var(--color-surface),
    0 -1px 0 var(--color-surface),
    1px 1px 0 var(--color-surface),
    -1px 1px 0 var(--color-surface),
    1px -1px 0 var(--color-surface),
    -1px -1px 0 var(--color-surface),
    0 0 8px color-mix(in oklab, var(--color-surface) 70%, transparent);
}

@supports (paint-order: stroke) {
  .colophon {
    paint-order: stroke fill;
    -webkit-text-stroke: 4px var(--color-surface);
    text-shadow: 0 0 8px color-mix(in oklab, var(--color-surface) 70%, transparent);
  }
}
</style>
