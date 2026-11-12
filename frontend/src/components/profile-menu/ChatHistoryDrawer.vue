<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { Clock3, X } from '@lucide/vue'

const props = defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close', 'open-chat'])

const historyGroups = [
  { label: '今天', items: [{ id: 1, title: '三层办公楼网络规划', time: '10:24' }, { id: 2, title: '财务部 VLAN 隔离说明', time: '09:12' }] },
  { label: '昨天', items: [{ id: 3, title: '服务器区风险分析', time: '16:40' }, { id: 4, title: '无线 AP 覆盖建议', time: '14:06' }] },
  { label: '过去 7 天', items: [{ id: 5, title: '核心交换机双机热备讨论', time: '周一' }, { id: 6, title: '会议室网络改造清单', time: '周日' }] }
]

function handleKeydown(event) {
  if (props.open && event.key === 'Escape') emit('close')
}

function selectChat(chatId) {
  emit('open-chat', chatId)
  emit('close')
}

onMounted(() => document.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="profile-drawer">
      <div v-if="open" class="profile-drawer-backdrop" @pointerdown.self="$emit('close')">
        <aside class="profile-history-drawer" aria-label="历史对话记录">
          <header>
            <div>
              <h2>历史对话记录</h2>
              <p>按时间查看需求分析与方案问答。</p>
            </div>
            <button class="profile-dialog-close" type="button" aria-label="关闭历史记录" @click="$emit('close')"><X :size="18" /></button>
          </header>
          <div class="profile-history-timeline">
            <section v-for="group in historyGroups" :key="group.label" class="profile-history-group">
              <h3><Clock3 :size="15" /> {{ group.label }}</h3>
              <button v-for="item in group.items" :key="item.id" type="button" class="profile-history-item" @click="selectChat(item.id)">
                <span>{{ item.title }}</span>
                <time>{{ item.time }}</time>
              </button>
            </section>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>
