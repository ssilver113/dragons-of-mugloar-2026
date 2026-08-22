<script setup lang="ts">
import { computed, ref } from 'vue'
import AdList from './components/AdList.vue'
import AutoPlayControls from './components/AutoPlayControls.vue'
import CalibrationTable from './components/CalibrationTable.vue'
import DecisionLog from './components/DecisionLog.vue'
import GameStats from './components/GameStats.vue'
import MessageBanner from './components/MessageBanner.vue'
import ShopPanel from './components/ShopPanel.vue'
import { useGameStore } from './stores/game'
import { useAutoPlayStore } from './stores/autoplay'
import { useCalibrationStore } from './stores/calibration'

const store = useGameStore()
const autoPlay = useAutoPlayStore()
const calibration = useCalibrationStore()

const starting = computed(() => store.startStatus === 'pending')
const outcome = computed(() => store.lastOutcome)

/**
 * Which part of the game is on screen — but only where they do not all fit. From `lg` up the
 * board, the shop and the log are all visible and this is inert, which is why the switch is
 * buttons rather than a tablist: a tab that controls nothing on a wide screen would be a lie to a
 * screen reader.
 */
type Panel = 'board' | 'shop' | 'log'
const PANELS: { id: Panel; label: string }[] = [
  { id: 'board', label: 'Board' },
  { id: 'shop', label: 'Shop' },
  { id: 'log', label: 'Log' },
]
const view = ref<Panel>('board')
const onlyOnMobile = (panel: Panel) => (view.value === panel ? '' : 'hidden lg:block')

const banner = computed(() => {
  const last = outcome.value
  if (!last) {
    return null
  }
  if (last.kind === 'solve') {
    return {
      tone: last.success ? ('success' as const) : ('failure' as const),
      title: last.success ? 'Mission accomplished' : 'Mission failed',
      body: last.message,
    }
  }
  return last.success
    ? {
        tone: 'success' as const,
        title: `Bought ${last.item.name}`,
        body: 'The dragon is better equipped than it was a turn ago.',
      }
    : {
        tone: 'failure' as const,
        title: 'The shop refused the sale',
        body: 'Nothing changed hands, and the turn is spent all the same.',
      }
})
</script>

<template>
  <main class="mx-auto flex min-h-dvh max-w-6xl flex-col gap-6 px-4 py-8">
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

      <!-- The board is gone but the run is still worth reading, so the log outlives the game. -->
      <DecisionLog
        v-if="autoPlay.log.length"
        :entries="autoPlay.log"
        :halt="autoPlay.halt"
        @keep-going="autoPlay.keepGoing()"
        @retry="autoPlay.run()"
      />

      <CalibrationTable
        v-if="calibration.attempts"
        :rows="calibration.rows"
        :attempts="calibration.attempts"
        :games="calibration.games"
        @reset="calibration.reset()"
      />
    </template>

    <template v-else>
      <AutoPlayControls
        :running="autoPlay.running"
        :stepping="autoPlay.stepping"
        :waiting="autoPlay.waiting"
        :speed="autoPlay.speed"
        :can-play="autoPlay.canPlay"
        :busy="store.busy"
        @run="autoPlay.run()"
        @pause="autoPlay.pause()"
        @step="autoPlay.step()"
        @update:speed="autoPlay.speed = $event"
      />

      <MessageBanner v-if="banner" :tone="banner.tone" :title="banner.title">
        {{ banner.body }}
      </MessageBanner>

      <div
        class="flex gap-1 rounded-lg bg-surface-raised p-1 lg:hidden"
        role="group"
        aria-label="Choose what to show"
      >
        <button
          v-for="panel in PANELS"
          :key="panel.id"
          type="button"
          class="flex-1 rounded-md px-3 py-1.5 text-sm font-medium focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
          :class="view === panel.id ? 'bg-accent text-surface' : 'text-ink-muted hover:text-ink'"
          :aria-pressed="view === panel.id"
          @click="view = panel.id"
        >
          {{ panel.label }}
        </button>
      </div>

      <div class="grid items-start gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div :class="onlyOnMobile('board')" class="min-w-0">
          <div class="flex flex-col gap-4">
            <AdList
              :ads="store.ads"
              :status="store.boardStatus"
              :solving-ad-id="store.solvingAdId"
              :advisor="store.advisorEnabled"
              :lives="store.game?.lives ?? 1"
              :disabled="store.busy || autoPlay.active"
              @solve="store.solve($event)"
              @refresh="store.refreshAds()"
              @toggle-advisor="store.toggleAdvisor()"
            />
            <CalibrationTable
              v-if="store.advisorEnabled"
              :rows="calibration.rows"
              :attempts="calibration.attempts"
              :games="calibration.games"
              @reset="calibration.reset()"
            />
          </div>
        </div>
        <div :class="onlyOnMobile('shop')" class="min-w-0">
          <ShopPanel
            :items="store.shopItems"
            :gold="store.game?.gold ?? 0"
            :status="store.shopStatus"
            :buying-item-id="store.buyingItemId"
            :disabled="store.busy || autoPlay.active"
            @buy="store.buy($event)"
            @refresh="store.refreshShop()"
          />
        </div>
      </div>

      <div :class="onlyOnMobile('log')">
        <DecisionLog
          :entries="autoPlay.log"
          :halt="autoPlay.halt"
          @keep-going="autoPlay.keepGoing()"
          @retry="autoPlay.run()"
        />
      </div>
    </template>
  </main>
</template>
