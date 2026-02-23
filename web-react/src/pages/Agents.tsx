import { Link } from 'react-router-dom'
import { Plus, RefreshCw, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import Shell from '@/components/Shell'
import { apiGet } from '@/utils/http'
import type { AgentSummary } from '@/types/models'

function fmtTime(ms: number) {
  if (!ms) return ''
  const d = new Date(ms)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function Agents() {
  const [items, setItems] = useState<AgentSummary[]>([])
  const [q, setQ] = useState('')
  const [err, setErr] = useState('')
  const [loading, setLoading] = useState(false)

  const load = async () => {
    setErr('')
    setLoading(true)
    try {
      const res = await apiGet<AgentSummary[]>('/api/agents')
      setItems(res)
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
  }, [])

  const filtered = useMemo(() => {
    const qq = q.trim().toLowerCase()
    if (!qq) return items
    return items.filter((x) => {
      const t = `${x.name} ${x.description || ''} ${(x.tags || []).join(' ')}`.toLowerCase()
      return t.includes(qq)
    })
  }, [items, q])

  return (
    <Shell
      title="Agent Console"
      subtitle="管理所有 Agent（真实数据）"
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
      <div className="rounded-xl border border-slate-800 bg-slate-900">
        <div className="flex flex-col gap-3 border-b border-slate-800 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="text-sm font-semibold">Agents</div>
          <div className="flex items-center gap-2">
            <div className="relative">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-slate-400" />
              <input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder="搜索名称/标签"
                className="w-64 rounded-lg border border-slate-800 bg-slate-950 py-2 pl-9 pr-3 text-sm text-slate-200 outline-none focus:border-blue-600"
              />
            </div>
            <Link
              to="/agents/new"
              className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-500"
            >
              <Plus className="h-4 w-4" />
              创建 Agent
            </Link>
          </div>
        </div>

        {err ? <div className="px-4 py-3 text-sm text-rose-300">{err}</div> : null}

        <div className="divide-y divide-slate-800">
          {filtered.length === 0 ? (
            <div className="px-4 py-10 text-center text-sm text-slate-400">暂无 Agent</div>
          ) : (
            filtered.map((a) => (
              <Link
                key={a.agentId}
                to={`/agents/${encodeURIComponent(a.agentId)}`}
                className="block px-4 py-3 hover:bg-slate-950"
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="text-sm font-semibold text-slate-100">{a.name}</div>
                    {a.description ? <div className="mt-1 text-xs text-slate-400">{a.description}</div> : null}
                    <div className="mt-2 flex flex-wrap gap-2">
                      {(a.tags || []).slice(0, 8).map((t) => (
                        <span key={t} className="rounded-full border border-slate-800 bg-slate-950 px-2 py-0.5 text-xs text-slate-300">
                          {t}
                        </span>
                      ))}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-xs text-slate-300">skills: {a.skillCount} · rules: {a.ruleCount}</div>
                    <div className="mt-1 text-xs text-slate-500">更新：{fmtTime(a.updatedAt)}</div>
                  </div>
                </div>
              </Link>
            ))
          )}
        </div>
      </div>
    </Shell>
  )
}

