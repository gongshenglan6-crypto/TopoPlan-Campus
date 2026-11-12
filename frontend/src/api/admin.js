import { apiRequest } from './http'

export function getAdminDashboard() {
  return apiRequest('/api/admin/dashboard')
}

export function listAdminUsers({ page = 0, size = 50, keyword = '' } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword.trim()) params.set('keyword', keyword.trim())
  return apiRequest(`/api/admin/users?${params.toString()}`)
}

export function createAdminUser(payload) {
  return apiRequest('/api/admin/users', { method: 'POST', body: payload })
}

export function updateAdminUser(id, payload) {
  return apiRequest(`/api/admin/users/${id}`, { method: 'PATCH', body: payload })
}

export function setAdminUserStatus(id, status) {
  return apiRequest(`/api/admin/users/${id}/status`, { method: 'POST', body: { status } })
}

export function resetAdminUserPassword(id) {
  return apiRequest(`/api/admin/users/${id}/password`, { method: 'POST' })
}

export function listAdminLoginRecords({
  page = 0,
  size = 10,
  keyword = '',
  userId,
  accountType = '',
  result = '',
  riskStatus = '',
  loginMethod = '',
  startAt = '',
  endAt = ''
} = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword.trim()) params.set('keyword', keyword.trim())
  if (userId !== undefined && userId !== null && userId !== '') params.set('userId', String(userId))
  if (accountType && accountType !== '全部') params.set('accountType', accountType)
  if (result && result !== '全部') params.set('result', result)
  if (riskStatus && riskStatus !== '全部') params.set('riskStatus', riskStatus)
  if (loginMethod && loginMethod !== '全部') params.set('loginMethod', loginMethod)
  if (startAt) params.set('startAt', startAt)
  if (endAt) params.set('endAt', endAt)
  return apiRequest(`/api/admin/login-records?${params.toString()}`)
}

export function getAdminLoginRecordStats() {
  return apiRequest('/api/admin/login-records/stats')
}

function listResource(path, options) {
  return apiRequest(path, options).then((data) => data?.items ?? data)
}

export function getBillingOverview({ range = 'month' } = {}) {
  return apiRequest(`/api/admin/billing/overview?range=${encodeURIComponent(range)}`)
}
export function saveBillingOverview(payload) { return apiRequest('/api/admin/billing/overview', { method: 'PUT', body: payload }) }
export function listBillingTiers() { return listResource('/api/admin/billing/tiers') }
export function saveBillingTier(id, values) { return apiRequest(`/api/admin/billing/tiers/${id}`, { method: 'PUT', body: values }) }
export function listBillingQuotas() { return listResource('/api/admin/billing/user-quotas') }
export function saveBillingQuota(id, values) { return apiRequest(`/api/admin/billing/user-quotas/${id}`, { method: 'PUT', body: values }) }

export function listKnowledgeAssets() { return listResource('/api/admin/knowledge-assets') }
export function createKnowledgeAsset(payload) { return apiRequest('/api/admin/knowledge-assets', { method: 'POST', body: payload }) }
export function deleteKnowledgeAsset(id) { return apiRequest(`/api/admin/knowledge-assets/${id}`, { method: 'DELETE' }) }
export function getKnowledgeOverview() { return apiRequest('/api/admin/knowledge/overview') }
export function listKnowledgeDocuments() { return listResource('/api/admin/knowledge/documents') }
export function uploadKnowledgeDocument(file, category, uploader = '系统管理员') {
  const body = new FormData()
  body.append('file', file)
  body.append('category', category)
  body.append('uploader', uploader)
  return apiRequest('/api/admin/knowledge/documents/upload', { method: 'POST', body })
}
export function reparseKnowledgeDocument(id) { return apiRequest(`/api/admin/knowledge/documents/${id}/reparse`, { method: 'POST' }) }
export function deleteKnowledgeDocument(id) { return apiRequest(`/api/admin/knowledge/documents/${id}`, { method: 'DELETE' }) }
export function getKnowledgeRagConfig() { return apiRequest('/api/admin/knowledge/rag-config') }
export function saveKnowledgeRagConfig(payload) { return apiRequest('/api/admin/knowledge/rag-config', { method: 'PUT', body: payload }) }
export function testKnowledgeRetrieval(payload) { return apiRequest('/api/admin/knowledge/retrieval-test', { method: 'POST', body: payload }) }
export function listDeviceProfiles() { return listResource('/api/admin/device-profiles') }
export function createDeviceProfile(payload) { return apiRequest('/api/admin/device-profiles', { method: 'POST', body: payload }) }
export function updateDeviceProfile(id, payload) { return apiRequest(`/api/admin/device-profiles/${id}`, { method: 'PATCH', body: payload }) }
export function listTopologyResourcesForAdmin() { return listResource('/api/admin/topology-resources') }
export function getTopologyResourceForAdmin(id) { return apiRequest(`/api/admin/topology-resources/${encodeURIComponent(id)}`) }
export function createTopologyResource(payload) { return apiRequest('/api/admin/topology-resources', { method: 'POST', body: payload }) }
export function updateTopologyResource(id, payload) { return apiRequest(`/api/admin/topology-resources/${encodeURIComponent(id)}`, { method: 'PATCH', body: payload }) }
export function listTopologyResourceCategories() { return apiRequest('/api/admin/topology-resources/categories') }
export function uploadTopologyResourceIcon(file) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest('/api/admin/topology-resources/icon/upload', { method: 'POST', body })
}

export function listModelChannels() { return listResource('/api/admin/model-channels') }
export function createModelChannel(payload) { return apiRequest('/api/admin/model-channels', { method: 'POST', body: payload }) }
export function updateModelChannel(id, payload) { return apiRequest(`/api/admin/model-channels/${id}`, { method: 'PATCH', body: payload }) }
export function setModelChannelStatus(id, enabled) { return apiRequest(`/api/admin/model-channels/${id}/status`, { method: 'PATCH', body: { enabled } }) }
export function testModelChannel(id) { return apiRequest(`/api/admin/model-channels/${id}/test`, { method: 'POST' }) }
export function setModelChannelPriority(id, priority) { return apiRequest(`/api/admin/model-channels/${id}/priority`, { method: 'PATCH', body: { priority } }) }
export function setModelChannelPrimary(id) { return apiRequest(`/api/admin/model-channels/${id}/set-primary`, { method: 'POST' }) }
export function moveModelChannel(id, direction) { return apiRequest(`/api/admin/model-channels/${id}/${direction === 'up' ? 'move-up' : 'move-down'}`, { method: 'POST' }) }
export function deleteModelChannel(id, force = false) { return apiRequest(`/api/admin/model-channels/${id}`, { method: 'DELETE', body: { force } }) }
export function listPrompts() { return listResource('/api/admin/prompts') }
export function createPrompt(payload) { return apiRequest('/api/admin/prompts', { method: 'POST', body: payload }) }
export function updatePrompt(id, payload) { return apiRequest(`/api/admin/prompts/${id}`, { method: 'PATCH', body: payload }) }
export function deletePrompt(id) { return apiRequest(`/api/admin/prompts/${id}`, { method: 'DELETE' }) }
export function setPromptStatus(id, active) { return apiRequest(`/api/admin/prompts/${id}/status`, { method: 'PATCH', body: { active } }) }
export function listPromptVersions(id) { return listResource(`/api/admin/prompts/${id}/versions`) }
export function restorePromptVersion(id, versionId) { return apiRequest(`/api/admin/prompts/${id}/restore`, { method: 'POST', body: { versionId } }) }
export function testPrompt(id, payload) { return apiRequest(`/api/admin/prompts/${id}/test`, { method: 'POST', body: payload }) }
export function restoreDefaultPrompt(id) { return apiRequest(`/api/admin/prompts/${id}/restore-default`, { method: 'POST' }) }

export function getRiskPolicy() { return apiRequest('/api/admin/security/risk-policy') }
export function saveRiskPolicy(payload) { return apiRequest('/api/admin/security/risk-policy', { method: 'PUT', body: payload }) }
export function getSecurityOverview() { return apiRequest('/api/admin/security/overview') }
export function updateSecurityEventStatus(id, status) { return apiRequest(`/api/admin/security/events/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }) }
export function listRiskBlacklist() { return listResource('/api/admin/risk-blacklist') }
export function createRiskBlacklistEntry(payload) { return apiRequest('/api/admin/risk-blacklist', { method: 'POST', body: payload }) }
export function updateRiskBlacklistEntry(id, payload) { return apiRequest(`/api/admin/risk-blacklist/${id}`, { method: 'PATCH', body: payload }) }
export function deleteRiskBlacklistEntry(id) { return apiRequest(`/api/admin/risk-blacklist/${id}`, { method: 'DELETE' }) }
export function getSystemRules() { return apiRequest('/api/admin/system-rules') }
export function saveSystemRules(payload) { return apiRequest('/api/admin/system-rules', { method: 'PUT', body: payload }) }
export function listAuditLogs(params = {}) { return apiRequest(`/api/admin/audit-logs?${new URLSearchParams(params).toString()}`) }
export function getMonitoring({ force = false } = {}) { return apiRequest(`/api/admin/monitoring?force=${force ? 'true' : 'false'}`) }
