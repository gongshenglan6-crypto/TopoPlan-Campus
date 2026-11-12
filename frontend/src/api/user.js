import { apiRequest, apiStream } from './http'

export function listProjects() {
  return apiRequest('/api/projects')
}

export function getUserSubscription() {
  return apiRequest('/api/user/subscription')
}

export function listUserPlans() {
  return apiRequest('/api/user/plans').then((data) => data?.items ?? data ?? [])
}

export function getProject(projectId) {
  return apiRequest(`/api/projects/${projectId}`)
}

export function createProject(project) {
  return apiRequest('/api/projects', { method: 'POST', body: project })
}

export function updateProject(projectId, project) {
  return apiRequest(`/api/projects/${projectId}`, { method: 'PUT', body: project })
}

export function duplicateProject(projectId) {
  return apiRequest(`/api/projects/${projectId}/duplicate`, { method: 'POST' })
}

export function deleteProject(projectId) {
  return apiRequest(`/api/projects/${projectId}`, { method: 'DELETE' })
}

export function listChatSessions() {
  return apiRequest('/api/chat/sessions')
}

export function listConversations() {
  return apiRequest('/api/conversations')
}

export function createConversation(payload = {}) {
  return apiRequest('/api/conversations', { method: 'POST', body: payload })
}

export function getConversation(conversationId) {
  return apiRequest(`/api/conversations/${conversationId}`)
}

export function getConversationMessages(conversationId) {
  return apiRequest(`/api/conversations/${conversationId}/messages`)
}
export function editConversationMessage(conversationId, messageId, content) {
  return apiRequest(`/api/conversations/${conversationId}/messages/${messageId}`, {
    method: 'PATCH',
    body: { content }
  })
}

export function updateConversation(conversationId, payload) {
  return apiRequest(`/api/conversations/${conversationId}`, { method: 'PATCH', body: payload })
}

export function deleteConversation(conversationId) {
  return apiRequest(`/api/conversations/${conversationId}`, { method: 'DELETE' })
}

export function sendConversationMessage(conversationId, content, modelId = null, clientMessageId = null) {
  return apiRequest(`/api/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: { content, modelId, clientMessageId: clientMessageId || null }
  })
}

export function streamConversation(conversationId, content, modelId, clientMessageId, { signal, onEvent } = {}) {
  return apiStream(`/api/conversations/${conversationId}/stream`, {
    body: { content, modelId: modelId || null, clientMessageId: clientMessageId || null },
    signal,
    onEvent
  })
}
export function streamRegenerateConversation(conversationId, messageId, modelId = null, clientMessageId = null, { signal, onEvent } = {}) {
  return apiStream(`/api/conversations/${conversationId}/regenerate`, {
    body: { messageId, modelId: modelId || null, clientMessageId: clientMessageId || null },
    signal,
    onEvent
  })
}

export function ensureProjectChatSession(projectId) {
  return apiRequest(`/api/projects/${encodeURIComponent(projectId)}/chat/session`, { method: 'POST' })
}

export function listProjectChatSessions(projectId) {
  return apiRequest(`/api/projects/${projectId}/chat/sessions`)
}

export function createChatSession(projectId, payload) {
  return apiRequest('/api/chat/conversations', {
    method: 'POST',
    body: { ...payload, projectPublicId: projectId ? String(projectId) : null }
  })
}

export function getProjectChatMessages(projectId, conversationId) {
  return apiRequest(`/api/projects/${encodeURIComponent(projectId)}/ai/conversations/${encodeURIComponent(conversationId)}/messages`)
}

export function getChatMessages(sessionId) {
  return apiRequest(`/api/chat/conversations/${encodeURIComponent(sessionId)}/messages`)
}

export function sendChatMessage(sessionId, content) {
  return apiRequest(`/api/chat/sessions/${sessionId}/messages`, { method: 'POST', body: { content } })
}

export function listChatModels() {
  return apiRequest('/api/chat/models').then((data) => data?.items ?? data)
}

export function streamChatCompletion(conversationId, message, modelId, { signal, onEvent } = {}) {
  return apiStream('/api/chat/stream', {
    body: { conversationId, message, modelId: modelId || null },
    signal,
    onEvent
  })
}

export function streamProjectChat({ conversationId, conversationPublicId = null, projectId, message, selectedNodeId = null, taskType = 'TOPOLOGY_QA', clientMessageId = null, fileIds = [], webSearch = false, signal, onEvent } = {}) {
  return apiStream('/api/chat/stream', {
    body: { conversationId: conversationPublicId ? null : conversationId, conversationPublicId: conversationPublicId ? String(conversationPublicId) : null, projectPublicId: projectId ? String(projectId) : null, message, selectedNodeId, taskType, clientMessageId, fileIds: Array.isArray(fileIds) ? fileIds : [], webSearch: Boolean(webSearch) },
    signal,
    onEvent
  })
}

export function streamProjectTopologyPlan({ projectId, message, conversationPublicId = null, selectedNodeId = null, clientMessageId = null, fileIds = [], webSearch = false, signal, onEvent } = {}) {
  const body = { message }
  if (conversationPublicId) body.conversationPublicId = String(conversationPublicId)
  if (selectedNodeId) body.selectedNodeId = String(selectedNodeId)
  if (clientMessageId) body.clientMessageId = String(clientMessageId)
  body.fileIds = Array.isArray(fileIds) ? fileIds : []
  body.webSearch = Boolean(webSearch)
  return apiStream(`/api/projects/${encodeURIComponent(projectId)}/ai/plan`, {
    body,
    signal,
    onEvent
  })
}

export function getTopology(projectId) {
  return apiRequest(`/api/projects/${projectId}/topology`)
}

export function saveTopology(projectId, topology) {
  return apiRequest(`/api/projects/${projectId}/topology`, { method: 'PUT', body: topology })
}

export function listTopologyVersions(projectId) {
  return apiRequest(`/api/projects/${projectId}/topology/versions`)
}

export function listTopologyResources() {
  return apiRequest('/api/topology/resources').then((data) => data?.items ?? data ?? [])
}

export function listAiTopologyResources() {
  return apiRequest('/api/topology/resources/ai-available').then((data) => data?.items ?? data ?? [])
}

export function checkTopology(projectId) {
  return apiRequest('/api/projects/' + projectId + '/topology/check', { method: 'POST' })
}

export function applyTopologyPlan(projectId, plan, planId = null) {
  return apiRequest('/api/projects/' + projectId + '/topology/apply-ai-plan', {
    method: 'POST',
    body: { planId: planId || 'plan-' + Date.now(), plan }
  })
}

export function getTopologyResource(resourceId) {
  return apiRequest(`/api/topology/resources/${encodeURIComponent(resourceId)}`)
}

export function uploadProjectFile(projectId, file) {
  const body = new FormData()
  body.append('file', file)
  return apiRequest(`/api/projects/${encodeURIComponent(projectId)}/files`, { method: 'POST', body })
}

export function listProjectFiles(projectId) {
  return apiRequest(`/api/projects/${encodeURIComponent(projectId)}/files`).then((data) => data?.items ?? data ?? [])
}
