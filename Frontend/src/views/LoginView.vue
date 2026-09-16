<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Eye, EyeOff, GraduationCap, ImagePlus, LoaderCircle, MailCheck, RefreshCw, ShieldCheck, Store, X } from 'lucide-vue-next'
import { getCaptcha, resetPassword, sendMerchantRegistrationEmailCode, sendPasswordResetEmailCode, sendRegistrationEmailCode } from '../services/api'
import { useAuthStore } from '../stores/auth'
import type { UserRole } from '../types/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
type PortalRole = 'MERCHANT' | 'STUDENT' | 'ADMIN'
const portalRole = ref<PortalRole>('MERCHANT')
const mode = ref<'login' | 'register' | 'reset'>('login')
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const error = ref('')
const feedback = ref('')
const captchaId = ref('')
const captchaImage = ref('')
const captchaLoading = ref(false)
const captchaRemaining = ref(0)
const captchaNotice = ref('')
const emailSending = ref(false)
const resetSubmitting = ref(false)
const emailCodeSent = ref(false)
const emailCodeRemaining = ref(0)
const emailCodeNotice = ref('')
const cooldown = ref(0)
const merchantLogoInput = ref<HTMLInputElement | null>(null)
const merchantLogoFile = ref<File | null>(null)
const merchantLogoPreview = ref('')
const merchantLogoError = ref('')
const form = reactive({ email: '', password: '', confirmPassword: '', nickname: '', merchantName: '', captchaCode: '', emailCode: '' })
let cooldownTimer: number | undefined
let captchaTimer: number | undefined
let emailCodeTimer: number | undefined
let captchaExpiresAt = 0
let emailCodeExpiresAt = 0
let cooldownExpiresAt = 0
const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim()))
const passwordValid = computed(() => form.password.length >= 8 && form.password.length <= 72 && /[A-Za-z]/.test(form.password) && /\d/.test(form.password))
const passwordsMatch = computed(() => Boolean(form.confirmPassword) && form.password === form.confirmPassword)
const nicknameValid = computed(() => portalRole.value === 'MERCHANT'
  ? form.nickname.trim().length <= 6
  : form.nickname.trim().length >= 3 && form.nickname.trim().length <= 16)
const merchantNameValid = computed(() => form.merchantName.trim().length >= 2 && form.merchantName.trim().length <= 128)
const captchaAnswerValid = computed(() => /^[A-Za-z0-9]{4}$/.test(form.captchaCode))
const canSendEmailCode = computed(() => mode.value !== 'login' && emailValid.value && captchaAnswerValid.value && Boolean(captchaId.value) && captchaRemaining.value > 0 && cooldown.value === 0 && !emailSending.value)
const submitting = computed(() => auth.loading || resetSubmitting.value)
const canSubmit = computed(() => {
  if (mode.value === 'login') {
    return Boolean(emailValid.value && form.password && captchaAnswerValid.value && captchaId.value && captchaRemaining.value > 0)
  }
  if (mode.value === 'reset') {
    return emailValid.value && passwordValid.value && emailCodeSent.value
      && emailCodeRemaining.value > 0 && /^\d{6}$/.test(form.emailCode)
  }
  return passwordValid.value && passwordsMatch.value && nicknameValid.value
    && (portalRole.value !== 'MERCHANT' || merchantNameValid.value)
    && emailValid.value && emailCodeSent.value
    && emailCodeRemaining.value > 0 && /^\d{6}$/.test(form.emailCode)
})
const portalTitle = computed(() => ({ MERCHANT: '商家入口', STUDENT: '学生入口', ADMIN: '管理员入口' })[portalRole.value])
const heading = computed(() => {
  if (mode.value === 'reset') return '重新签发账号密码'
  if (mode.value === 'register') return portalRole.value === 'MERCHANT' ? '建立商家发行账号' : '领取你的校园凭证'
  return portalRole.value === 'MERCHANT' ? '进入商家工作台' : portalRole.value === 'ADMIN' ? '进入管理控制台' : '欢迎回到发行现场'
})
const headingDescription = computed(() => {
  if (mode.value === 'reset') return '验证账号邮箱后设置新密码。'
  if (mode.value === 'register') return portalRole.value === 'MERCHANT' ? '验证经营邮箱并登记商家名称。' : '使用邮箱验证码确认学生账号。'
  return `使用${portalTitle.value.replace('入口', '')}账号邮箱登录。`
})

function formatSeconds(total: number) {
  const minutes = Math.floor(total / 60)
  return `${minutes}:${String(total % 60).padStart(2, '0')}`
}

function secondsUntil(deadline: number) {
  return Math.max(0, Math.ceil((deadline - Date.now()) / 1000))
}

function syncCaptchaExpiry() {
  captchaRemaining.value = secondsUntil(captchaExpiresAt)
  if (captchaExpiresAt && captchaRemaining.value === 0) {
    window.clearInterval(captchaTimer)
    captchaExpiresAt = 0
    captchaId.value = ''
    captchaImage.value = ''
    form.captchaCode = ''
    captchaNotice.value = '图形验证码已过期，请刷新。'
  }
}

function syncEmailCodeExpiry() {
  emailCodeRemaining.value = secondsUntil(emailCodeExpiresAt)
  if (emailCodeExpiresAt && emailCodeRemaining.value === 0) {
    window.clearInterval(emailCodeTimer)
    emailCodeExpiresAt = 0
    emailCodeSent.value = false
    form.emailCode = ''
    emailCodeNotice.value = '邮箱验证码已过期，请重新发送。'
  }
}

function syncCooldown() {
  cooldown.value = secondsUntil(cooldownExpiresAt)
  if (cooldownExpiresAt && cooldown.value === 0) {
    window.clearInterval(cooldownTimer)
    cooldownExpiresAt = 0
  }
}

function syncVerificationTimers() {
  syncCaptchaExpiry()
  syncEmailCodeExpiry()
  syncCooldown()
}

function destination(role: UserRole) {
  const redirect = route.query.redirect
  // 只有站内绝对路径允许作为回跳地址，避免外部 URL 注入跳转。
  if (typeof redirect === 'string' && redirect.startsWith('/')) return redirect
  if (role === 'ADMIN') return '/admin'
  if (role === 'MERCHANT' || role === 'OPERATOR') return '/operator'
  return '/'
}

async function submit() {
  if (!canSubmit.value) return
  error.value = ''
  feedback.value = ''
  if (mode.value === 'reset') {
    resetSubmitting.value = true
    try {
      await resetPassword({
        email: form.email.trim(),
        emailCode: form.emailCode,
        newPassword: form.password,
        portalRole: portalRole.value,
      })
      form.password = ''
      form.emailCode = ''
      mode.value = 'login'
      await nextTick()
      feedback.value = '密码已重置，请使用新密码登录。'
    } catch (requestError) {
      error.value = requestError instanceof Error ? requestError.message : '密码重置失败，请重新验证邮箱'
    } finally {
      resetSubmitting.value = false
    }
    return
  }
  try {
    // 登录和注册都通过 Pinia action 调用 API，并在成功后统一持久化用户信息。
    if (mode.value === 'login') {
      await auth.signIn(form.email, form.password, captchaId.value, form.captchaCode, portalRole.value)
    } else if (portalRole.value === 'MERCHANT') {
      await auth.signUpMerchant({ email: form.email, password: form.password, confirmPassword: form.confirmPassword, displayName: form.nickname, merchantName: form.merchantName, emailCode: form.emailCode, logo: merchantLogoFile.value || undefined })
    } else {
      await auth.signUp({ email: form.email, password: form.password, confirmPassword: form.confirmPassword, nickname: form.nickname, emailCode: form.emailCode })
    }
    await router.push(destination(auth.user!.role))
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '认证失败，请检查账号信息'
    if (mode.value === 'login') await loadCaptcha()
  }
}

async function loadCaptcha() {
  if (captchaLoading.value) return
  const previousCaptchaId = captchaId.value
  window.clearInterval(captchaTimer)
  captchaExpiresAt = 0
  captchaRemaining.value = 0
  captchaLoading.value = true
  captchaNotice.value = ''
  form.captchaCode = ''
  try {
    const challenge = await getCaptcha(previousCaptchaId || undefined)
    captchaId.value = challenge.captchaId
    captchaImage.value = challenge.imageData
    captchaExpiresAt = Date.now() + challenge.expiresInSeconds * 1000
    syncCaptchaExpiry()
    captchaTimer = window.setInterval(syncCaptchaExpiry, 1000)
  } catch (requestError) {
    captchaId.value = ''
    captchaImage.value = ''
    captchaExpiresAt = 0
    captchaRemaining.value = 0
    error.value = requestError instanceof Error ? requestError.message : '图形验证码加载失败'
  } finally {
    captchaLoading.value = false
  }
}

function startCooldown(seconds: number) {
  window.clearInterval(cooldownTimer)
  cooldownExpiresAt = Date.now() + seconds * 1000
  syncCooldown()
  cooldownTimer = window.setInterval(syncCooldown, 1000)
}

function startEmailCodeExpiry(seconds: number) {
  window.clearInterval(emailCodeTimer)
  emailCodeExpiresAt = Date.now() + seconds * 1000
  emailCodeNotice.value = ''
  syncEmailCodeExpiry()
  emailCodeTimer = window.setInterval(syncEmailCodeExpiry, 1000)
}

function resetEmailVerification() {
  window.clearInterval(emailCodeTimer)
  window.clearInterval(cooldownTimer)
  emailCodeExpiresAt = 0
  cooldownExpiresAt = 0
  emailCodeSent.value = false
  emailCodeRemaining.value = 0
  cooldown.value = 0
  form.emailCode = ''
  emailCodeNotice.value = ''
  feedback.value = ''
}

function clearMerchantLogo() {
  if (merchantLogoPreview.value) URL.revokeObjectURL(merchantLogoPreview.value)
  merchantLogoFile.value = null
  merchantLogoPreview.value = ''
  merchantLogoError.value = ''
  if (merchantLogoInput.value) merchantLogoInput.value.value = ''
}

function selectMerchantLogo(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  merchantLogoError.value = ''
  if (!['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(file.type)) {
    merchantLogoError.value = '仅支持 PNG、JPEG、GIF 或 WebP 图片。'
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    merchantLogoError.value = '图片不能超过 5 MB。'
    return
  }
  if (merchantLogoPreview.value) URL.revokeObjectURL(merchantLogoPreview.value)
  merchantLogoFile.value = file
  merchantLogoPreview.value = URL.createObjectURL(file)
}

async function sendEmailCode() {
  if (!canSendEmailCode.value) return
  emailSending.value = true
  error.value = ''
  feedback.value = ''
  try {
    const payload = {
      email: form.email.trim(),
      captchaId: captchaId.value,
      captchaCode: form.captchaCode,
      portalRole: portalRole.value,
    }
    const receipt = mode.value === 'reset'
      ? await sendPasswordResetEmailCode(payload)
      : portalRole.value === 'MERCHANT'
        ? await sendMerchantRegistrationEmailCode(payload)
        : await sendRegistrationEmailCode(payload)
    form.emailCode = ''
    emailCodeSent.value = true
    feedback.value = '验证码已发送，请检查邮箱和垃圾邮件目录。'
    startCooldown(receipt.retryAfterSeconds)
    startEmailCodeExpiry(receipt.expiresInSeconds)
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '邮箱验证码发送失败'
  } finally {
    emailSending.value = false
  }
}

watch(mode, () => {
  error.value = ''
  feedback.value = ''
  form.captchaCode = ''
  form.password = ''
  form.confirmPassword = ''
  showPassword.value = false
  showConfirmPassword.value = false
  resetEmailVerification()
  loadCaptcha()
})
watch(portalRole, () => {
  mode.value = 'login'
  error.value = ''
  feedback.value = ''
  form.password = ''
  form.confirmPassword = ''
  form.captchaCode = ''
  resetEmailVerification()
  clearMerchantLogo()
  loadCaptcha()
})
watch(() => form.email, resetEmailVerification)
onMounted(() => {
  loadCaptcha()
  document.addEventListener('visibilitychange', syncVerificationTimers)
  window.addEventListener('focus', syncVerificationTimers)
})
onBeforeUnmount(() => {
  window.clearInterval(cooldownTimer)
  window.clearInterval(captchaTimer)
  window.clearInterval(emailCodeTimer)
  document.removeEventListener('visibilitychange', syncVerificationTimers)
  window.removeEventListener('focus', syncVerificationTimers)
  if (merchantLogoPreview.value) URL.revokeObjectURL(merchantLogoPreview.value)
})
</script>

<template>
  <div class="auth-page" :data-mode="mode" :data-portal="portalRole.toLowerCase()">
    <RouterLink class="auth-brand" to="/" aria-label="返回校集首页">
      <span>校</span>
      <strong>校集</strong>
      <small>成都信息工程大学</small>
    </RouterLink>
    <RouterLink class="auth-back" to="/"><ArrowLeft :size="17" />返回首页</RouterLink>

    <main class="auth-panel">
      <div class="auth-form-wrap">
        <div class="portal-selector" role="tablist" aria-label="选择登录身份">
          <button type="button" role="tab" :aria-selected="portalRole === 'MERCHANT'" :class="{ active: portalRole === 'MERCHANT' }" @click="portalRole = 'MERCHANT'">
            <Store :size="19" aria-hidden="true" /><span><strong>商家</strong><small>商品与活动运营</small></span>
          </button>
          <button type="button" role="tab" :aria-selected="portalRole === 'STUDENT'" :class="{ active: portalRole === 'STUDENT' }" @click="portalRole = 'STUDENT'">
            <GraduationCap :size="19" aria-hidden="true" /><span><strong>学生</strong><small>预约与购买</small></span>
          </button>
          <button type="button" role="tab" :aria-selected="portalRole === 'ADMIN'" :class="{ active: portalRole === 'ADMIN' }" @click="portalRole = 'ADMIN'">
            <ShieldCheck :size="19" aria-hidden="true" /><span><strong>管理员</strong><small>审核与治理</small></span>
          </button>
        </div>

        <div v-if="portalRole !== 'ADMIN'" class="auth-tabs" :data-active="mode" role="tablist" :aria-label="`${portalTitle}操作`">
          <button id="login-tab" role="tab" type="button" aria-controls="auth-credential-panel" :aria-selected="mode === 'login'" :class="{ active: mode === 'login' }" @click="mode = 'login'; error = ''">登录</button>
          <button id="register-tab" role="tab" type="button" aria-controls="auth-credential-panel" :aria-selected="mode === 'register'" :class="{ active: mode === 'register' }" @click="mode = 'register'; error = ''">注册{{ portalRole === 'MERCHANT' ? '商家' : '学生' }}账号</button>
        </div>
        <div v-else class="admin-login-notice"><ShieldCheck :size="18" /><span><strong>管理员仅限登录</strong><small>账号由系统数据库预置，不开放在线注册。</small></span></div>

        <div id="auth-credential-panel" class="credential-stage" role="tabpanel" :aria-label="`${portalTitle}${mode === 'register' ? '注册' : mode === 'reset' ? '密码重置' : '登录'}表单`">
          <Transition name="credential-swap">
            <div :key="mode" class="credential-pane" :data-credential-mode="mode">
              <header class="auth-form-heading">
                <h2>{{ heading }}</h2>
                <p>{{ headingDescription }}</p>
              </header>

              <form class="auth-form" :aria-busy="submitting" @submit.prevent="submit">
              <label><span>邮箱</span><input id="auth-email" v-model.trim="form.email" name="email" type="email" autocomplete="email" maxlength="128" required placeholder="name@example.com" /></label>
              <label v-if="mode === 'register' && portalRole === 'MERCHANT'">
                <span>商家名称</span>
                <input id="merchant-name" v-model.trim="form.merchantName" name="merchantName" autocomplete="organization" minlength="2" maxlength="128" required aria-describedby="merchant-name-note" :aria-invalid="Boolean(form.merchantName) && !merchantNameValid" placeholder="校园文创店铺或组织名称" />
                <small id="merchant-name-note" class="field-note" :class="{ invalid: Boolean(form.merchantName) && !merchantNameValid }">{{ form.merchantName && !merchantNameValid ? '商家名称需为 2-128 个字符。' : '用于商品、活动和订单运营归属。' }}</small>
              </label>
              <div v-if="mode === 'register' && portalRole === 'MERCHANT'" class="merchant-logo-field">
                <span class="merchant-logo-label">商家照片 <small>选填</small></span>
                <div class="merchant-logo-control">
                  <div class="merchant-logo-preview" aria-live="polite">
                    <img v-if="merchantLogoPreview" :src="merchantLogoPreview" alt="商家照片预览" />
                    <Store v-else :size="28" aria-hidden="true" />
                  </div>
                  <div class="merchant-logo-actions">
                    <input ref="merchantLogoInput" class="visually-hidden" type="file" accept="image/png,image/jpeg,image/gif,image/webp" @change="selectMerchantLogo" />
                    <button class="secondary-action" type="button" @click="merchantLogoInput?.click()"><ImagePlus :size="17" />{{ merchantLogoFile ? '更换照片' : '选择照片' }}</button>
                    <button v-if="merchantLogoFile" class="icon-button" type="button" title="移除照片" aria-label="移除商家照片" @click="clearMerchantLogo"><X :size="17" /></button>
                    <small :class="['field-note', { invalid: merchantLogoError }]">{{ merchantLogoError || '用于商家工作台和店铺身份展示，最大 5 MB。' }}</small>
                  </div>
                </div>
              </div>
              <label v-if="mode === 'register'">
                <span>{{ portalRole === 'MERCHANT' ? '负责人显示名' : '昵称' }}</span>
                <input id="auth-nickname" v-model.trim="form.nickname" name="nickname" autocomplete="nickname" :minlength="portalRole === 'MERCHANT' ? undefined : 3" :maxlength="portalRole === 'MERCHANT' ? 6 : 16" :required="portalRole !== 'MERCHANT'" aria-describedby="nickname-note" :aria-invalid="Boolean(form.nickname) && !nicknameValid" :placeholder="portalRole === 'MERCHANT' ? '选填，最多 6 个字符' : '3-16 个字符'" />
                <small id="nickname-note" class="field-note" :class="{ invalid: Boolean(form.nickname) && !nicknameValid }">{{ form.nickname && !nicknameValid ? (portalRole === 'MERCHANT' ? '负责人显示名最多 6 个字符。' : '显示名需为 3-16 个字符。') : portalRole === 'MERCHANT' ? '选填，用于商家工作台和操作记录。' : '用于订单和提货信息展示。' }}</small>
              </label>
              <label>
                <span>密码</span>
                <span class="password-field">
                  <input id="auth-password" v-model="form.password" name="password" :type="showPassword ? 'text' : 'password'" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" :minlength="mode === 'login' ? 1 : 8" maxlength="72" required :aria-describedby="mode !== 'login' ? 'password-note' : undefined" :aria-invalid="mode !== 'login' && Boolean(form.password) && !passwordValid" :placeholder="mode === 'login' ? '输入密码' : '8-72 位，包含字母和数字'" />
                  <button type="button" :class="{ active: showPassword }" :title="showPassword ? '隐藏密码' : '显示密码'" :aria-label="showPassword ? '隐藏密码' : '显示密码'" :aria-pressed="showPassword" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="19" /><Eye v-else :size="19" /></button>
                </span>
                <small v-if="mode !== 'login'" id="password-note" class="field-note" :class="{ invalid: Boolean(form.password) && !passwordValid }">{{ form.password && !passwordValid ? '密码需为 8-72 位，并同时包含字母和数字。' : '请勿使用与校园统一身份认证相同的密码。' }}</small>
              </label>
              <label v-if="mode === 'register'">
                <span>确认密码</span>
                <span class="password-field">
                  <input id="confirm-password" v-model="form.confirmPassword" name="confirmPassword" :type="showConfirmPassword ? 'text' : 'password'" autocomplete="new-password" minlength="8" maxlength="72" required aria-describedby="confirm-password-note" :aria-invalid="Boolean(form.confirmPassword) && !passwordsMatch" placeholder="再次输入密码" />
                  <button type="button" :class="{ active: showConfirmPassword }" :title="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'" :aria-label="showConfirmPassword ? '隐藏确认密码' : '显示确认密码'" :aria-pressed="showConfirmPassword" @click="showConfirmPassword = !showConfirmPassword"><EyeOff v-if="showConfirmPassword" :size="19" /><Eye v-else :size="19" /></button>
                </span>
                <small id="confirm-password-note" class="field-note" :class="{ invalid: Boolean(form.confirmPassword) && !passwordsMatch }">{{ form.confirmPassword && !passwordsMatch ? '两次输入的密码不一致。' : '请再次输入密码以确认无误。' }}</small>
              </label>
              <template v-if="mode === 'login' || !emailCodeSent">
              <div class="captcha-row">
                <label>
                  <span>图形验证码</span>
                  <input id="captcha-code" v-model.trim="form.captchaCode" name="captchaCode" autocomplete="off" maxlength="4" required aria-describedby="captcha-status" placeholder="输入 4 位字符" />
                </label>
                <button class="captcha-image" type="button" :disabled="captchaLoading" title="刷新图形验证码" aria-label="刷新图形验证码" @click="loadCaptcha">
                  <LoaderCircle v-if="captchaLoading" class="auth-loading-icon" :size="20" />
                  <img v-else-if="captchaImage" :src="captchaImage" alt="图形验证码" />
                  <RefreshCw v-else :size="20" />
                </button>
              </div>
              <div class="captcha-meta">
                <span>看不清可点击图片刷新</span>
                <span id="captcha-status" role="status">{{ captchaNotice || (captchaRemaining ? `有效期 ${formatSeconds(captchaRemaining)}` : '正在获取验证码') }}</span>
              </div>
              </template>
              <div v-if="mode !== 'login'" class="email-code-row">
                <label><span>邮箱验证码</span><input id="email-code" v-model.trim="form.emailCode" name="emailCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6" required aria-describedby="email-code-status" placeholder="6 位数字" /></label>
                <button class="secondary-action email-code-button" type="button" :disabled="!canSendEmailCode" @click="sendEmailCode">
                  <LoaderCircle v-if="emailSending" class="auth-loading-icon" :size="17" />
                  <MailCheck v-else :size="17" />
                  {{ emailSending ? '发送中…' : cooldown ? `${cooldown}s 后重发` : emailCodeSent ? '重新发送' : '发送验证码' }}
                </button>
              </div>
              <p v-if="mode !== 'login' && (emailCodeSent || emailCodeNotice)" id="email-code-status" class="verification-status" role="status">{{ emailCodeNotice || `邮箱验证码有效期 ${formatSeconds(emailCodeRemaining)}` }}</p>
              <Transition name="feedback">
                <p v-if="error" class="form-error" role="alert">{{ error }}</p>
              </Transition>
              <Transition name="feedback"><p v-if="feedback" class="form-success" role="status">{{ feedback }}</p></Transition>
              <button class="primary-action wide-action auth-submit" type="submit" :disabled="!canSubmit || submitting">
                <LoaderCircle v-if="submitting" class="auth-loading-icon" :size="18" aria-hidden="true" />
                {{ submitting ? '正在验证…' : mode === 'login' ? `登录${portalTitle}` : mode === 'register' ? `注册${portalTitle}` : '重置密码' }}
                <ArrowRight v-if="!submitting" :size="19" />
              </button>
              <button v-if="mode === 'login'" class="auth-reset-link" type="button" @click="mode = 'reset'">忘记密码？使用邮箱验证码找回</button>
              <button v-else-if="mode === 'reset'" class="auth-reset-link" type="button" @click="mode = 'login'">返回邮箱登录</button>
              </form>
            </div>
          </Transition>
        </div>

      </div>
    </main>
  </div>
</template>
