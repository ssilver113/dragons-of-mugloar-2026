<script setup lang="ts">
/**
 * The three states a fetched panel is in: the first load, a first load that failed, and the
 * content. The board and the shop both have them, and only the content differs.
 *
 * The announcement is why this is one component rather than a copy in each: a live region
 * inserted at the same moment as its own text is not reliably announced — the assistive
 * technology has to be watching the node before the words land in it — so the paragraph is
 * always in the tree and only what it holds changes.
 *
 * The skeleton is a slot rather than a prop, because the two panels do not share its shape: the
 * board waits with three tall cards and the shop with four thin bars, and parameterising that
 * would be more argument than markup.
 */
defineProps<{
  /** A first load is out. A refetch over content already on screen is not one. */
  loading: boolean
  failed: boolean
  /** Spoken while the first load is out, and never read aloud again. */
  loadingMessage: string
  failureMessage: string
}>()
/** A refetch, never a retry of whatever spent the turn. */
defineEmits<{ retry: [] }>()
</script>

<template>
  <p class="sr-only" role="status">{{ loading ? loadingMessage : '' }}</p>

  <slot v-if="loading" name="skeleton" />

  <div v-else-if="failed" class="panel panel-danger p-4" role="alert">
    <p class="font-semibold">{{ failureMessage }}</p>
    <button
      type="button"
      class="btn btn-quiet mt-2 rounded-md px-3 py-1.5 text-sm"
      @click="$emit('retry')"
    >
      Try again
    </button>
  </div>

  <slot v-else />
</template>
