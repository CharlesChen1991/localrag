export type YamlFile = {
  name: string
  mtime?: number
  size?: number
}

export type YamlRead = {
  name: string
  content: string
  mtime?: number
  size?: number
  missing?: boolean
}

export type YamlTemplates = Record<string, string>

export type AgentSummary = {
  agentId: string
  name: string
  description?: string
  tags: string[]
  skillCount: number
  ruleCount: number
  createdAt: number
  updatedAt: number
}

export type AgentDetail = {
  agentId: string
  name: string
  description?: string
  tags: string[]
  skillFiles: string[]
  systemRuleFiles: string[]
  triggerRuleFiles: string[]
  createdAt: number
  updatedAt: number
}

export type ServiceCheck = {
  enabled: boolean
  ok: boolean
  detail?: string
}

export type ServicesHealth = {
  milvus: ServiceCheck & { ready?: boolean; collection?: string }
  elasticsearch: ServiceCheck & { index?: string; indexReady?: boolean }
}

export type ChatSession = {
  sessionId: string
  title?: string
  createdAt: number
  updatedAt: number
}

export type ChatMessage = {
  role: 'user' | 'assistant' | string
  content: string
  createdAt: number
}

export type ChatCitation = {
  chunkId: string
  path: string
  startPos?: string
  endPos?: string
}

export type ChatResponse = {
  sessionId: string
  answer: string
  citations: ChatCitation[]
}

export type RagItem = {
  chunkId: string
  path: string
  preview: string
}

export type RagSearchResponse = {
  items: RagItem[]
  topK: number
  milvusEnabled: boolean
  esEnabled: boolean
}
