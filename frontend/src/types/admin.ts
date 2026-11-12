export type TierKey = 'FREE' | 'STANDARD' | 'PRO'

export interface TierConfig {
  id: string
  name: string
  description: string
  monthlyTokenLimit: number
  maxMonthlyCalls: number
  maxContextWindow: number
  concurrentRequestsQps: number
  knowledgeBaseStorageMb: number
  dailyCallLimit: number
  enabled: boolean
}

export type KnowledgeDocumentStatus = 'processing' | 'ready' | 'failed'

export interface KnowledgeDocument {
  id: string | number
  name: string
  category: string
  format: 'pdf' | 'txt' | 'doc' | 'docx' | 'xlsx' | 'md'
  size: string
  uploader: string
  uploadedAt: string
  status: KnowledgeDocumentStatus
  chunks: number
}

export type BlacklistType = 'ip' | 'user_id' | 'keyword'
export type BanDuration = '24h' | '7d' | 'permanent'
export type GuardrailLevel = 1 | 2 | 3 | 4 | 5

export interface BlacklistEntry {
  id: string
  type: BlacklistType
  value: string
  triggerCount: number
  duration: BanDuration
  reason: string
  createdAt: string
  enabled: boolean
}

export type ModelCategory = 'text' | 'embedding' | 'multimodal'

export interface ModelChannel {
  id: string
  provider: string
  alias: string
  modelId: string
  category: ModelCategory
  status: 'healthy' | 'degraded' | 'disabled'
  latency: number
  concurrency: number
  maxConcurrency: number
  active: boolean
  baseUrl: string
  apiKey: string
  rpm: number
  tpm: number
}

export interface AlertRule {
  id: string
  label: string
  description: string
  enabled: boolean
  severity: 'info' | 'warning' | 'critical'
}
