<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

defineProps<{ title: string; description: string }>()
</script>

<template>
  <div class="empty-state">
    <Inbox :size="34" aria-hidden="true" />
    <h2>{{ title }}</h2>
    <p>{{ description }}</p>
    <slot />
  </div>
</template>
