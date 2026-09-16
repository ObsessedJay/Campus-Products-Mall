<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Bell, Check, CheckCheck, LoaderCircle, Mail, Save, School } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import { getMessages, getUnreadMessageCount, getUserProfile, markAllMessagesRead, markMessageRead, updateUserProfile } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { UserMessage, UserProfile } from '../types/api'

const auth = useAuthStore()
const router = useRouter()
const profile = ref<UserProfile | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const feedback = ref('')
const messages = ref<UserMessage[]>([])
const messagesLoading = ref(true)
const messagesError = ref('')
const unreadCount = ref(0)
const messageBusy = ref(false)
const visibleMessages = computed(() => [...messages.value].sort((left, right) => {
  const readOrder = Number(Boolean(left.readAt)) - Number(Boolean(right.readAt))
  return readOrder || new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()
}))
const form = reactive({ nickname: '' })
const initials = computed(() => (profile.value?.nickname || auth.user?.nickname || '同学').slice(0, 2))
const canSave = computed(() => form.nickname.trim().length > 0 && !loading.value && !saving.value)

watch(
  () => form.nickname,
  () => { feedback.value = '' },
  { flush: 'sync' },
)

function fillForm(value: UserProfile) {
  profile.value = value
  form.nickname = value.nickname || ''
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [profileData, userMessages, count] = await Promise.all([
      getUserProfile(), getMessages(), getUnreadMessageCount(),
    ])
    fillForm(profileData)
    messages.value = userMessages
    unreadCount.value = count
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '资料加载失败，请稍后重试'
  } finally {
    loading.value = false
    messagesLoading.value = false
  }
}

function messageTypeLabel(type: UserMessage['type']) {
  return ({ ACTIVITY_REMINDER: '开售提醒', LOTTERY_RESULT: '抽签结果', PAYMENT_SUCCESS: '支付通知', PICKUP_VERIFIED: '核销通知' })[type]
}

async function openMessage(message: UserMessage) {
  if (!message.readAt) {
    try {
      await markMessageRead(message.id)
      message.readAt = new Date().toISOString()
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch (requestError) {
      messagesError.value = requestError instanceof Error ? requestError.message : '消息状态更新失败'
      return
    }
  }
  if (message.link) await router.push(message.link)
}

async function readAll() {
  if (messageBusy.value || unreadCount.value === 0) return
  messageBusy.value = true
  messagesError.value = ''
  try {
    await markAllMessagesRead()
    const readAt = new Date().toISOString()
    messages.value = messages.value.map((message) => ({ ...message, readAt: message.readAt || readAt }))
    unreadCount.value = 0
  } catch (requestError) {
    messagesError.value = requestError instanceof Error ? requestError.message : '全部已读操作失败'
  } finally { messageBusy.value = false }
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  feedback.value = ''
  try {
    const updated = await updateUserProfile({ nickname: form.nickname.trim() })
    fillForm(updated)
    auth.updateNickname(updated.nickname)
    feedback.value = '个人资料已保存。'
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '保存失败，请检查后重试'
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="profile-page page-wrap narrow-wrap">
    <header class="profile-header">
      <div><h1>个人资料</h1><p>确认校园身份并维护公开昵称。</p></div>
      <span class="profile-status"><Check :size="16" />身份已登录</span>
    </header>

    <div v-if="error" class="inline-alert error" role="alert"><span>{{ error }}</span><button type="button" @click="load">重新加载</button></div>
    <p v-if="feedback" class="action-feedback" role="status">{{ feedback }}</p>

    <div v-if="loading" class="profile-ledger profile-loading" aria-label="正在加载个人资料">
      <div class="skeleton"></div><div class="skeleton"></div>
    </div>

    <form v-else-if="profile" class="profile-ledger" :aria-busy="saving" @submit.prevent="save">
      <aside class="identity-pass">
        <div class="identity-seal" aria-hidden="true">{{ initials }}</div>
        <div>
          <span>校园领取身份</span>
          <h2>{{ profile.nickname }}</h2>
          <p>{{ profile.email }}</p>
        </div>
        <dl>
          <div><dt><School :size="16" />学校</dt><dd>{{ profile.school }}</dd></div>
          <div><dt><Mail :size="16" />邮箱</dt><dd>{{ profile.email }}</dd></div>
        </dl>
        <p class="identity-note">学校由平台统一设置，无需额外填写校园身份字段。</p>
      </aside>

      <section class="profile-form-panel">
        <header><h2>个人资料</h2><p>领取地点由商家在发布商品时确定，并随订单展示。</p></header>
        <div class="profile-fields">
          <label><span>昵称</span><input v-model.trim="form.nickname" maxlength="64" autocomplete="nickname" required /></label>
        </div>

        <div class="profile-actions">
          <span>昵称用于订单与提货展示，学校固定为成都信息工程大学。</span>
          <button class="primary-action" type="submit" :disabled="!canSave">
            <LoaderCircle v-if="saving" class="auth-loading-icon" :size="18" />
            <Save v-else :size="18" />{{ saving ? '正在保存…' : '保存资料' }}
          </button>
        </div>
      </section>
    </form>

    <section class="message-center" :aria-busy="messagesLoading">
      <header>
        <div><Bell :size="24" /><span><h2>站内消息</h2><p>活动资格、支付与领取进度会保留在这里。</p></span></div>
        <div class="message-actions"><strong>{{ unreadCount }} 条未读</strong><button class="secondary-action" type="button" :disabled="messageBusy || unreadCount === 0" @click="readAll"><CheckCheck :size="17" />全部已读</button></div>
      </header>
      <div v-if="messagesError" class="inline-alert error" role="alert"><span>{{ messagesError }}</span></div>
      <div v-if="messagesLoading" class="message-list"><div v-for="n in 3" :key="n" class="skeleton message-skeleton"></div></div>
      <div v-else-if="messages.length" class="message-list">
        <button v-for="message in visibleMessages" :key="message.id" class="message-row" :class="{ unread: !message.readAt }" type="button" @click="openMessage(message)">
          <span class="message-state" aria-hidden="true"></span>
          <span class="message-copy"><small>{{ messageTypeLabel(message.type) }}</small><strong>{{ message.title }}</strong><span>{{ message.content }}</span></span>
          <time>{{ new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(message.createdAt)) }}</time>
        </button>
      </div>
      <div v-else class="message-empty"><Bell :size="22" /><span>暂无站内消息</span></div>
    </section>
  </div>
</template>
