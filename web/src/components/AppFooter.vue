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
       as one panel broken in half. `collar` is what keeps it legible without one. -->
  <footer
    class="collar flex flex-wrap items-baseline justify-center gap-x-4 text-center text-sm font-medium text-ink"
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
