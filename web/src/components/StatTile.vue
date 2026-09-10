<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import type { IconName } from '../assets/artwork'

defineProps<{
  label: string
  value: number | string
  icon: IconName
  emphasis?: boolean
}>()
</script>

<template>
  <!--
    A gauge: a window sunk into the hide, rimmed in brass, with the figure lit behind the glass.
    The rim is deliberately a worn brass rather than the shop's — see the rule below.
  -->
  <div class="gauge" :class="{ struck: emphasis }">
    <!--
      Engraved into the metal below the window rather than printed on the glass, which is where a
      dial carries its legend. One size on a phone and one from `sm` up: five gauges share 375px
      there, so the legend drops a step and the padding halves. Truncated rather than wrapped,
      because a label on two lines would make the bar a different height on every screen.
    -->
    <dt class="legend">{{ label }}</dt>
    <!-- The mark sits with the figure, which leaves the Lives gauge free to draw its hearts here
         rather than carry one in both rows. It survives on a phone because the figure and the mark
         both drop a step below `sm`. The slot is what Lives fills, conditionally, so a count too
         large to draw falls through and reads like every other gauge. -->
    <dd class="reading">
      <slot name="figure">
        <AppIcon :name="icon" :size="20" class="size-2.5 sm:size-5" />
        <span>{{ value }}</span>
      </slot>
    </dd>
  </div>
</template>

<style scoped>
/**
 * The window, and the one place a figure is read against a dark ground. Cut into the hide rather
 * than laid on it: the inverse of `relief`, so it reads as a hollow.
 *
 * Measured against the lightest ground the type can land on, not the base colour — the highlight
 * is painted at full strength exactly where the legend sits, so the base would flatter both
 * numbers. Legend 6.08:1, figure 10.64:1, against a palette floor of 4.94.
 */
.gauge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.0625rem;
  min-width: 0;
  overflow: hidden;
  padding: 0.1875rem 0.125rem 0.25rem;
  border-radius: 3px;
  background-color: oklch(25% 0.02 45);
  background-image: radial-gradient(80% 100% at 50% 0%, oklch(33% 0.03 50 / 0.8), transparent 74%);
  /* The rim is a worn brass, six lightness points and three chroma points below the token. The
     shop's Buy fitting and the solver's nameplate are the app's brass, and both mean "this is the
     thing you press"; five rims at that strength, pinned to the top of the window for the whole
     game, would outshout the one control that has to keep the meaning. Dimming them is what lets
     the strip be made of metal without spending the metal's job. */
  box-shadow:
    inset 0 0 0 1px oklch(62% 0.07 84),
    inset 0 2px 5px oklch(10% 0.012 40 / 0.75),
    0 1px 0 oklch(72% 0.06 84 / 0.28);
}

@media (width >= 40rem) {
  .gauge {
    gap: 0.125rem;
    padding: 0.3125rem 0.5rem 0.375rem;
  }
}

.legend {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-display);
  font-size: 0.5625rem;
  /* Tight, because the legend is a struck line rather than a paragraph and the bar's whole height
     is a budget: the strip is pinned to the top of the window at every width, and every pixel it
     takes is a pixel of board that is never on screen. */
  line-height: 1.25;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: oklch(76% 0.045 82);
  /* Struck into the metal: one dark pixel below the face, which is the shallowest engraving that
     survives at nine pixels. The nameplate does the same thing the other way up, because there the
     metal is light and the ink is dark. */
  text-shadow: 0 1px 0 oklch(14% 0.012 40);
}

.reading {
  display: flex;
  align-items: center;
  gap: 0.125rem;
  height: 1.125rem;
  /* Clipped at the rim rather than allowed past it, which is the safety net and not the plan. The
     plan is that it never fires: five gauges share 343px on a phone, and the mark, the gap and the
     side padding were each shaved a step until a six-figure score measured 58px inside 59. That
     figure is the bound the strip was designed against before the marks were brought back, and it
     is worth keeping even though no game this one has played has come near it. */
  min-width: 0;
  margin: 0;
  font-size: 0.8125rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: oklch(93% 0.03 86);
  text-shadow: 0 1px 0 oklch(14% 0.012 40 / 0.9);
}

@media (width >= 40rem) {
  .legend {
    font-size: 0.75rem;
  }

  .reading {
    gap: 0.375rem;
    height: 1.75rem;
    font-size: 1.25rem;
  }
}

/**
 * The lamp. Each mark bakes its own dark ink and cannot be re-tinted, so against this window they
 * would disappear. `drop-shadow` follows the painted pixels, making a rim rather than a square.
 * `:deep` because the hearts are slotted in by the parent and carry its scope.
 */
.reading :deep(img) {
  filter: drop-shadow(0 0 2px oklch(90% 0.06 84 / 0.85))
    drop-shadow(0 0 4px oklch(84% 0.07 82 / 0.5));
}

@media (prefers-reduced-motion: no-preference) {
  .struck {
    animation: struck 1.2s ease-out;
  }
}

/* Only `from` is declared, so the gauge settles back onto whatever its resting style is rather
   than onto a copy of it that would then have to be kept in step. The rim comes up to full brass
   and the window lights from within — a dial struck, rather than a tile briefly painted a
   different colour, which is what this replaced. */
@keyframes struck {
  from {
    box-shadow:
      inset 0 0 0 1px oklch(86% 0.1 86),
      inset 0 0 14px oklch(72% 0.09 84 / 0.6),
      0 0 10px oklch(76% 0.1 86 / 0.5);
  }
}
</style>
