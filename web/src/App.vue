<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import AdList from './components/AdList.vue'
import AppBackdrop from './components/AppBackdrop.vue'
import AppIcon from './components/AppIcon.vue'
import AutoPlayControls from './components/AutoPlayControls.vue'
import CalibrationTable from './components/CalibrationTable.vue'
import DecisionLog from './components/DecisionLog.vue'
import DragonSigil from './components/DragonSigil.vue'
import GameStats from './components/GameStats.vue'
import MessageBanner from './components/MessageBanner.vue'
import MissionResult from './components/MissionResult.vue'
import ReputationPanel from './components/ReputationPanel.vue'
import ShopPanel from './components/ShopPanel.vue'
import type { IconName } from './assets/artwork'
import type { PendingKind } from './components/MissionResult.vue'
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
 * A reload is not a new game. The id of the one in progress outlives the page, and neither
 * listing the board nor listing the shop costs a turn, so the whole thing can be picked back up
 * for the price of two GETs. The log follows the game rather than leading it: it is restored only
 * once the game it belongs to is known to be on screen.
 */
void resumeInterrupted()

async function resumeInterrupted(): Promise<void> {
  const resumed = await store.resume()
  const current = store.game
  if (resumed && current) {
    autoPlay.restore(current.gameId)
  }
}

/**
 * Abandoning takes two clicks. Starting a game costs nothing upstream, but the run it replaces is
 * gone for good, and a stray click at turn forty is an expensive way to learn that.
 */
const abandoning = ref(false)
const confirmAbandon = ref<HTMLButtonElement | null>(null)
const startNew = ref<HTMLButtonElement | null>(null)

/**
 * Not while the solver is mid-run: a turn already sent would land after the new game had started
 * and write the old game's state over it. Pausing first is the player's call, not something to do
 * silently on their behalf.
 */
const canAbandon = computed(() => !store.acting && !autoPlay.active)

async function askToAbandon(): Promise<void> {
  abandoning.value = true
  await nextTick()
  confirmAbandon.value?.focus()
}

async function keepPlaying(): Promise<void> {
  abandoning.value = false
  await nextTick()
  startNew.value?.focus()
}

function abandon(): void {
  abandoning.value = false
  void store.startGame()
}

/**
 * Which part of the game is on screen — but only where they do not all fit. From `lg` up the
 * board, the shop and the log are all visible and this is inert, which is why the switch is
 * buttons rather than a tablist: a tab that controls nothing on a wide screen would be a lie to a
 * screen reader.
 */
type Panel = 'board' | 'shop' | 'log'
const PANELS: { id: Panel; label: string; icon: IconName }[] = [
  { id: 'board', label: 'Board', icon: 'board' },
  { id: 'shop', label: 'Shop', icon: 'shop' },
  { id: 'log', label: 'Log', icon: 'log' },
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
// An open confirmation belongs to the run that was live when it opened. A game that ends under it
// answers the question, and the next game must not inherit a half-pressed button.
watch(() => store.playable, () => (abandoning.value = false))
watch(ended, async (now, before) => {
  if (now && !before) {
    await nextTick()
    endPanel.value?.focus()
  }
})

/**
 * Which of the player's own moves is in flight. The solver's turns are deliberately not here:
 * they arrive several a second at max speed, and a placeholder strobing between two states is
 * worse than one that says plainly who is holding the game.
 */
const pending = computed<PendingKind | null>(() => {
  if (store.solvingAdId !== null) {
    return 'solve'
  }
  if (store.buyingItemId !== null) {
    return 'purchase'
  }
  return store.investigating ? 'investigation' : null
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
    <header class="flex flex-col gap-1">
      <h1 class="text-xl font-semibold text-accent sm:text-3xl">Dragons of Mugloar</h1>
      <p class="text-sm text-ink-muted">
        Take the jobs your dragon can survive. Every action costs a turn.
      </p>
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
      <!--
        Said once, on the way in: the game from before the reload is gone, but nothing the player
        did lost it. A defeat panel would be claiming something about their dragon that is untrue.
      -->
      <MessageBanner
        v-if="store.resumeFailed"
        tone="info"
        title="The game from before could not be picked up"
      >
        The server had already let that session go — it aged out, or the API restarted. A session
        is never picked back up, so this one starts fresh.
      </MessageBanner>

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
        :halt="autoPlay.halt"
        :turns="autoPlay.log.length"
        @run="autoPlay.run()"
        @pause="autoPlay.pause()"
        @step="autoPlay.step()"
        @update:speed="autoPlay.speed = $event"
      />

      <MissionResult :pending="pending" :solver-running="autoPlay.running" :outcome="banner" />

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
          <span class="flex items-center justify-center gap-1.5">
            <AppIcon :name="panel.icon" :size="16" />
            {{ panel.label }}
          </span>
        </button>
      </div>

      <!--
        Hidden as a whole rather than one column at a time. Hiding only the children leaves an
        empty grid box in the flow, and the page column still spends a `gap-6` on it — which is
        why the log used to start twenty-four pixels lower than the board and the shop did.
      -->
      <div
        class="items-start gap-6 lg:grid lg:grid-cols-[minmax(0,1fr)_20rem]"
        :class="view === 'log' ? 'hidden lg:grid' : 'grid'"
      >
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

      <!--
        Last on the page and quiet with it. This is the only way out of a run that is going badly
        but is not over, and it is deliberately nowhere near the buttons that spend turns.
      -->
      <footer
        class="mt-auto flex flex-col items-start gap-2 border-t border-ink-muted/15 pt-4"
        @keydown.esc="keepPlaying()"
      >
        <template v-if="!abandoning">
          <button
            ref="startNew"
            type="button"
            class="rounded-md border border-ink-muted/40 px-3 py-1.5 text-sm font-semibold text-ink-muted hover:border-ink hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="!canAbandon"
            @click="askToAbandon()"
          >
            Start a new game
          </button>
          <p class="text-xs text-ink-muted">
            {{
              autoPlay.active
                ? 'Pause the solver first — a turn already in flight would land on the new game.'
                : 'Ends this run and deals a fresh board. The game itself costs nothing to start.'
            }}
          </p>
        </template>

        <template v-else>
          <p id="abandon-question" class="text-sm">
            Abandon this run? It is worth {{ store.game?.score }} points after
            {{ store.game?.turn }} turns, and cannot be picked back up.
          </p>
          <div class="flex flex-wrap gap-2">
            <button
              ref="confirmAbandon"
              type="button"
              class="rounded-md bg-accent px-3 py-1.5 text-sm font-semibold text-surface hover:brightness-110 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-40"
              aria-describedby="abandon-question"
              :disabled="!canAbandon || starting"
              @click="abandon()"
            >
              {{ starting ? 'Starting…' : 'Yes, start a new game' }}
            </button>
            <button
              type="button"
              class="rounded-md border border-ink-muted/40 px-3 py-1.5 text-sm font-semibold text-ink-muted hover:border-ink hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
              @click="keepPlaying()"
            >
              Keep playing
            </button>
          </div>
        </template>
      </footer>
    </template>
  </main>
</template>
