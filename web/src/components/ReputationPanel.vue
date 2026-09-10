<script setup lang="ts">
import AppIcon from './AppIcon.vue'
import TurnSpinner from './TurnSpinner.vue'
import { crestArt, type Faction } from '../assets/artwork'
import type { ReputationView } from '../api/types'

const props = defineProps<{
  reputation: ReputationView | null
  disabled: boolean
  scouting: boolean
}>()
defineEmits<{ scout: [] }>()

const FACTIONS: { id: Faction; label: string }[] = [
  { id: 'people', label: 'People' },
  { id: 'state', label: 'State' },
  { id: 'underworld', label: 'Underworld' },
]

/**
 * Signed and to one decimal: standing moves in fractions, so a rounded integer would show a
 * faction turning against you as a flat zero. The sign comes back separately so it can be set apart.
 */
const reading = (faction: Faction): { sign: string; figure: string } => {
  const value = props.reputation?.[faction] ?? 0
  return {
    sign: value > 0 ? '+' : value < 0 ? '-' : '',
    figure: Math.abs(value).toFixed(1),
  }
}
</script>

<template>
  <section aria-labelledby="standing-heading" class="flex flex-col gap-3">
    <!-- Nothing is ever read against timber, so everything on the wall itself is on a plate. -->
    <div class="flex items-baseline justify-between gap-3">
      <h2
        id="standing-heading"
        class="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-accent"
      >
        <AppIcon name="standing" :size="16" />
        Standing
      </h2>
      <!-- Opaque rather than the usual 60%, and disabled dims only the label: this is the one
           control with the painting directly behind it, a ground running 0.16 to 0.55. -->
      <button
        type="button"
        class="relief rounded-md border border-ink-muted/40 bg-surface-raised px-2.5 py-1 text-xs font-semibold text-ink enabled:hover:border-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:text-ink-muted disabled:shadow-none"
        :disabled="disabled || scouting"
        @click="$emit('scout')"
      >
        <span class="flex items-center gap-1.5">
          <TurnSpinner v-if="scouting" :size="12" />
          {{ scouting ? 'Scouting…' : 'Send scouts' }}
        </span>
      </button>
    </div>

    <div class="wall timber">
      <!-- Named as well as drawn: crests alone would put the reading on colour and shape. -->
      <div v-if="reputation" class="mounts">
        <div v-for="faction in FACTIONS" :key="faction.id" class="mount">
          <span class="shield">
            <span class="nail nail-left" aria-hidden="true" />
            <span class="nail nail-right" aria-hidden="true" />
            <img
              :src="crestArt(faction.id)"
              alt=""
              aria-hidden="true"
              width="36"
              height="43"
              loading="lazy"
              decoding="async"
            />
          </span>
          <!-- One `dl` per plate: a description list allows one wrapper per pair, which the
               shield above the plate has already spent. -->
          <dl class="oak-plate plate">
            <dt class="truncate text-xs">{{ faction.label }}</dt>
            <!-- The sign is out of the flow so the figure is what centres. Reading order is
                 untouched. -->
            <dd class="text-sm font-semibold tabular-nums">
              <span class="reading"
                ><span class="sign">{{ reading(faction.id).sign }}</span
                >{{ reading(faction.id).figure }}</span
              >
            </dd>
          </dl>
        </div>
      </div>

      <!-- The fixings without the shields: an unscouted game is not a reading of zero, and three
           empty mounts say so without a number in sight. -->
      <template v-else>
        <div class="mounts" aria-hidden="true">
          <div v-for="n in FACTIONS.length" :key="n" class="mount">
            <span class="shield">
              <span class="nail nail-left" />
              <span class="nail nail-right" />
              <span class="vacant" />
            </span>
          </div>
        </div>
        <p class="oak-plate notice">
          Nobody has scouted this dragon's reputation yet. Sending scouts costs a turn and ages
          every ad by one — but it is the only move that cannot cost a life.
        </p>
      </template>
    </div>
  </section>
</template>

<style scoped>
/**
 * The wall the crests are nailed to. `timber` is the surface, shared with the board and the
 * shopfront; this sets what is particular. Nailed flat is the difference — an ad hangs from one
 * tack and tilts, a plaque hangs from two ropes and is level, a shield does not hang at all.
 */
.wall {
  padding: 1.125rem 0.875rem 1rem;
}

.mounts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  justify-items: center;
  gap: 0.625rem;
  margin: 0;
}

/* Capped for the widths in between: at 20rem and at phone width the cell is already under this,
   but a single column can run to 600px and three plates would stretch to match. */
.mount {
  display: flex;
  width: 100%;
  min-width: 0;
  max-width: 8rem;
  flex-direction: column;
  align-items: center;
}

/* Sized by the drawing, so swapping the SVGs for rasters at the same stems changes nothing here. */
.shield {
  position: relative;
  display: block;
  padding-top: 2px;
}

.shield img {
  display: block;
  height: 2.75rem;
  width: auto;
}

/** A nail head, one per shoulder. Round because a shadow follows the border box, not the paint. */
.nail {
  position: absolute;
  top: 0;
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  background-image: radial-gradient(
    circle at 35% 30%,
    oklch(92% 0.07 88),
    var(--color-brass) 60%,
    oklch(58% 0.08 80)
  );
  box-shadow: 0 1px 1px oklch(18% 0.02 50 / 0.6);
}

.nail-left {
  left: 6px;
}

.nail-right {
  right: 6px;
}

/**
 * The mount with no shield on it: the inverse of `relief`, so it reads as a hollow. The outline is
 * sampled off the shared shield path — rounded corners read as pockets sewn onto the wall, and the
 * taper is what makes it shield-shaped. Percentages so it survives a change of crest size.
 */
.vacant {
  display: block;
  height: 2.75rem;
  /* The crest's own aspect, so a filled mount and an empty one are the same size. */
  width: calc(2.75rem * 64 / 76);
  background-color: oklch(50% 0.022 66);
  box-shadow:
    inset 0 3px 5px oklch(18% 0.02 50 / 0.6),
    inset 0 1px 0 oklch(18% 0.02 50 / 0.55);
  clip-path: polygon(
    7.8% 6.6%,
    92.2% 6.6%,
    92.2% 50%,
    88.6% 65.1%,
    79% 77.1%,
    65.5% 86.4%,
    50% 93.4%,
    34.5% 86.4%,
    21% 77.1%,
    11.4% 65.1%,
    7.8% 50%
  );
}

/** Stock, cut and rim come from `oak-plate`; this is only how one is hung and set. */
.plate {
  display: block;
  margin-bottom: 0;
  margin-top: 0.5rem;
  width: 100%;
  padding: 0.1875rem 0.25rem 0.25rem;
  text-align: center;
}

/**
 * The figure centres on itself, not on the string. Tabular figures do not cover the signs, and a
 * plus is four pixels wider than a minus, so three plates put their digits in three places.
 */
.reading {
  position: relative;
  display: inline-block;
}

.sign {
  position: absolute;
  right: 100%;
  margin-right: 0.5px;
}

/** The same plate as the readings: a paragraph on bare timber is text on an unmeasured ground. */
.notice {
  margin: 0.75rem 0 0;
  padding: 0.5rem 0.625rem;
  font-size: 0.8125rem;
  line-height: 1.4;
}
</style>
