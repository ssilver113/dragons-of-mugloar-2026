<script setup lang="ts">
import { computed, ref } from 'vue'
import AbandonFooter from './components/AbandonFooter.vue'
import AdList from './components/AdList.vue'
import AdvisorPanel from './components/AdvisorPanel.vue'
import AppBackdrop from './components/AppBackdrop.vue'
import AppFooter from './components/AppFooter.vue'
import PaperFilters from './components/PaperFilters.vue'
import AppIcon from './components/AppIcon.vue'
import AutoPlayControls from './components/AutoPlayControls.vue'
import CalibrationTable from './components/CalibrationTable.vue'
import DecisionLog from './components/DecisionLog.vue'
import GameEnding from './components/GameEnding.vue'
import GameStats from './components/GameStats.vue'
import MessageBanner from './components/MessageBanner.vue'
import MissionResult from './components/MissionResult.vue'
import ReputationPanel from './components/ReputationPanel.vue'
import ShopPanel from './components/ShopPanel.vue'
import { wordmarkArt, wordmarkSrcset, type IconName } from './assets/artwork'
import { useBoardView } from './advisor/boardView'
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

/** Held here because two siblings need it: the advisor's table sets it, the list is drawn from it. */
const boardView = useBoardView({
  ads: computed(() => store.ads),
  lives: computed(() => store.game?.lives ?? 1),
  advisor: computed(() => store.advisorEnabled),
  holding: computed(() => store.acting),
})

/** A reload is not a new game. The log follows the game, restored only once it is on screen. */
void store.loadMeta()
void resumeInterrupted()

async function resumeInterrupted(): Promise<void> {
  const resumed = await store.resume()
  const current = store.game
  if (resumed && current) {
    autoPlay.restore(current.gameId)
  }
}

/** Never mid-turn: one already sent would land on the game that replaced it. */
const canAbandon = computed(() => !store.acting && !autoPlay.active)

/**
 * Which part of the game is on screen, and inert from `lg` up where all three fit. Buttons rather
 * than a tablist for that reason: a tab controlling nothing would be a lie to a screen reader.
 */
type Panel = 'board' | 'shop' | 'solver'
const PANELS: { id: Panel; label: string; icon: IconName }[] = [
  { id: 'board', label: 'Board', icon: 'board' },
  { id: 'shop', label: 'Shop', icon: 'shop' },
  { id: 'solver', label: 'Auto-play', icon: 'autoplay' },
]
const view = ref<Panel>('board')
const onlyOnMobile = (panel: Panel) => (view.value === panel ? '' : 'hidden lg:block')

/** Terminal codes never arrive here: the store puts those on the game's state instead. */
const failure = computed(() => {
  const e = store.error
  return e === null ? null : { ...present(e.code), message: e.message }
})

/** The player's own moves only: the solver's arrive several a second and would strobe. */
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
  <PaperFilters />

  <!-- Owns the page height, so the footer lands under the board on a long page and at the bottom
       of the window on a short one. -->
  <div class="relative mx-auto flex min-h-dvh max-w-6xl flex-col gap-6 px-4 py-8">
    <!-- The same three names the switcher below uses, for the input the switcher does not serve:
         from `lg` up all three panels are on screen, so a keyboard reaches the shop only after the
         board's ten Solve buttons. Below `lg` the switcher hides the other two, and a hidden panel
         is not in the tab order, so there is nothing to skip. Each target carries `tabindex="-1"`
         so the link lands focus in the column rather than only scrolling to it. -->
    <nav v-if="store.playable" class="skip-links hidden lg:flex" aria-label="Skip to">
      <a v-for="panel in PANELS" :key="panel.id" :href="`#${panel.id}`" class="skip-link">
        {{ panel.label }}
      </a>
    </nav>

    <main class="flex flex-1 flex-col gap-6">
      <header class="flex flex-col items-center gap-1 text-center">
        <!-- The name is a drawing, not type, so the words stay for the accessibility tree. -->
        <h1>
          <img
            :src="wordmarkArt"
            :srcset="wordmarkSrcset"
            sizes="(min-width: 44rem) 42rem, calc(100vw - 2rem)"
            alt=""
            aria-hidden="true"
            width="1344"
            height="394"
            fetchpriority="high"
            decoding="async"
            class="h-auto w-full max-w-2xl drop-shadow-sm"
          />
          <span class="sr-only">Dragons of Mugloar</span>
        </h1>
        <!-- Balanced because a centred line that wraps looks accidental when the second line is short. -->
        <p class="text-sm text-balance text-ink-muted">
          Take the jobs your dragon can survive. Every action costs a turn.
        </p>
        <!-- Stated wherever a score is: nothing won against a simulated board is a claim about
             the real game. -->
        <p
          v-if="store.offline"
          class="rounded border border-warning/50 px-1.5 py-0.5 text-xs text-warning"
        >
          Simulated world — no live game behind this one
        </p>
      </header>

      <GameStats v-if="store.game" :game="store.game" :announce="!autoPlay.active" />

      <!-- `fault` alerts and `note` does not: a refusal the server saw coming is not a failure. -->
      <MessageBanner
        v-if="failure"
        :tone="failure.severity === 'fault' ? 'error' : 'info'"
        :title="failure.title"
        dismissible
        @dismiss="store.dismissError()"
      >
        {{ failure.message }}
        <!-- A refetch, never a retry: an action that timed out may already have landed upstream. -->
        <button
          v-if="failure.offerRefresh && store.playable"
          type="button"
          class="focus-ring ml-1 rounded font-semibold text-accent underline hover:brightness-110"
          @click="store.refreshAds()"
        >
          Refresh the board
        </button>
      </MessageBanner>

      <template v-if="!store.started">
        <!-- The game from before is gone, but nothing the player did lost it, so this is a note
             rather than the defeat panel. -->
        <MessageBanner
          v-if="store.resumeFailed"
          tone="info"
          title="The game from before could not be picked up"
        >
          The server had already let that session go — it aged out, or the API restarted. A session
          is never picked back up, so this one starts fresh.
        </MessageBanner>

        <section class="panel flex flex-col items-start gap-4 p-6">
          <p class="text-ink-muted">
            Start a game to draw a board of ten jobs, each scored for your dragon's level.
          </p>
          <button
            type="button"
            class="btn btn-primary rounded-md px-4 py-2"
            :disabled="starting"
            @click="store.startGame()"
          >
            {{ starting ? 'Starting…' : 'Start a game' }}
          </button>
        </section>
      </template>

      <!-- `store.game` is never null once there is an ending — the store derives one from the
           other — so the second test is only what narrows the type. -->
      <template v-else-if="store.ending && store.game">
        <GameEnding
          :ending="store.ending"
          :game="store.game"
          :starting="starting"
          @restart="store.startGame()"
        />

        <!-- The board is gone but the run is still worth reading, so the log outlives the game. -->
        <DecisionLog
          v-if="autoPlay.log.length"
          :entries="autoPlay.log"
          :halt="autoPlay.halt"
          @keep-going="autoPlay.keepGoing()"
          @retry="autoPlay.run()"
        />

        <!-- On the end screen the tally is on its own, and it carries no surface, so the panel
             is here rather than in it. -->
        <section v-if="calibration.attempts" class="panel p-4">
          <CalibrationTable
            :rows="calibration.rows"
            :attempts="calibration.attempts"
            :games="calibration.games"
            @reset="calibration.reset()"
          />
        </section>
      </template>

      <template v-else>
        <!-- First, because it is what the rest of the page is read in: it sets the order of the
             jobs below and what their figures mean. -->
        <AdvisorPanel
          :advisor="store.advisorEnabled"
          :sort="boardView.sort.value"
          :posture="boardView.posture.value"
          :filters="boardView.filters.value"
          :shown="boardView.shown.value"
          :total="boardView.total.value"
          :life-cost="boardView.lifeCost.value"
          :rows="calibration.rows"
          :attempts="calibration.attempts"
          :games="calibration.games"
          @toggle-advisor="store.toggleAdvisor()"
          @update:sort="boardView.sort.value = $event"
          @update:posture="boardView.posture.value = $event"
          @toggle-filter="boardView.toggleFilter($event)"
          @clear-filters="boardView.clearFilters()"
          @reset-calibration="calibration.reset()"
        />

        <MissionResult :pending="pending" :solver-running="autoPlay.running" :outcome="banner" />

        <div class="panel flex gap-1 p-1 lg:hidden" role="group" aria-label="Choose what to show">
          <button
            v-for="panel in PANELS"
            :key="panel.id"
            type="button"
            class="focus-ring relative flex-1 rounded-md px-2 py-1.5 text-sm font-medium sm:px-3"
            :class="
              view === panel.id
                ? 'relief-pressed bg-accent text-surface'
                : 'text-ink-muted hover:text-ink'
            "
            :aria-pressed="view === panel.id"
            @click="view = panel.id"
          >
            <!-- Held on one line: `Auto-play` wraps at 375px, which turns a 32px switch into a
                 52px one. -->
            <span class="flex items-center justify-center gap-1.5 whitespace-nowrap">
              <AppIcon :name="panel.icon" :size="16" />
              {{ panel.label }}
            </span>
            <!-- A halt is the one state needing an answer from behind this switch. Out of the
                 flow, and said in words as well as drawn. -->
            <template v-if="panel.id === 'solver' && autoPlay.halt">
              <span
                class="absolute top-1 right-1 size-1.5 rounded-full bg-danger"
                aria-hidden="true"
              />
              <span class="sr-only">, stopped</span>
            </template>
          </button>
        </div>

        <!-- Hidden as a whole: hiding only the children leaves an empty grid box that still
             spends the column's `gap-6`. -->
        <div
          class="items-start gap-6 lg:grid lg:grid-cols-[minmax(0,1fr)_20rem]"
          :class="view === 'solver' ? 'hidden lg:grid' : 'grid'"
        >
          <div
            id="board"
            tabindex="-1"
            :class="onlyOnMobile('board')"
            class="focus-ring min-w-0 rounded-md"
          >
            <AdList
              :entries="boardView.entries.value"
              :total="boardView.total.value"
              :status="store.boardStatus"
              :solving-ad-id="store.solvingAdId"
              :advisor="store.advisorEnabled"
              :disabled="store.busy || autoPlay.active"
              @solve="store.solve($event)"
              @refresh="store.refreshAds()"
            />
          </div>
          <!-- The stack is a level in: `lg:block` and `flex` are both display utilities and the
               variant is emitted later, so one element could not carry both. -->
          <div
            id="shop"
            tabindex="-1"
            :class="onlyOnMobile('shop')"
            class="focus-ring min-w-0 rounded-md"
          >
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

        <!-- The drive and the record it writes, on one board — which is also the box the drive
             is sticky within, so Pause stays on screen while the log scrolls past. -->
        <div
          id="solver"
          tabindex="-1"
          :class="onlyOnMobile('solver')"
          class="focus-ring rounded-md"
        >
          <div class="timber solver-board flex flex-col gap-3">
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
            <DecisionLog
              :entries="autoPlay.log"
              :halt="autoPlay.halt"
              @keep-going="autoPlay.keepGoing()"
              @retry="autoPlay.run()"
            />
          </div>
        </div>

        <AbandonFooter
          v-if="store.game"
          :game="store.game"
          :can-abandon="canAbandon"
          :starting="starting"
          :solver-active="autoPlay.active"
          @abandon="store.startGame()"
        />
      </template>
    </main>

    <AppFooter :version="store.version" :built-at="store.builtAt" />
  </div>
</template>
