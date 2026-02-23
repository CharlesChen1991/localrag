import { ArrowLeft, Save, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Shell from '@/components/Shell'
import { apiGet, apiSend } from '@/utils/http'
import type { AgentDetail, AgentSummary, YamlFile } from '@/types/models'

function splitTags(s: string) {
  return s
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean)
}

export default function AgentDetail(props: { mode: 'create' | 'edit' }) {
  const nav = useNavigate()
  const params = useParams()
  const agentId = props.mode === 'edit' ? String(params.agentId || '') : ''

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [tags, setTags] = useState('')

  const [skills, setSkills] = useState<YamlFile[]>([])
  const [sysRules, setSysRules] = useState<YamlFile[]>([])
  const [trigRules, setTrigRules] = useState<YamlFile[]>([])

  const [skillFiles, setSkillFiles] = useState<string[]>([])
  const [systemRuleFiles, setSystemRuleFiles] = useState<string[]>([])
  const [triggerRuleFiles, setTriggerRuleFiles] = useState<string[]>([])

  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState('')

  const load = async () => {
    setErr('')
    const [s, r1, r2] = await Promise.all([
      apiGet<YamlFile[]>('/api/yaml/skills'),
      apiGet<YamlFile[]>('/api/yaml/rules-system'),
      apiGet<YamlFile[]>('/api/yaml/rules-triggers'),
    ])
    setSkills(s)
    setSysRules(r1)
    setTrigRules(r2)

    if (props.mode === 'edit') {
      const a = await apiGet<AgentDetail>(`/api/agents/${encodeURIComponent(agentId)}`)
      setName(a.name)
      setDescription(a.description || '')
      setTags((a.tags || []).join(', '))
      setSkillFiles(a.skillFiles || [])
      setSystemRuleFiles(a.systemRuleFiles || [])
      setTriggerRuleFiles(a.triggerRuleFiles || [])
    }
  }

  useEffect(() => {
    load().catch((e) => setErr(String(e.message || e)))
  }, [agentId, props.mode])

  const ruleCount = (systemRuleFiles.length || 0) + (triggerRuleFiles.length || 0)

  const preview = useMemo(() => {
    const obj = {
      name: name.trim(),
      description: description.trim(),
      tags: splitTags(tags),
      skillFiles,
      systemRuleFiles,
      triggerRuleFiles,
    }
    return JSON.stringify(obj, null, 2)
  }, [name, description, tags, skillFiles, systemRuleFiles, triggerRuleFiles])

  const toggle = (arr: string[], v: string, set: (x: string[]) => void) => {
    if (arr.includes(v)) set(arr.filter((x) => x !== v))
    else set([...arr, v])
  }

  const save = async () => {
    setErr('')
    const n = name.trim()
    if (!n) {
      setErr('名称必填')
      return
    }
    setSaving(true)
    try {
      if (props.mode === 'create') {
        const created = await apiSend<AgentSummary>('/api/agents', 'POST', {
          name: n,
          description: description.trim(),
          tags: splitTags(tags),
          skillFiles,
          systemRuleFiles,
          triggerRuleFiles,
        })
        nav(`/agents/${encodeURIComponent(created.agentId)}`)
        return
      }
      await apiSend<{ ok: boolean }>(`/api/agents/${encodeURIComponent(agentId)}`, 'PUT', {
        name: n,
        description: description.trim(),
        tags: splitTags(tags),
        skillFiles,
        systemRuleFiles,
        triggerRuleFiles,
      })
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setSaving(false)
    }
  }

  const del = async () => {
    if (props.mode !== 'edit') return
    setErr('')
    setSaving(true)
    try {
      await apiSend<{ ok: boolean }>(`/api/agents/${encodeURIComponent(agentId)}`, 'DELETE')
      nav('/')
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Shell
      title={props.mode === 'create' ? '创建 Agent' : 'Agent 配置'}
      subtitle="为单个 Agent 绑定 skills 与 rules，并保存校验"
      right={
        <div className="flex items-center gap-2">
          {props.mode === 'edit' ? (
            <button
              onClick={() => del()}
              disabled={saving}
              className="inline-flex items-center gap-2 rounded-lg border border-rose-900/60 bg-rose-950/30 px-3 py-2 text-sm text-rose-200 hover:bg-rose-950 disabled:opacity-60"
            >
              <Trash2 className="h-4 w-4" />
              删除
            </button>
          ) : null}
          <button
            onClick={() => save()}
            disabled={saving}
            className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-500 disabled:opacity-60"
          >
            <Save className="h-4 w-4" />
            保存
          </button>
        </div>
      }
    >
      <div className="mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <Link to="/agents" className="inline-flex items-center gap-2 text-sm text-slate-300 hover:text-slate-100">
          <ArrowLeft className="h-4 w-4" />
            返回 Agent Console
          </Link>
          <Link to="/skills" className="text-sm text-slate-300 hover:text-slate-100">
            管理 Skills / Rules
          </Link>
        </div>
      </div>

      {err ? <div className="mb-4 rounded-xl border border-rose-900/60 bg-rose-950/20 px-4 py-3 text-sm text-rose-200">{err}</div> : null}

      <div className="grid gap-4 lg:grid-cols-3">
        <section className="space-y-4 lg:col-span-2">
          <div className="rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="text-sm font-semibold">基本信息</div>
            <div className="mt-3 grid gap-3">
              <div>
                <div className="text-xs text-slate-400">名称（必填）</div>
                <input
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <div className="text-xs text-slate-400">简介</div>
                <input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-600"
                />
              </div>
              <div>
                <div className="text-xs text-slate-400">标签（逗号分隔）</div>
                <input
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-600"
                />
              </div>
            </div>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="flex items-center justify-between">
              <div className="text-sm font-semibold">Skills 绑定</div>
              <div className="text-xs text-slate-400">已选 {skillFiles.length}</div>
            </div>
            <div className="mt-3 grid gap-2 md:grid-cols-2">
              {skills.length === 0 ? <div className="text-sm text-slate-400">暂无 skills</div> : null}
              {skills
                .slice()
                .sort((a, b) => a.name.localeCompare(b.name))
                .map((s) => {
                  const on = skillFiles.includes(s.name)
                  return (
                    <button
                      key={s.name}
                      onClick={() => toggle(skillFiles, s.name, setSkillFiles)}
                      className={`flex items-center justify-between rounded-lg border px-3 py-2 text-left text-sm ${on ? 'border-blue-700 bg-blue-950/30 text-blue-100' : 'border-slate-800 bg-slate-950 text-slate-200 hover:bg-slate-900'}`}
                    >
                      <span className="font-semibold">{s.name}</span>
                      <span className="text-xs text-slate-400">{on ? '已选' : '未选'}</span>
                    </button>
                  )
                })}
            </div>
          </div>

          <div className="rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="flex items-center justify-between">
              <div className="text-sm font-semibold">Rules 绑定</div>
              <div className="text-xs text-slate-400">已选 {ruleCount}</div>
            </div>
            <div className="mt-3 grid gap-4 md:grid-cols-2">
              <div>
                <div className="text-xs font-semibold text-slate-300">System rules</div>
                <div className="mt-2 space-y-2">
                  {sysRules.length === 0 ? <div className="text-sm text-slate-400">暂无 system rules</div> : null}
                  {sysRules
                    .slice()
                    .sort((a, b) => a.name.localeCompare(b.name))
                    .map((r) => {
                      const on = systemRuleFiles.includes(r.name)
                      return (
                        <button
                          key={r.name}
                          onClick={() => toggle(systemRuleFiles, r.name, setSystemRuleFiles)}
                          className={`w-full rounded-lg border px-3 py-2 text-left text-sm ${on ? 'border-blue-700 bg-blue-950/30 text-blue-100' : 'border-slate-800 bg-slate-950 text-slate-200 hover:bg-slate-900'}`}
                        >
                          <div className="flex items-center justify-between">
                            <span className="font-semibold">{r.name}</span>
                            <span className="text-xs text-slate-400">{on ? '已选' : '未选'}</span>
                          </div>
                        </button>
                      )
                    })}
                </div>
              </div>

              <div>
                <div className="text-xs font-semibold text-slate-300">Trigger rules</div>
                <div className="mt-2 space-y-2">
                  {trigRules.length === 0 ? <div className="text-sm text-slate-400">暂无 trigger rules</div> : null}
                  {trigRules
                    .slice()
                    .sort((a, b) => a.name.localeCompare(b.name))
                    .map((r) => {
                      const on = triggerRuleFiles.includes(r.name)
                      return (
                        <button
                          key={r.name}
                          onClick={() => toggle(triggerRuleFiles, r.name, setTriggerRuleFiles)}
                          className={`w-full rounded-lg border px-3 py-2 text-left text-sm ${on ? 'border-blue-700 bg-blue-950/30 text-blue-100' : 'border-slate-800 bg-slate-950 text-slate-200 hover:bg-slate-900'}`}
                        >
                          <div className="flex items-center justify-between">
                            <span className="font-semibold">{r.name}</span>
                            <span className="text-xs text-slate-400">{on ? '已选' : '未选'}</span>
                          </div>
                        </button>
                      )
                    })}
                </div>
              </div>
            </div>
          </div>
        </section>

        <aside className="space-y-4">
          <div className="rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="text-sm font-semibold">配置预览</div>
            <pre className="mt-3 max-h-[520px] overflow-auto rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs text-slate-200">
              {preview}
            </pre>
          </div>
        </aside>
      </div>
    </Shell>
  )
}
