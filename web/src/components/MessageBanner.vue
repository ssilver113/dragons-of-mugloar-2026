<script setup lang="ts">
import { computed } from 'vue'

type Tone = 'error' | 'success' | 'failure' | 'info'

const props = defineProps<{ tone: Tone; title: string; dismissible?: boolean }>()
defineEmits<{ dismiss: [] }>()

// Full class strings rather than composed fragments, so Tailwind's scanner can see them.
const TONES: Record<Tone, string> = {
  error: 'paper-danger paper-tinted',
  failure: 'paper-danger paper-tinted',
  success: 'paper-success paper-tinted',
  info: '',
}

const tone = computed(() => TONES[props.tone])
// Errors interrupt; a turn outcome is announced without stealing focus from the board.
const role = computed(() => (props.tone === 'error' ? 'alert' : 'status'))
</script>

<template>
  <div :class="tone" :role="role" class="parchment torn flex items-start gap-3 px-4 py-3">
    <div class="min-w-0 flex-1">
      <p class="font-semibold">{{ title }}</p>
      <p class="mt-0.5 text-sm text-ink-muted"><slot /></p>
    </div>
    <button
      v-if="dismissible"
      type="button"
      class="rounded px-2 py-1 text-sm text-ink-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent"
      @click="$emit('dismiss')"
    >
      Dismiss
    </button>
  </div>
</template>
