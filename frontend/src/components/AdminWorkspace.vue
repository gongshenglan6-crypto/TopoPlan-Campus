<script setup>
import { FileCog, ScrollText, ShieldCheck } from '@lucide/vue'
import AdminDashboard from './admin/AdminDashboard.vue'
import AdminBillingQuota from './admin/AdminBillingQuota.vue'
import AdminDeviceLibrary from './admin/AdminDeviceLibrary.vue'
import AdminKnowledgeBase from './admin/AdminKnowledgeBase.vue'
import AdminKnowledgeDocuments from './admin/AdminKnowledgeDocuments.vue'
import AdminKnowledgeTest from './admin/AdminKnowledgeTest.vue'
import AdminLoginRecords from './admin/AdminLoginRecords.vue'
import AdminModelApi from './admin/AdminModelApi.vue'
import AdminSecurityRisk from './admin/AdminSecurityRisk.vue'
import AdminSystemManagement from './admin/AdminSystemManagement.vue'
import AdminPromptStudio from './admin/AdminPromptStudio.vue'
import AdminRolePermissions from './admin/AdminRolePermissions.vue'
import AdminRagConfig from './admin/AdminRagConfig.vue'
import AdminUsersPanel from './admin/AdminUsersPanel.vue'
import { logs } from '../data/mockData'

defineProps({
  activePage: {
    type: String,
    required: true
  },
  theme: {
    type: String,
    default: 'light'
  }
})

const billingPageTabs = {
  quota: 'overview',
  'quota-tiers': 'tiers',
  'quota-users': 'users'
}

const modelPages = ['ai-models']
const securityPages = ['security']
</script>

<template>
  <main class="admin-workspace" :class="{ 'is-prompt-page': activePage === 'prompts', 'is-ai-engine-page': modelPages.includes(activePage), 'is-billing-page': Boolean(billingPageTabs[activePage]), 'is-system-management-page': activePage === 'system-management', 'is-security-page': activePage === 'security', 'is-user-access-page': ['users', 'roles', 'login-records'].includes(activePage) }">
    <AdminDashboard v-if="activePage === 'dashboard'" :theme="theme" />
    <AdminUsersPanel v-else-if="activePage === 'users'" />
    <AdminLoginRecords v-else-if="activePage === 'login-records'" />
    <AdminRolePermissions v-else-if="activePage === 'roles'" />
    <AdminBillingQuota v-else-if="billingPageTabs[activePage]" :key="activePage" :initial-tab="billingPageTabs[activePage]" />
    <AdminSecurityRisk v-else-if="securityPages.includes(activePage)" />
    <AdminSystemManagement v-else-if="activePage === 'system-management'" />
    <AdminKnowledgeBase v-else-if="activePage === 'assets' || activePage === 'knowledge'" />
    <AdminKnowledgeDocuments v-else-if="activePage === 'knowledge-docs'" />
    <AdminRagConfig v-else-if="activePage === 'rag-config'" />
    <AdminKnowledgeTest v-else-if="activePage === 'knowledge-test'" />
    <AdminDeviceLibrary v-else-if="activePage === 'devices'" />
    <AdminModelApi v-else-if="modelPages.includes(activePage)" />
    <AdminPromptStudio v-else-if="activePage === 'prompts'" />

    <section v-else-if="activePage === 'templates'" class="admin-section-page">
      <header class="admin-page-intro compact">
        <div><p>交付规范与可视化文档</p><h2>导出模板</h2><span>设置工程方案文档的封面、章节和拓扑图版式，并保持团队一致的交付水准。</span></div>
        <div class="admin-section-icon"><FileCog :size="22" /></div>
      </header>
      <section class="admin-template-preview-grid"><article class="admin-card"><span>默认方案文档</span><h3>网络工程实施方案</h3><div class="admin-doc-sheet"><strong>TOPOPLAN</strong><span>01 项目背景与目标</span><span>02 需求分析</span><span>03 网络总体设计</span><span>04 设备与地址规划</span></div></article><article class="admin-card admin-template-rules"><ShieldCheck :size="22" /><h3>发布策略</h3><p>仅管理员可修改公共导出模板。更新后，新创建的项目将自动使用最新版本。</p><button type="button" class="admin-primary-button">编辑默认模板</button></article></section>
    </section>

    <section v-else class="admin-section-page">
      <header class="admin-page-intro compact">
        <div><p>审计与可观测性</p><h2>系统日志</h2><span>记录登录、上传、AI 调用、导出和后台配置变更。</span></div>
        <div class="admin-section-icon"><ScrollText :size="22" /></div>
      </header>
      <section class="admin-card admin-log-timeline"><article v-for="log in logs" :key="`${log.time}-${log.action}`"><time>{{ log.time }}</time><span></span><div><strong>{{ log.action }}</strong><p>{{ log.user }} · {{ log.result }}</p></div></article></section>
    </section>
  </main>
</template>
