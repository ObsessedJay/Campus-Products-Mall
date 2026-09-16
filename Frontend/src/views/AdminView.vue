<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { Check, FileCheck2, Flag, History, LoaderCircle, RefreshCw, ScrollText, ShieldAlert, X } from 'lucide-vue-next'
import StatusTag from '../components/StatusTag.vue'
import { approveReview, getPendingReviews, getRecentReviewLogs, getReports, rejectReport, rejectReview, resolveReport, type ReviewContentType, type ReviewItem, type ReviewLog } from '../services/admin'
import { useAuthStore } from '../stores/auth'
import type { ReportRecord, ReportStatus } from '../types/api'

const auth = useAuthStore()
const items = ref<ReviewItem[]>([])
const logs = ref<ReviewLog[]>([])
const queueLoading = ref(true)
const logsLoading = ref(true)
const queueError = ref('')
const logsError = ref('')
const actionError = ref('')
const feedback = ref('')
const activeType = ref<'ALL' | ReviewContentType>('ALL')
const logType = ref<'ALL' | ReviewContentType>('ALL')
const logResult = ref<'ALL' | ReviewLog['result']>('ALL')
const rejecting = ref<ReviewItem | null>(null)
const reason = ref('')
const busyKey = ref('')
const rejectReasonInput = ref<HTMLTextAreaElement | null>(null)
const rejectTrigger = ref<HTMLElement | null>(null)
const reports = ref<ReportRecord[]>([])
const reportsLoading = ref(true)
const reportsError = ref('')
const reportStatus = ref<ReportStatus | 'ALL'>('PENDING')
const reportBusyId = ref<number>()


const visibleItems = computed(() => activeType.value === 'ALL'
  ? items.value
  : items.value.filter((item) => item.type === activeType.value))
const visibleLogs = computed(() => logs.value.filter((log) =>
  (logType.value === 'ALL' || log.contentType === logType.value)
  && (logResult.value === 'ALL' || log.result === logResult.value)))

function label(type: ReviewContentType) {
  return type === 'PRODUCT' ? '商品' : '活动'
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未知'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(date)
}

async function loadQueue() {
  queueLoading.value = true
  queueError.value = ''
  try {
    items.value = await getPendingReviews()
  } catch (requestError) {
    queueError.value = requestError instanceof Error ? requestError.message : '审核队列加载失败'
  } finally {
    queueLoading.value = false
  }
}

async function loadLogs() {
  logsLoading.value = true
  logsError.value = ''
  try {
    logs.value = await getRecentReviewLogs(undefined, 100)
  } catch (requestError) {
    logsError.value = requestError instanceof Error ? requestError.message : '审核记录加载失败'
  } finally {
    logsLoading.value = false
  }
}

async function load() {
  await Promise.all([loadQueue(), loadLogs(), loadReports()])
}

async function loadReports() {
  reportsLoading.value = true
  reportsError.value = ''
  try { reports.value = await getReports(reportStatus.value === 'ALL' ? undefined : reportStatus.value) }
  catch (requestError) { reportsError.value = requestError instanceof Error ? requestError.message : '举报队列加载失败' }
  finally { reportsLoading.value = false }
}

async function handleReport(report: ReportRecord, action: 'resolve' | 'reject') {
  if (reportBusyId.value) return
  const promptLabel = action === 'resolve' ? '处理结果' : '驳回说明'
  const result = window.prompt(`请输入${promptLabel}`)?.trim()
  if (!result) return
  reportBusyId.value = report.id
  try {
    await (action === 'resolve' ? resolveReport(report.id, result) : rejectReport(report.id, result))
    feedback.value = action === 'resolve' ? '举报已确认处理。' : '举报已驳回。'
    await loadReports()
  } catch (requestError) {
    reportsError.value = requestError instanceof Error ? requestError.message : '举报处理失败'
  } finally { reportBusyId.value = undefined }
}

async function approve(item: ReviewItem) {
  busyKey.value = `${item.type}:${item.id}`
  actionError.value = ''
  feedback.value = ''
  try {
    await approveReview(item.type, item.id)
    items.value = items.value.filter((candidate) => candidate.type !== item.type || candidate.id !== item.id)
    feedback.value = `已通过${label(item.type)}“${item.name}”`
    await loadLogs()
  } catch (requestError) {
    actionError.value = requestError instanceof Error ? requestError.message : '审核操作失败'
  } finally {
    busyKey.value = ''
  }
}

function openReject(item: ReviewItem, event: Event) {
  rejectTrigger.value = event.currentTarget as HTMLElement
  rejecting.value = item
  reason.value = ''
  actionError.value = ''
  nextTick(() => rejectReasonInput.value?.focus())
}

function closeReject() {
  if (busyKey.value) return
  rejecting.value = null
  nextTick(() => rejectTrigger.value?.focus())
}

function handleModalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeReject()
    return
  }
  if (event.key !== 'Tab') return
  const container = event.currentTarget as HTMLElement
  const controls = Array.from(container.querySelectorAll<HTMLElement>('button:not(:disabled), textarea:not(:disabled)'))
  if (controls.length === 0) return
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

async function submitReject() {
  if (!rejecting.value || reason.value.trim().length === 0) return
  const item = rejecting.value
  busyKey.value = `${item.type}:${item.id}`
  actionError.value = ''
  feedback.value = ''
  try {
    await rejectReview(item.type, item.id, reason.value.trim())
    items.value = items.value.filter((candidate) => candidate.type !== item.type || candidate.id !== item.id)
    rejecting.value = null
    feedback.value = `已驳回${label(item.type)}“${item.name}”`
    await loadLogs()
    nextTick(() => rejectTrigger.value?.focus())
  } catch (requestError) {
    actionError.value = requestError instanceof Error ? requestError.message : '审核操作失败'
  } finally {
    busyKey.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="admin-page page-wrap">
    <header class="workspace-header">
      <div><h1>审核工作台</h1><p>处理内容送审，核对最近审核记录。</p></div>
      <span class="admin-identity"><ShieldAlert :size="18" aria-hidden="true" />{{ auth.user?.nickname }}</span>
    </header>

    <section class="admin-queue" :aria-busy="queueLoading">
      <div class="queue-title">
        <FileCheck2 :size="25" aria-hidden="true" />
        <div><h2>待审核队列</h2><p>按提交时间依次处理商品和活动。</p></div>
        <strong>{{ queueLoading ? '--' : visibleItems.length }}</strong>
      </div>
      <div class="segment-row review-tabs" aria-label="审核内容类型">
        <button type="button" :class="{ active: activeType === 'ALL' }" @click="activeType = 'ALL'">全部</button>
        <button type="button" :class="{ active: activeType === 'PRODUCT' }" @click="activeType = 'PRODUCT'">商品</button>
        <button type="button" :class="{ active: activeType === 'ACTIVITY' }" @click="activeType = 'ACTIVITY'">活动</button>
        <button class="icon-button" type="button" title="刷新审核队列" aria-label="刷新审核队列" :disabled="queueLoading" @click="loadQueue"><RefreshCw :size="17" aria-hidden="true" /></button>
      </div>
      <p v-if="feedback" class="action-feedback" role="status" aria-live="polite">{{ feedback }}</p>
      <div v-if="queueError" class="inline-alert error" role="alert"><span>{{ queueError }}</span><button type="button" @click="loadQueue">重试</button></div>
      <div v-if="actionError" class="inline-alert error" role="alert"><span>{{ actionError }}</span></div>
      <div v-if="queueLoading" class="queue-list" aria-label="正在加载审核队列"><div v-for="n in 3" :key="n" class="skeleton queue-skeleton"></div></div>
      <div v-else-if="visibleItems.length" class="queue-list">
        <article v-for="item in visibleItems" :key="`${item.type}-${item.id}`" class="review-row">
          <div class="review-summary">
            <span class="review-kind">{{ label(item.type) }} #{{ item.id }}</span>
            <h3>{{ item.name }}</h3>
            <small>提交于 {{ formatTime(item.submittedAt) }}<span v-if="item.submittedBy">，{{ item.submittedBy }}</span></small>
          </div>
          <StatusTag :status="item.reviewStatus" compact />
          <div class="review-actions">
            <button class="primary-action small-action" type="button" :disabled="busyKey !== ''" @click="approve(item)">
              <LoaderCircle v-if="busyKey === `${item.type}:${item.id}`" class="auth-loading-icon" :size="16" aria-hidden="true" />
              <Check v-else :size="16" aria-hidden="true" />通过
            </button>
            <button class="secondary-action" type="button" :disabled="busyKey !== ''" @click="openReject(item, $event)"><X :size="16" aria-hidden="true" />驳回</button>
          </div>
        </article>
      </div>
      <div v-else class="queue-empty"><span>QUEUE CLEAR</span><h3>暂无待审核内容</h3><p>新的商品或活动送审后会出现在这里。</p></div>
    </section>

    <section class="review-audit" :aria-busy="logsLoading">
      <header class="audit-header">
        <div><ScrollText :size="24" aria-hidden="true" /><span><h2>最近审核记录</h2><p>展示最近 100 条审核结果。</p></span></div>
        <button class="icon-button" type="button" title="刷新审核记录" aria-label="刷新审核记录" :disabled="logsLoading" @click="loadLogs"><RefreshCw :size="17" aria-hidden="true" /></button>
      </header>
      <div class="audit-filters">
        <label><span>内容类型</span><select v-model="logType"><option value="ALL">全部</option><option value="PRODUCT">商品</option><option value="ACTIVITY">活动</option></select></label>
        <label><span>审核结果</span><select v-model="logResult"><option value="ALL">全部</option><option value="APPROVED">已通过</option><option value="REJECTED">已驳回</option></select></label>
        <strong>{{ logsLoading ? '--' : visibleLogs.length }} 条</strong>
      </div>
      <div v-if="logsError" class="inline-alert error" role="alert"><span>{{ logsError }}</span><button type="button" @click="loadLogs">重试</button></div>
      <div v-if="logsLoading" class="audit-loading" aria-label="正在加载审核记录"><div v-for="n in 4" :key="n" class="skeleton"></div></div>
      <div v-else-if="visibleLogs.length" class="audit-ledger">
        <div class="audit-row audit-head" aria-hidden="true"><span>内容</span><span>结果</span><span>审核人</span><span>处理时间</span><span>备注</span></div>
        <div v-for="log in visibleLogs" :key="log.id" class="audit-row">
          <span><small>内容</small><strong>{{ label(log.contentType) }} #{{ log.contentId }}</strong></span>
          <span><small>结果</small><StatusTag :status="log.result" compact /></span>
          <span><small>审核人</small><strong>{{ log.reviewerUsername || '未知账号' }}</strong></span>
          <span><small>处理时间</small>{{ formatTime(log.createdAt) }}</span>
          <span class="audit-reason"><small>备注</small>{{ log.reason || '无备注' }}</span>
        </div>
      </div>
      <div v-else class="audit-empty"><History :size="22" aria-hidden="true" /><span>当前筛选条件下暂无审核记录</span></div>
    </section>

    <section class="report-governance" :aria-busy="reportsLoading">
      <header class="audit-header"><div><Flag :size="24" /><span><h2>举报治理</h2><p>核对商品与评价举报，处理结果全程留痕。</p></span></div><button class="icon-button" type="button" title="刷新举报" aria-label="刷新举报" :disabled="reportsLoading" @click="loadReports"><RefreshCw :size="17" /></button></header>
      <div class="segment-row report-tabs" aria-label="举报处理状态"><button v-for="tab in [{ label: '待处理', value: 'PENDING' }, { label: '已成立', value: 'RESOLVED' }, { label: '已驳回', value: 'REJECTED' }, { label: '全部', value: 'ALL' }]" :key="tab.value" type="button" :class="{ active: reportStatus === tab.value }" @click="reportStatus = tab.value as ReportStatus | 'ALL'; loadReports()">{{ tab.label }}</button></div>
      <div v-if="reportsError" class="inline-alert error"><span>{{ reportsError }}</span><button type="button" @click="loadReports">重试</button></div>
      <div v-if="reportsLoading" class="report-list"><div v-for="n in 2" :key="n" class="skeleton report-skeleton"></div></div>
      <div v-else-if="reports.length" class="report-list">
        <article v-for="report in reports" :key="report.id" class="report-row">
          <div><span class="review-kind">{{ report.targetType === 'PRODUCT' ? '商品' : '评价' }} #{{ report.targetId }}</span><h3>{{ report.targetSummary || '目标内容不可用' }}</h3><p>{{ report.reason }}</p><small>{{ report.reporterNickname || `用户 ${report.reporterId}` }} · {{ formatTime(report.createdAt) }}</small></div>
          <StatusTag :status="report.status" compact />
          <div v-if="report.status === 'PENDING'" class="review-actions"><button class="primary-action small-action" type="button" :disabled="Boolean(reportBusyId)" @click="handleReport(report, 'resolve')"><Check :size="16" />确认成立</button><button class="secondary-action" type="button" :disabled="Boolean(reportBusyId)" @click="handleReport(report, 'reject')"><X :size="16" />驳回</button></div>
          <div v-else class="report-result"><strong>{{ report.handleResult }}</strong><small>{{ report.handlerNickname || '管理员' }} · {{ formatTime(report.handledAt || report.createdAt) }}</small></div>
        </article>
      </div>
      <div v-else class="audit-empty"><Flag :size="22" /><span>当前筛选条件下暂无举报</span></div>
    </section>

    <div v-if="rejecting" class="modal-backdrop" role="presentation" @click.self="closeReject" @keydown="handleModalKeydown">
      <section class="confirm-modal" role="dialog" aria-modal="true" aria-labelledby="reject-title">
        <header><div><span class="review-kind">{{ label(rejecting.type) }} #{{ rejecting.id }}</span><h2 id="reject-title">驳回审核</h2></div><button class="icon-button" type="button" title="关闭" aria-label="关闭" :disabled="Boolean(busyKey)" @click="closeReject"><X :size="18" aria-hidden="true" /></button></header>
        <p>驳回原因会保留在审核记录中。</p>
        <label class="reject-reason"><span>驳回原因</span><textarea ref="rejectReasonInput" v-model="reason" maxlength="500" rows="4" placeholder="例如：请补充材质说明或调整宣传图片"></textarea></label>
        <p v-if="actionError" class="inline-alert error" role="alert">{{ actionError }}</p>
        <div class="confirm-actions"><button class="secondary-action" type="button" :disabled="Boolean(busyKey)" @click="closeReject">取消</button><button class="primary-action" type="button" :disabled="!reason.trim() || Boolean(busyKey)" @click="submitReject"><LoaderCircle v-if="busyKey" class="auth-loading-icon" :size="16" aria-hidden="true" />确认驳回</button></div>
      </section>
    </div>
  </div>
</template>
