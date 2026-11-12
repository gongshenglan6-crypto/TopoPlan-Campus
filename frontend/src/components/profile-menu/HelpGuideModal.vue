<script setup>
import { BookOpen, CheckCircle2, Network, Sparkles } from '@lucide/vue'
import ModalShell from './ModalShell.vue'

defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close'])
</script>

<template>
  <ModalShell :open="open" title="帮助与使用指南" description="从需求描述到工程材料导出，按以下步骤完成项目规划。" @close="$emit('close')">
    <ol class="profile-help-steps">
      <li><span><BookOpen :size="17" /></span><div><strong>描述建设需求</strong><p>输入场景、楼层、部门、终端规模和已有资料。</p></div></li>
      <li><span><Sparkles :size="17" /></span><div><strong>确认 AI 分析</strong><p>核对 AI 整理后的区域、容量与关键约束。</p></div></li>
      <li><span><Network :size="17" /></span><div><strong>生成并优化拓扑</strong><p>查看设备、链路和风险建议，再完成调整。</p></div></li>
      <li><span><CheckCircle2 :size="17" /></span><div><strong>导出工程材料</strong><p>将拓扑、设备清单和方案说明汇总导出。</p></div></li>
    </ol>
  </ModalShell>
</template>
