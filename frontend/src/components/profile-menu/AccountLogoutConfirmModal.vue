<script setup>
import { LogOut } from '@lucide/vue'
import ModalShell from './ModalShell.vue'

defineProps({
  open: {
    type: Boolean,
    default: false
  }
})

defineEmits(['close', 'confirm'])
</script>

<template>
  <ModalShell :open="open" title="确认退出登录" description="退出后需要重新登录才能继续使用 TopoPlan。" @close="$emit('close')">
    <div class="account-logout-confirm">
      <p>确定要退出当前账号吗？</p>
      <div class="account-logout-confirm-actions">
        <button type="button" @click="$emit('close')">取消</button>
        <button class="is-danger" type="button" @click="$emit('confirm')"><LogOut :size="16" />退出登录</button>
      </div>
    </div>
  </ModalShell>
</template>
