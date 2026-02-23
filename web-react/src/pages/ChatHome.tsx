import { Link } from 'react-router-dom'
import { MessageCircle, RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import Shell from '@/components/Shell'
import { apiGet } from '@/utils/http'
import type { AgentSummary } from '@/types/models'

export default function ChatHome() {
  const [agents, setAgents] = useState<AgentSummary[]>([])
  const [err, setErr] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setErr('')
    setLoading(true)
    try {
      const a = await apiGet<AgentSummary[]>('/api/agents')
      setAgents(a)
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  return (
    <Shell
      title="Agent 对话"
      subtitle="选择一个 Agent 进入对话（真实数据）"
      right={
        <button
          onClick={() => load()}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-sm text-slate-200 hover:bg-slate-800 disabled:opacity-60"
        >
          <RefreshCw className="h-4 w-4" />
          刷新
        </button>
      }
    >
      {err ? <div className="mb-4 rounded-xl border border-rose-900/60 bg-rose-950/20 px-4 py-3 text-sm text-rose-200">{err}</div> : null}

      <div className="rounded-xl border border-slate-800 bg-slate-900">
        <div className="border-b border-slate-800 px-4 py-3 text-sm font-semibold">Agents</div>
        <div className="divide-y divide-slate-800">
          {agents.length === 0 ? (
            <div className="px-4 py-10 text-center text-sm text-slate-400">暂无 Agent，请先去 Agent Console 创建</div>
          ) : (
            agents
              .slice()
              .sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0))
              .map((a) => (
                <Link
                  key={a.agentId}
                  to={`/chat/${encodeURIComponent(a.agentId)}`}
                  className="flex items-center justify-between gap-4 px-4 py-3 hover:bg-slate-950"
                >
                  <div>
                    <div className="text-sm font-semibold text-slate-100">{a.name}</div>
                    {a.description ? <div className="mt-1 text-xs text-slate-400">{a.description}</div> : null}
                  </div>
                  <div className="inline-flex items-center gap-2 text-sm text-slate-300">
                    <MessageCircle className="h-4 w-4" />
                    对话
                  </div>
                </Link>
              ))
          )}
        </div>
      </div>
    </Shell>
  )
}

