<template>
  <!--
    The tear itself: fractal noise pushing a sheet's outline around, three seeds so a column of
    cards does not repeat. Mounted once at the root because `filter: url(#…)` resolves against the
    document, not against the element's own tree.

    Sized to nothing and hidden by `overflow` rather than by `display: none`. A display-none SVG
    still serves its filters in Chrome and Firefox, and has historically not in WebKit — the cost
    of not relying on that is one line.

    Two frequencies per filter. The low one gives the slow wave of a sheet pulled apart; the
    octaves above it give the fibre chatter that stops the wave reading as a wobble. `sRGB`
    interpolation because the noise is being used as a coordinate offset rather than as colour,
    and linear light would skew the distribution towards one side.
  -->
  <svg
    class="pointer-events-none absolute size-0 overflow-hidden"
    aria-hidden="true"
    focusable="false"
  >
    <defs>
      <filter
        v-for="seed in SEEDS"
        :id="`deckle-${seed.id}`"
        :key="seed.id"
        x="-8%"
        y="-16%"
        width="116%"
        height="132%"
        color-interpolation-filters="sRGB"
      >
        <feTurbulence
          type="fractalNoise"
          :baseFrequency="seed.frequency"
          numOctaves="3"
          :seed="seed.seed"
          result="fibre"
        />
        <feDisplacementMap
          in="SourceGraphic"
          in2="fibre"
          :scale="seed.scale"
          xChannelSelector="R"
          yChannelSelector="G"
        />
      </filter>
    </defs>
  </svg>
</template>

<script setup lang="ts">
/**
 * Three tears rather than one. The frequencies differ as well as the seeds, so the sheets do not
 * merely start the same noise at different offsets — one is torn coarsely, one finely, one in
 * between, which is what a stack of hand-cut paper actually looks like.
 */
const SEEDS = [
  { id: 'a', seed: 3, frequency: '0.009 0.015', scale: 11 },
  { id: 'b', seed: 17, frequency: '0.013 0.010', scale: 9 },
  { id: 'c', seed: 41, frequency: '0.007 0.018', scale: 13 },
] as const
</script>
