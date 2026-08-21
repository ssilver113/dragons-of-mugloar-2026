<script setup lang="ts">
import { computed } from 'vue'
import AdList from './components/AdList.vue'
import GameStats from './components/GameStats.vue'
import MessageBanner from './components/MessageBanner.vue'
import { useGameStore } from './stores/game'

const store = useGameStore()

const starting = computed(() => store.startStatus === 'pending')
const outcome = computed(() => store.lastOutcome)
</script>

<template>
  <main class="mx-auto flex min-h-dvh max-w-4xl flex-col gap-6 px-4 py-8">
    <header class="flex flex-col gap-1">
      <h1 class="text-2xl font-semibold text-accent sm:text-3xl">Dragons of Mugloar</h1>
      <p class="text-sm text-ink-muted">
        Take the jobs your dragon can survive. Every action costs a turn.
      </p>
    </header>

    <GameStats v-if="store.game" :game="store.game" />

    <MessageBanner
      v-if="store.error"
      tone="error"
      :title="store.error.code === 'SESSION_EXPIRED' ? 'Game lost' : 'Something went wrong'"
      dismissible
      @dismiss="store.dismissError()"
    >
      {{ store.error.message }}
    </MessageBanner>

    <template v-if="!store.started">
      <section class="flex flex-col items-start gap-4 rounded-lg border border-ink-muted/20 p-6">
        <p class="text-ink-muted">
          Start a game to draw a board of ten jobs, each scored for your dragon's level.
        </p>
        <button
          type="button"
          class="rounded-md bg-accent px-4 py-2 font-semibold text-surface hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="starting"
          @click="store.startGame()"
        >
          {{ starting ? 'Starting…' : 'Start a game' }}
        </button>
      </section>
    </template>

    <template v-else-if="store.finished">
      <section
        class="flex flex-col items-start gap-4 rounded-lg border border-ink-muted/20 p-6"
        role="status"
      >
        <div>
          <h2 class="text-lg font-semibold">The dragon has fallen</h2>
          <p class="text-ink-muted">
            Final score {{ store.game?.score }} after {{ store.game?.turn }} turns.
          </p>
        </div>
        <button
          type="button"
          class="rounded-md bg-accent px-4 py-2 font-semibold text-surface hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="starting"
          @click="store.startGame()"
        >
          {{ starting ? 'Starting…' : 'Play again' }}
        </button>
      </section>
    </template>

    <template v-else>
      <MessageBanner
        v-if="outcome"
        :tone="outcome.success ? 'success' : 'failure'"
        :title="outcome.success ? 'Mission accomplished' : 'Mission failed'"
      >
        {{ outcome.message }}
      </MessageBanner>

      <AdList
        :ads="store.orderedAds"
        :status="store.boardStatus"
        :solving-ad-id="store.solvingAdId"
        :advisor="store.advisorEnabled"
        :disabled="store.busy"
        @solve="store.solve($event)"
        @refresh="store.refreshAds()"
        @toggle-advisor="store.toggleAdvisor()"
      />
    </template>
  </main>
</template>
