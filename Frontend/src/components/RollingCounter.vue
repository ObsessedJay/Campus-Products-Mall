<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value: string | number
  ariaLabel?: string
}>()

// 按字符拆分后，每个数字拥有独立的翻动轨道；冒号等分隔符保持静止。
const glyphs = computed(() => Array.from(String(props.value)))
const isDigit = (glyph: string) => /^\d$/.test(glyph)
</script>

<template>
  <span class="rolling-counter" role="timer" aria-live="off" :aria-label="ariaLabel || String(value)">
    <span class="rolling-counter__visual" aria-hidden="true">
      <template v-for="(glyph, index) in glyphs" :key="index">
        <span v-if="isDigit(glyph)" class="rolling-counter__digit">
          <Transition name="counter-roll">
            <span :key="glyph" class="rolling-counter__glyph">{{ glyph }}</span>
          </Transition>
        </span>
        <span v-else class="rolling-counter__separator">{{ glyph }}</span>
      </template>
    </span>
  </span>
</template>
