<script setup>
import { ref } from 'vue'
import { Copy, Link2, Trash2 } from '@lucide/vue'
import ModalShell from './ModalShell.vue'

defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
const copiedId = ref(null)
const links = ref([
  { id: 1, title: '三层办公楼网络规划', url: 'https://topoplan.local/share/office-3f' },
  { id: 2, title: '园区无线覆盖方案', url: 'https://topoplan.local/share/campus-wifi' }
])

async function copyLink(item) {
  try {
    await navigator.clipboard.writeText(item.url)
  } catch {
    const input = document.createElement('textarea')
    input.value = item.url
    input.style.position = 'fixed'
    input.style.opacity = '0'
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    input.remove()
  }
  copiedId.value = item.id
  window.setTimeout(() => { copiedId.value = null }, 1500)
}

function unshare(itemId) {
  links.value = links.value.filter((item) => item.id !== itemId)
}
</script>

<template>
  <ModalShell :open="open" title="我的分享链接" description="管理已公开的网络方案链接。" @close="$emit('close')">
    <div class="profile-share-list">
      <article v-for="item in links" :key="item.id" class="profile-share-row">
        <Link2 :size="18" />
        <div>
          <strong>{{ item.title }}</strong>
          <span>{{ item.url }}</span>
        </div>
        <button type="button" @click="copyLink(item)"><Copy :size="15" /> {{ copiedId === item.id ? '已复制' : '复制' }}</button>
        <button class="is-danger icon-only" type="button" aria-label="取消分享" @click="unshare(item.id)"><Trash2 :size="16" /></button>
      </article>
      <p v-if="links.length === 0" class="profile-empty-state">当前没有已分享的方案链接。</p>
    </div>
  </ModalShell>
</template>
