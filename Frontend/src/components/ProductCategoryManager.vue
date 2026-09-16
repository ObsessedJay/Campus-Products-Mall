<script setup lang="ts">
import { reactive, ref } from 'vue'
import { LoaderCircle, Pencil, Plus, Power, PowerOff, Save, Trash2, X } from 'lucide-vue-next'
import { createCategory, deleteCategory, getManagedCategories, updateCategory } from '../services/api'
import type { Category, CategoryStatus } from '../types/api'

defineProps<{ categories: Category[]; disabled?: boolean }>()
const emit = defineEmits<{ close: []; changed: [categories: Category[]] }>()

const editingId = ref<number | 'new'>()
const deleteConfirmId = ref<number>()
const busy = ref(false)
const error = ref('')
const feedback = ref('')
const form = reactive({ name: '', sortOrder: '0', status: 'ACTIVE' as CategoryStatus })

function beginCreate() {
  editingId.value = 'new'
  deleteConfirmId.value = undefined
  Object.assign(form, { name: '', sortOrder: '0', status: 'ACTIVE' })
  error.value = ''
  feedback.value = ''
}

function beginEdit(category: Category) {
  editingId.value = category.id
  deleteConfirmId.value = undefined
  Object.assign(form, { name: category.name, sortOrder: String(category.sortOrder), status: category.status })
  error.value = ''
  feedback.value = ''
}

async function refresh(message: string) {
  emit('changed', await getManagedCategories())
  feedback.value = message
}

async function submit() {
  if (busy.value) return
  const name = form.name.trim()
  const sortOrder = Number(form.sortOrder)
  if (!name) {
    error.value = '请填写分类名称。'
    return
  }
  if (!Number.isInteger(sortOrder) || sortOrder < 0) {
    error.value = '排序值必须是大于或等于 0 的整数。'
    return
  }
  busy.value = true
  error.value = ''
  feedback.value = ''
  try {
    if (editingId.value === 'new') {
      await createCategory({ name, sortOrder })
      await refresh('分类已创建。')
    } else if (editingId.value) {
      await updateCategory(editingId.value, { name, sortOrder, status: form.status })
      await refresh('分类资料已更新。')
    }
    editingId.value = undefined
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '分类保存失败，请稍后重试'
  } finally { busy.value = false }
}

async function toggle(category: Category) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  feedback.value = ''
  try {
    const status: CategoryStatus = category.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
    await updateCategory(category.id, { name: category.name, sortOrder: category.sortOrder, status })
    await refresh(status === 'ACTIVE' ? '分类已启用。' : '分类已停用。')
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '分类状态更新失败'
  } finally { busy.value = false }
}

async function remove(category: Category) {
  if (deleteConfirmId.value !== category.id) {
    deleteConfirmId.value = category.id
    editingId.value = undefined
    return
  }
  busy.value = true
  error.value = ''
  feedback.value = ''
  try {
    await deleteCategory(category.id)
    deleteConfirmId.value = undefined
    await refresh('分类已删除。')
  } catch (requestError) {
    error.value = requestError instanceof Error ? requestError.message : '分类删除失败'
  } finally { busy.value = false }
}
</script>

<template>
  <section class="category-manager" aria-labelledby="category-manager-title">
    <header>
      <div><h3 id="category-manager-title">商品分类</h3><p>调整目录名称、顺序和公开状态</p></div>
      <span class="category-header-actions">
        <button class="secondary-action" type="button" :disabled="disabled || busy" @click="beginCreate"><Plus :size="16" />新增</button>
        <button class="icon-button" type="button" title="关闭分类管理" aria-label="关闭分类管理" @click="emit('close')"><X :size="18" /></button>
      </span>
    </header>

    <form v-if="editingId" class="category-editor" @submit.prevent="submit">
      <label><span>分类名称</span><input v-model.trim="form.name" maxlength="64" required /></label>
      <label><span>目录顺序</span><input v-model="form.sortOrder" type="number" min="0" step="1" inputmode="numeric" required /></label>
      <label v-if="editingId !== 'new'"><span>公开状态</span><select v-model="form.status"><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></label>
      <span class="category-editor-actions">
        <button class="secondary-action" type="button" :disabled="busy" @click="editingId = undefined">取消</button>
        <button class="primary-action" type="submit" :disabled="disabled || busy"><LoaderCircle v-if="busy" class="auth-loading-icon" :size="16" /><Save v-else :size="16" />保存</button>
      </span>
    </form>

    <p v-if="error" class="inline-alert error" role="alert">{{ error }}</p>
    <p v-if="feedback" class="action-feedback" role="status">{{ feedback }}</p>
    <div class="category-list">
      <div v-for="category in categories" :key="category.id" class="category-row" :class="{ inactive: category.status === 'INACTIVE' }">
        <span><strong>{{ category.name }}</strong><small>顺序 {{ category.sortOrder }} · {{ category.status === 'ACTIVE' ? '公开' : '已停用' }}</small></span>
        <span class="product-row-actions">
          <button class="icon-button" type="button" title="编辑分类" :aria-label="`编辑 ${category.name}`" :disabled="disabled || busy" @click="beginEdit(category)"><Pencil :size="16" /></button>
          <button class="icon-button" type="button" :title="category.status === 'ACTIVE' ? '停用分类' : '启用分类'" :aria-label="`${category.status === 'ACTIVE' ? '停用' : '启用'} ${category.name}`" :disabled="disabled || busy" @click="toggle(category)"><PowerOff v-if="category.status === 'ACTIVE'" :size="16" /><Power v-else :size="16" /></button>
          <button class="icon-button danger" type="button" :class="{ confirming: deleteConfirmId === category.id }" :title="deleteConfirmId === category.id ? '再次点击确认删除' : '删除分类'" :aria-label="deleteConfirmId === category.id ? `确认删除 ${category.name}` : `删除 ${category.name}`" :disabled="disabled || busy" @click="remove(category)"><Trash2 :size="16" /></button>
        </span>
        <p v-if="deleteConfirmId === category.id" class="category-delete-note">再次点击删除；已被商品使用的分类只能停用。</p>
      </div>
      <div v-if="!categories.length" class="product-table-empty"><strong>暂无分类</strong><span>创建首个分类后即可编排商品目录。</span></div>
    </div>
  </section>
</template>
