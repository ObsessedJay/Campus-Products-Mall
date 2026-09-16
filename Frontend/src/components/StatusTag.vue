<script setup lang="ts">
import { getStatusLabel, getStatusTone } from '../utils/status'

const props = defineProps<{ status: string; compact?: boolean }>()
</script>

<template>
  <span class="status-tag" :class="[`tone-${getStatusTone(props.status)}`, { compact }]">{{ getStatusLabel(props.status) }}</span>
</template>
