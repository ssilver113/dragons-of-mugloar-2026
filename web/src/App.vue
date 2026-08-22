<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import AdList from './components/AdList.vue'
import AppBackdrop from './components/AppBackdrop.vue'
import AutoPlayControls from './components/AutoPlayControls.vue'
import CalibrationTable from './components/CalibrationTable.vue'
import DecisionLog from './components/DecisionLog.vue'
import DragonSigil from './components/DragonSigil.vue'
import GameStats from './components/GameStats.vue'
import MessageBanner from './components/MessageBanner.vue'
import ReputationPanel from './components/ReputationPanel.vue'
import ShopPanel from './components/ShopPanel.vue'
import type { DragonMood } from './assets/artwork'
import { present } from './api/errorPresentation'
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

/**
 * A failure, dressed for how much it matters. Terminal codes never arrive here — the store puts
 * those on the game's state instead, and the panel that replaces the board says them.
 */
const failure = computed(() => {
  const e = store.error
  return e === null ? null : { ...present(e.code), message: e.message }
})

/**
 * Where the run stopped, and what to say about it. A lost session is not a defeat: the dragon
 * was fine, the server simply stopped tracking it, and telling the player their dragon fell
 * would be a lie about their own game.
 */
const ENDINGS = {
  finished: {
    heading: 'The dragon has fallen',
    action: 'Play again',
  },
  lost: {
    heading: 'This game was lost',
    action: 'Start a new game',
  },
} as const

const ended = computed(() => (store.ending === null ? null : ENDINGS[store.ending]))

/**
 * The button that ended the run is gone by the time this renders, so keyboard focus would land
 * back at the top of the document with no announcement of why. Moving it to the panel puts the
 * explanation and the way out under the cursor that is already there.
 */
const endPanel = ref<HTMLElement | null>(null)
watch(ended, async (now, before) => {
  if (now && !before) {
    await nextTick()
    endPanel.value?.focus()
  }
})

/**
 * What the seal shows. A win is a moment rather than a state, so it is held for a beat and then
 * released; everything else is read straight off the game.
 *
 * A lost session is deliberately not a defeat. The dragon was fine — the server stopped tracking
 * it — and stamping the fallen seal on that would be a lie about the player's own game, the same
 * one the ending copy takes care not to tell.
 */
const triumphant = ref(false)
let fading: ReturnType<typeof setTimeout> | undefined

watch(outcome, (last) => {
  const won =
    last !== null &&
    ((last.kind === 'solve' && last.success) ||
      (last.kind === 'purchase' && last.success && last.item.levelsGained > 0))
  if (!won) {
    return
  }
  triumphant.value = true
  clearTimeout(fading)
  fading = setTimeout(() => (triumphant.value = false), 2000)
})

onUnmounted(() => clearTimeout(fading))

const mood = computed<DragonMood>(() => {
  if (store.ending === 'finished') {
    return 'defeated'
  }
  return triumphant.value ? 'victorious' : 'idle'
})

const banner = computed(() => {
  const last = outcome.value
  if (!last) {
    return null
  }
  if (last.kind === 'investigation') {
    return {
      tone: 'info' as const,
      title: 'The scouts are back',
      body: 'A turn spent and nothing risked. Every ad on the board is a turn older.',
    }
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
  <AppBackdrop />

  <main class="mx-auto flex min-h-dvh max-w-6xl flex-col gap-6 px-4 py-8">
    <header class="flex items-center justify-between gap-4">
      <div class="flex flex-col gap-1">
        <!-- Cinzel is a wide face: at 24px the title wraps beside the seal on a 375px screen. -->
        <h1 class="text-xl font-semibold text-accent sm:text-3xl">Dragons of Mugloar</h1>
        <p class="text-sm text-ink-muted">
          Take the jobs your dragon can survive. Every action costs a turn.
        </p>
      </div>
      <DragonSigil :mood="mood" :size="80" class="size-14 sm:size-20" />
    </header>

    <GameStats v-if="store.game" :game="store.game" :announce="!autoPlay.active" />

    <!--
      `fault` is an alert and `note` is not: a refusal the server saw coming is not a failure, and
      the app has usually already corrected itself by the time the sentence is read.
    -->
    <MessageBanner
      v-if="failure"
      :tone="failure.severity === 'fault' ? 'error' : 'info'"
      :title="failure.title"
      dismissible
      @dismiss="store.dismissError()"
    >
      {{ failure.message }}
      <!--
        A refetch, never a retry of the action itself: a solve or a purchase that timed out may
        already have landed upstream, and repeating it would spend a second turn.
      -->
      <button
        v-if="failure.offerRefresh && store.playable"
        type="button"
        class="ml-1 rounded font-semibold text-accent underline hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        @click="store.refreshAds()"
      >
        Refresh the board
      </button>
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

    <template v-else-if="ended">
      <section
        ref="endPanel"
        tabindex="-1"
        class="flex flex-col items-start gap-4 rounded-lg border border-ink-muted/20 p-6 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
        role="status"
      >
        <DragonSigil
          v-if="store.ending === 'finished'"
          mood="defeated"
          :size="112"
          class="size-24 self-center sm:size-28"
        />
        <div class="flex flex-col gap-1">
          <h2 class="text-lg font-semibold">{{ ended.heading }}</h2>
          <p v-if="store.ending === 'lost'" class="text-ink-muted">
            The server is no longer tracking this game — it aged out, or the API restarted. A
            session is never picked back up, so the run ends here.
          </p>
          <p class="text-ink-muted">
            {{ store.ending === 'lost' ? 'It was worth' : 'Final score' }}
            {{ store.game?.score }} points after {{ store.game?.turn }} turns.
          </p>
        </div>
        <button
          type="button"
          class="rounded-md bg-accent px-4 py-2 font-semibold text-surface hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="starting"
          @click="store.startGame()"
        >
          {{ starting ? 'Starting…' : ended.action }}
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
        <!--
          The stack is a level in, as it is in the board column: `lg:block` and `flex` are both
          display utilities, and the variant is emitted later, so the two on one element would
          leave the column laid out as blocks with its gap doing nothing.
        -->
        <div :class="onlyOnMobile('shop')" class="min-w-0">
          <div class="flex flex-col gap-4">
            <ShopPanel
              :items="store.shopItems"
              :gold="store.game?.gold ?? 0"
              :status="store.shopStatus"
              :buying-item-id="store.buyingItemId"
              :disabled="store.busy || autoPlay.active"
              @buy="store.buy($event)"
              @refresh="store.refreshShop()"
            />
            <ReputationPanel
              :reputation="store.reputation"
              :scouting="store.investigating"
              :disabled="store.busy || autoPlay.active"
              @scout="store.investigate()"
            />
          </div>
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
