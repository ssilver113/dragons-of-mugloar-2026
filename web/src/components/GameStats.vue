<script setup lang="ts">
import StatTile from './StatTile.vue'
import type { GameView } from '../api/types'

defineProps<{ game: GameView }>()
</script>

<template>
  <!-- Announced as a whole, so a turn does not read out five separate number changes. -->
  <dl
    class="grid grid-cols-3 gap-2 sm:grid-cols-5"
    aria-label="Dragon status"
    aria-live="polite"
  >
    <StatTile label="Score" :value="game.score" />
    <StatTile label="Gold" :value="game.gold" />
    <StatTile label="Lives" :value="game.lives" />
    <StatTile label="Level" :value="game.level" />
    <StatTile label="Turn" :value="game.turn" />
  </dl>
</template>
