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
 * Signed and to one decimal. Standing moves in fractions and can go negative, and a bare rounded
 * integer would show a faction turning against you as a flat zero.
 *
 * The sign is returned apart from the figure so it can be set apart from it. Read back together
 * they are exactly the string this used to build, rounding edges included.
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
    <!--
      The heading and the control stay above the wall, as the board's and the shop's do. Nothing is
      ever read against timber, so everything on the wall itself is on a plate.
    -->
    <div class="flex items-baseline justify-between gap-3">
      <h2
        id="standing-heading"
        class="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-accent"
      >
        <AppIcon name="standing" :size="16" />
        Standing
      </h2>
      <!--
        The app's regular secondary control, with two departures the ground forces. Its surface is
        opaque rather than the usual 60%, and disabled dims the label instead of the whole button:
        this one is the only control in the app with the painting directly behind it, so anything
        translucent is measured against a ground that runs from 0.16 to 0.55.
      -->
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
      <!--
        Named as well as drawn, and the figure is text rather than a bar: three crests alone would
        put the whole reading on colour and shape, which is exactly what the ad scale avoids.
      -->
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
          <!--
            One list per plate rather than one list around all three. A `dl` may hold its pairs
            directly or wrap each in a single `div`, and neither shape has room for both the shield
            above the plate and the plate's own box around the pair — a second wrapper inside the
            first is where it stops being a description list. A plate is a term and its reading, so
            that is what each one is.
          -->
          <dl class="plate">
            <dt class="truncate text-xs">{{ faction.label }}</dt>
            <!--
              The sign is out of the flow, so what gets centred is the figure. Its reading order is
              untouched — a screen reader still says the sign first, and the two together are the
              same string as ever.
            -->
            <dd class="text-sm font-semibold tabular-nums">
              <span class="reading"
                ><span class="sign">{{ reading(faction.id).sign }}</span
                >{{ reading(faction.id).figure }}</span
              >
            </dd>
          </dl>
        </div>
      </div>

      <!--
        The fixings without the shields. An unscouted game is not a reading of nothing, and three
        empty mounts say that without a number in sight — the wall is plainly waiting for something
        that has not arrived. The sentence beside them carries the meaning; the mounts are drawing.
      -->
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
        <p class="notice">
          Nobody has scouted this dragon's reputation yet. Sending scouts costs a turn and ages
          every ad by one — but it is the only move that cannot cost a life.
        </p>
      </template>
    </div>
  </section>
</template>

<style scoped>
/**
 * The wall the three crests are nailed to. Its surface is the `timber` utility, shared with the
 * message board and the shopfront — one material, defined once, so the three cannot drift apart.
 * This rule sets only what is particular to a wall with shields on it.
 *
 * Nailed flat is the whole difference from the two surfaces above it, and it is physics rather
 * than a rule. A sheet on one tack hangs crooked, which is why every ad on the board is tilted; a
 * plaque on two ropes hangs level, which is why the shop's are not; a shield driven onto two nails
 * does not hang at all. Three ways of fixing something to wood, three resting postures, no
 * component needing to know about the others.
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

/* Capped, because a plate is two short words and the column it sits in is not always narrow. At
   the widths that matter — 20rem beside the shop, and the full width of a phone — the cell is
   already under this and the cap does nothing. It earns its place in between, where a single
   column can run to 600px and three plates would otherwise stretch to match. */
.mount {
  display: flex;
  width: 100%;
  min-width: 0;
  max-width: 8rem;
  flex-direction: column;
  align-items: center;
}

/* The crest and its fixings. Sized by the drawing rather than fixed, so replacing the SVG set with
   rasters at the same stems changes nothing here. */
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

/**
 * A nail head, one at each shoulder.
 *
 * Round, and that matters for more than the drawing: a shadow is cast by the border box whatever
 * is actually painted, so an L-shaped or part-bordered fixing throws the shadow of a whole square
 * and leaves stray dark lines where nothing was drawn. A circle with a circular shadow cannot.
 */
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
 * The mount with no shield on it: the recess the crest would sit in, cut into the wood. Dark at
 * the top and light along the bottom, which is the inverse of `relief` and so reads as a hollow
 * rather than as a boss.
 *
 * The outline is the crest's own, sampled off the shared shield path — square shoulders, straight
 * sides to half height, then a taper to a point. Rounded corners were tried first and read as three
 * pockets sewn onto the wall; the taper is what makes the hollow shield-shaped, and it is the whole
 * reason the empty state says what it says. Percentages rather than a path, so the shape follows
 * the box if the crest is ever drawn at another size.
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

/**
 * The plate under a shield, and the only surface here that carries text. `oak` is the shop's milled
 * face and is bounded against the measured palette, so ink on it is at least as good as ink on the
 * page; the wood behind it is not, and never has to be.
 */
.plate {
  display: block;
  margin-bottom: 0;
  margin-top: 0.5rem;
  width: 100%;
  border-radius: 2px;
  padding: 0.1875rem 0.25rem 0.25rem;
  background-color: var(--color-oak);
  background-image: var(--parchment-grain);
  background-size: 260px 17px;
  background-blend-mode: multiply;
  box-shadow:
    inset 0 1px 0 oklch(100% 0 0 / 0.5),
    inset 0 0 0 1px oklch(52% 0.05 66 / 0.55),
    0 2px 3px oklch(18% 0.02 50 / 0.45);
  text-align: center;
}

/**
 * The figure, centred on itself rather than on the string it belongs to.
 *
 * Centring the whole reading was already exact — measured, the gaps either side agreed to a
 * hundredth of a pixel — and still looked wrong, because a plus is four pixels wider than a minus
 * even under tabular figures, which the signs are not. Three plates side by side therefore put
 * their digits in three different places, and a row that ragged reads as badly centred whatever
 * the box says.
 *
 * Lifting the sign out of the flow fixes the row rather than the cell: every reading now centres on
 * its digits, so `+2.0`, `-0.5` and `0.0` land identically and only a change of magnitude moves
 * anything. The sign hangs off the leading edge, which is where it already appeared to sit.
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

/**
 * The notice posted under the empty mounts. The same milled face as the plates, because it is the
 * same thing: the one surface on this wall that carries words. A paragraph set straight on the
 * timber would be text on a ground nothing has measured.
 */
.notice {
  margin: 0.75rem 0 0;
  border-radius: 2px;
  padding: 0.5rem 0.625rem;
  background-color: var(--color-oak);
  background-image: var(--parchment-grain);
  background-size: 260px 17px;
  background-blend-mode: multiply;
  box-shadow:
    inset 0 1px 0 oklch(100% 0 0 / 0.5),
    inset 0 0 0 1px oklch(52% 0.05 66 / 0.55),
    0 2px 3px oklch(18% 0.02 50 / 0.45);
  font-size: 0.8125rem;
  line-height: 1.4;
}
</style>
