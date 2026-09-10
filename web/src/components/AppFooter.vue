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
  <!--
    The page's one contentinfo landmark, and the only thing on it that is about the software rather
    than about the game.

    Printed on the painting rather than on paper. It cannot join the abandon block above it, which
    is the obvious way to spend one sheet instead of two: that block only exists while a game is in
    progress, and this line has to be there on the start screen and after a run has ended. Two
    identical sheets stacked at the foot of the page read as one panel that broke in half, so the
    lower one gives up its sheet instead — see the collar in the style below for how it stays
    readable without one.
  -->
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
 * The collar, and the reason this line can sit on the backdrop when no other copy in the app does.
 *
 * The painting runs from 0.16 to 0.55 in luminance, which crosses the type's own — muted ink
 * measures 1.5:1 against it at worst, and no scrim short of erasing the picture fixes that. So the
 * ground is brought to the glyph instead of the glyph to a sheet: the type is given a stroke of the
 * app's own paper, and inside that stroke it is read against `surface`, where ink measures 9.86:1.
 * Contrast is spent on the collar, so the face is full-strength ink at medium weight rather than
 * the muted grade a panel would have allowed.
 *
 * `paint-order` is what makes this a collar rather than a hollowing-out: without it the stroke is
 * painted over the fill and eats the letterform from both sides. It is centred on the outline, so
 * a 4px stroke shows 2px of paper outside the glyph and the other half is covered by the fill,
 * which keeps the face at its drawn weight.
 *
 * The `@supports` gate is not ceremony. Every browser the bundle targets has `paint-order` on HTML
 * text, but the failure if one did not would be the worst possible one — a light stroke painted
 * straight over dark type, which is thinner and paler than no stroke at all. The eight-offset ring
 * below is what such a browser gets: the same idea at one pixel, built out of shadows, which paint
 * behind the glyph by definition and so cannot do that.
 *
 * The soft pass is in both branches and does no contrast work. It stops the collar reading as a
 * cut-out where the backdrop goes dark, which is what a hard edge alone looks like over the
 * treeline.
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
