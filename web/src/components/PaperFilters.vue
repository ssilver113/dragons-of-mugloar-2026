<template>
  <!-- The tear: fractal noise pushing a sheet's outline around, three seeds so a column does not
       repeat. Mounted at the root because `filter: url(#…)` resolves against the document.

       Hidden by `overflow`, not `display: none` — a display-none SVG has historically not served
       its filters in WebKit. Two frequencies per filter: the low one is the slow wave of a sheet
       pulled apart, the octaves the fibre chatter that stops it reading as a wobble. `sRGB`
       because the noise is a coordinate offset, and linear light would skew the distribution. -->
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
