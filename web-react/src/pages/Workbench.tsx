import { Link } from 'react-router-dom'
import { Plus, RefreshCw, Search } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import Shell from '@/components/Shell'
import StatCard from '@/components/StatCard'
import { apiGet } from '@/utils/http'
import type { AgentSummary, ServicesHealth, YamlFile } from '@/types/models'

function fmtTime(ms: number) {
  if (!ms) return ''
  const d = new Date(ms)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export default function Workbench() {
  const [agents, setAgents] = useState<AgentSummary[]>([])
  const [skills, setSkills] = useState<YamlFile[]>([])
  const [health, setHealth] = useState<ServicesHealth | null>(null)
  const [q, setQ] = useState('')
  const [err, setErr] = useState('')

  const load = async () => {
    setErr('')
    const [a, s, h] = await Promise.all([
      apiGet<AgentSummary[]>('/api/agents'),
      apiGet<YamlFile[]>('/api/yaml/skills'),
      apiGet<ServicesHealth>('/api/services/health'),
    ])
    setAgents(a)
    setSkills(s)
    setHealth(h)
  }

  useEffect(() => {
    load().catch((e) => setErr(String(e.message || e)))
  }, [])

  const filtered = useMemo(() => {
    const qq = q.trim().toLowerCase()
    if (!qq) return agents
    return agents.filter((x) => {
      const t = `${x.name} ${x.description || ''} ${(x.tags || []).join(' ')}`.toLowerCase()
      return t.includes(qq)
    })
  }, [agents, q])

  const milvusTone = health?.milvus.ok ? 'ok' : health?.milvus.enabled ? 'bad' : 'muted'
  const esTone = health?.elasticsearch.ok ? 'ok' : health?.elasticsearch.enabled ? 'bad' : 'muted'

  return (
    <Shell
      title="Agent 工作台"
      subtitle="Agent 列表、Skills 浏览与本地服务状态（全部真实数据）"
      right={
        <button
          onClick={() => load().catch((e) => setErr(String(e.message || e)))}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-sm text-slate-200 hover:bg-slate-800"
        >
          <RefreshCw className="h-4 w-4" />
          刷新
        </button>
      }
    >
      <div className="grid gap-4 lg:grid-cols-3">
        <section className="lg:col-span-2">
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
                          {(a.tags || []).slice(0, 6).map((t) => (
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
        </section>

        <aside className="space-y-4">
          <div className="grid gap-3">
            <StatCard
              label="Milvus"
              value={health ? (health.milvus.enabled ? (health.milvus.ok ? '已连接' : '不可用') : '未启用') : '加载中'}
              hint={health?.milvus.detail || (health?.milvus.collection ? `collection: ${health.milvus.collection}` : '')}
              tone={milvusTone as any}
            />
            <StatCard
              label="Elasticsearch"
              value={health ? (health.elasticsearch.enabled ? (health.elasticsearch.ok ? '已连接' : '不可用') : '未启用') : '加载中'}
              hint={
                health?.elasticsearch.detail ||
                (health?.elasticsearch.index ? `index: ${health.elasticsearch.index} (${health.elasticsearch.indexReady ? 'ready' : 'missing'})` : '')
              }
              tone={esTone as any}
            />
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900">
            <div className="border-b border-slate-800 px-4 py-3 text-sm font-semibold">Skills（可选）</div>
            <div className="max-h-[420px] overflow-auto p-2">
              {skills.length === 0 ? (
                <div className="px-2 py-8 text-center text-sm text-slate-400">暂无 skills 文件</div>
              ) : (
                skills
                  .slice()
                  .sort((a, b) => (b.mtime || 0) - (a.mtime || 0))
                  .map((s) => (
                    <div key={s.name} className="rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-xs text-slate-300">
                      <div className="font-semibold text-slate-100">{s.name}</div>
                      <div className="mt-1 text-slate-500">更新：{fmtTime(s.mtime || 0)}</div>
                    </div>
                  ))
              )}
            </div>
          </div>
        </aside>
      </div>
    </Shell>
  )
}

