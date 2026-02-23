import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Plus, Send, Trash2, Loader2 } from 'lucide-react'
import Shell from '@/components/Shell'
import { apiGet, apiSend } from '@/utils/http'
import type { AgentDetail, ChatMessage, ChatResponse, ChatSession } from '@/types/models'

function fmtTime(ms: number) {
  if (!ms) return ''
  const d = new Date(ms)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function MessageContent({ content }: { content: string }) {
  // If content is completely empty, it might be just starting to stream
  if (!content) {
      return (
      <div className="space-y-2">
        <div className="flex items-center gap-2 text-blue-400">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span className="text-sm font-medium">Thinking...</span>
        </div>
      </div>
    )
  }

  // Handle both "Final Answer:" and potential formatting variations or partial streams
  // Sometimes models might output "Final Answer: " or "Final Answer:"
  const idx = content.lastIndexOf('Final Answer:')
  
  // If no final answer yet, show "Thinking..." with details
  if (idx === -1) {
    return (
      <div className="space-y-2">
        <div className="flex items-center gap-2 text-blue-400">
          <Loader2 className="h-4 w-4 animate-spin" />
          <span className="text-sm font-medium">思考中...</span>
        </div>
        <details className="group rounded-lg border border-slate-800 bg-slate-950/50 p-2" open>
          <summary className="cursor-pointer text-xs font-medium text-slate-500 hover:text-slate-300 select-none">
            查看思考细节 (点击展开)
          </summary>
          <div className="mt-2 whitespace-pre-wrap text-xs text-slate-400 font-mono max-h-60 overflow-auto">{content}</div>
        </details>
      </div>
    )
  }

  // If final answer exists, hide thoughts by default
  const thoughts = content.slice(0, idx).trim()
  const answer = content.slice(idx + 13).trim()
  
  return (
    <div className="space-y-2">
      {thoughts && (
        <details className="group rounded-lg border border-slate-800 bg-slate-950/50 p-2">
          <summary className="cursor-pointer text-xs font-medium text-slate-500 hover:text-slate-300 select-none">
            思考过程 (点击展开)
          </summary>
          <div className="mt-2 whitespace-pre-wrap text-xs text-slate-400 font-mono max-h-60 overflow-auto">{thoughts}</div>
        </details>
      )}
      <div className="whitespace-pre-wrap text-slate-100">{answer}</div>
    </div>
  )
}

export default function ChatAgent() {
  const params = useParams()
  const agentId = String(params.agentId || '')

  const [agent, setAgent] = useState<AgentDetail | null>(null)
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [activeSessionId, setActiveSessionId] = useState<string>('')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [sending, setSending] = useState(false)
  const [err, setErr] = useState('')
  const creatingSessionRef = useRef(false)

  const bottomRef = useRef<HTMLDivElement | null>(null)

  const loadAgent = async () => {
    const a = await apiGet<AgentDetail>(`/api/agents/${encodeURIComponent(agentId)}`)
    setAgent(a)
  }

  const loadSessions = async () => {
    const s = await apiGet<ChatSession[]>(`/api/agents/${encodeURIComponent(agentId)}/chat/sessions`)
    setSessions(s)
    if (s.length && !activeSessionId) {
      setActiveSessionId(s[0].sessionId)
    }
  }

  const loadMessages = async (sid: string) => {
    const m = await apiGet<ChatMessage[]>(`/api/agents/${encodeURIComponent(agentId)}/chat/sessions/${encodeURIComponent(sid)}`)
    setMessages(m)
  }

  const refreshAll = async () => {
    setErr('')
    try {
      await Promise.all([loadAgent(), loadSessions()])
    } catch (e: any) {
      setErr(String(e.message || e))
    }
  }

  useEffect(() => {
    refreshAll()
  }, [agentId])

  useEffect(() => {
    if (activeSessionId) {
      if (creatingSessionRef.current) {
        creatingSessionRef.current = false
        return
      }
      loadMessages(activeSessionId).catch((e) => setErr(String(e.message || e)))
    } else {
      setMessages([])
    }
  }, [activeSessionId])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ block: 'end' })
  }, [messages.length])

  const newSession = () => {
    setActiveSessionId('')
    setMessages([])
  }

  const deleteSession = async (sid: string, e: React.MouseEvent) => {
    e.stopPropagation()
    if (!confirm('确定要删除该会话吗？')) return
    try {
      await apiSend(`/api/agents/${encodeURIComponent(agentId)}/chat/sessions/${encodeURIComponent(sid)}`, 'DELETE')
      if (activeSessionId === sid) {
        newSession()
      }
      await loadSessions()
    } catch (e: any) {
      setErr(String(e.message || e))
    }
  }

  const send = async () => {
    if (sending) return
    const text = input.trim()
    if (!text) return
    setErr('')
    setSending(true)
    const sid = activeSessionId
    
    // Optimistically add user message
    const newMsg: ChatMessage = {
      role: 'user',
      content: text,
      createdAt: Date.now(),
    }
    setMessages(prev => [...prev, newMsg])
    setInput('')

    try {
      const res = await fetch(`/api/agents/${encodeURIComponent(agentId)}/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          sessionId: sid,
          message: text,
          topK: 8,
          stream: true,
        })
      })

      if (!res.ok) {
        throw new Error(`Chat error: ${res.status}`)
      }
      
      if (!res.body) return

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let currentSessionId = sid
      
      // Add assistant placeholder
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: '',
        createdAt: Date.now(),
      }])

      let buffer = ''
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || '' // Keep incomplete line
        
        for (const line of lines) {
          if (line.startsWith('data: ')) {
            const dataStr = line.slice(6).trim()
            if (dataStr === '[DONE]') break
            try {
              const data = JSON.parse(dataStr)
              if (data.sessionId) {
                currentSessionId = data.sessionId
                if (!sid) {
                  creatingSessionRef.current = true
                  setActiveSessionId(currentSessionId)
                  loadSessions()
                }
              }
              if (data.delta) {
                 setMessages(prev => {
                   const last = prev[prev.length - 1]
                   if (last.role === 'assistant') {
                     return [...prev.slice(0, -1), { ...last, content: last.content + data.delta }]
                   }
                   return prev
                 })
              }
            } catch (e) {}
          }
        }
      }
      
      if (currentSessionId) {
        // loadMessages(currentSessionId) // Optional: reload to ensure consistency
        loadSessions()
      }
      
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setSending(false)
    }
  }

  const title = agent ? `对话：${agent.name}` : 'Agent 对话'
  const subtitle = useMemo(() => {
    const sk = (agent?.skillFiles || []).length
    const sr = (agent?.systemRuleFiles || []).length
    const tr = (agent?.triggerRuleFiles || []).length
    return `skills=${sk} · systemRules=${sr} · triggerRules=${tr}`
  }, [agent])

  return (
    <Shell title={title} subtitle={subtitle}>
      <div className="mb-4 flex items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/chat" className="inline-flex items-center gap-2 text-sm text-slate-300 hover:text-slate-100">
            <ArrowLeft className="h-4 w-4" />
            返回选择 Agent
          </Link>
          <Link to={`/agents/${encodeURIComponent(agentId)}`} className="text-sm text-slate-300 hover:text-slate-100">
            打开 Agent 配置
          </Link>
          <Link to="/rag" className="text-sm text-slate-300 hover:text-slate-100">
            RAG 调试
          </Link>
        </div>
        <button
          onClick={() => newSession()}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-sm text-slate-200 hover:bg-slate-800"
        >
          <Plus className="h-4 w-4" />
          新会话
        </button>
      </div>

      {err ? <div className="mb-4 rounded-xl border border-rose-900/60 bg-rose-950/20 px-4 py-3 text-sm text-rose-200">{err}</div> : null}

      <div className="grid gap-4 lg:grid-cols-3">
        <aside className="rounded-xl border border-slate-800 bg-slate-900 lg:col-span-1">
          <div className="flex items-center justify-between border-b border-slate-800 px-4 py-3">
            <div className="text-sm font-semibold">会话</div>
            <div className="text-xs text-slate-400">{sessions.length}</div>
          </div>
          <div className="max-h-[560px] overflow-auto p-2">
            {sessions.length === 0 ? (
              <div className="px-3 py-8 text-center text-sm text-slate-400">暂无会话</div>
            ) : (
              sessions.map((s) => (
                <button
                  key={s.sessionId}
                  onClick={() => setActiveSessionId(s.sessionId)}
                  className={`group relative w-full rounded-lg border px-3 py-2 text-left text-sm ${activeSessionId === s.sessionId ? 'border-blue-700 bg-blue-950/30 text-blue-100' : 'border-slate-800 bg-slate-950 text-slate-200 hover:bg-slate-900'}`}
                >
                  <div className="pr-6 font-semibold">{s.title || s.sessionId.slice(0, 8)}</div>
                  <div className="mt-1 text-xs text-slate-500">更新：{fmtTime(s.updatedAt)}</div>
                  <div
                    onClick={(e) => deleteSession(s.sessionId, e)}
                    className="absolute right-2 top-2 hidden rounded p-1 text-slate-400 hover:bg-rose-950 hover:text-rose-400 group-hover:block"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </div>
                </button>
              ))
            )}
          </div>
        </aside>

        <section className="rounded-xl border border-slate-800 bg-slate-900 lg:col-span-2">
          <div className="border-b border-slate-800 px-4 py-3 text-sm font-semibold">对话</div>
          <div className="max-h-[520px] overflow-auto p-4">
            {messages.length === 0 ? (
              <div className="py-12 text-center text-sm text-slate-400">开始提问吧</div>
            ) : (
              <div className="space-y-3">
                {messages.map((m, idx) => (
                  <div
                    key={idx}
                    className={`rounded-xl border px-4 py-3 text-sm ${m.role === 'user' ? 'border-slate-800 bg-slate-950' : 'border-emerald-900/40 bg-emerald-950/15'}`}
                  >
                    <div className="mb-1 text-xs text-slate-500">{m.role} · {fmtTime(m.createdAt)}</div>
                    {m.role === 'assistant' ? (
                      <MessageContent content={m.content} />
                    ) : (
                      <div className="whitespace-pre-wrap text-slate-100">{m.content}</div>
                    )}
                  </div>
                ))}
                <div ref={bottomRef} />
              </div>
            )}
          </div>
          <div className="border-t border-slate-800 p-4">
            <div className="flex gap-2">
              <input
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') send()
                }}
                placeholder="输入消息，回车发送"
                className="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-600"
              />
              <button
                onClick={() => send()}
                disabled={sending}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-500 disabled:opacity-60"
              >
                <Send className="h-4 w-4" />
                发送
              </button>
            </div>
            <div className="mt-2 text-xs text-slate-400">Agent 运行在 ReAct 模式下，支持 MCP 工具调用与多步推理。</div>
          </div>
        </section>
      </div>
    </Shell>
  )
}

