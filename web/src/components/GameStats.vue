<script setup lang="ts">
import { ref, watch } from 'vue'
import StatTile from './StatTile.vue'
import type { GameView } from '../api/types'

const props = defineProps<{ game: GameView }>()

/**
 * A level is the only figure here worth marking as it moves. Score and gold change most turns and
 * the turn counter changes every one of them, so flashing those would be a light that is always
 * on; a level is bought deliberately, a few times a game, and is what the whole ad scale is read
 * against. It is a flourish and nothing more — the number itself is what says what happened.
 */
const levelledUp = ref(false)
let clear: ReturnType<typeof setTimeout> | undefined

watch(
  () => props.game.level,
  (now, before) => {
    if (now <= before) {
      return
    }
    levelledUp.value = true
    clearTimeout(clear)
    clear = setTimeout(() => (levelledUp.value = false), 1200)
  },
)
</script>

<template>
  <!-- Announced as a whole, so a turn does not read out five separate number changes. -->
  <dl class="grid grid-cols-3 gap-2 sm:grid-cols-5" aria-label="Dragon status" aria-live="polite">
    <StatTile label="Score" :value="game.score" />
    <StatTile label="Gold" :value="game.gold" />
    <StatTile label="Lives" :value="game.lives" />
    <StatTile label="Level" :value="game.level" :emphasis="levelledUp" />
    <StatTile label="Turn" :value="game.turn" />
  </dl>
</template>
