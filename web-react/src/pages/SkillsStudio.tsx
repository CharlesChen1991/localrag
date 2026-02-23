import { useEffect, useMemo, useState } from 'react'
import { FilePlus2, RefreshCw, Save, Trash2 } from 'lucide-react'
import Shell from '@/components/Shell'
import { apiGet, apiSend } from '@/utils/http'
import type { YamlFile, YamlRead, YamlTemplates } from '@/types/models'

type Kind = 'skills' | 'rules-system' | 'rules-triggers' | 'mcp'

const kinds: { kind: Kind; label: string; hint: string }[] = [
  { kind: 'skills', label: 'Skills', hint: '自定义工具（YAML）' },
  { kind: 'rules-system', label: 'System Rules', hint: '系统级规则（YAML）' },
  { kind: 'rules-triggers', label: 'Trigger Rules', hint: '触发式规则（YAML）' },
  { kind: 'mcp', label: 'MCP', hint: '外部 MCP servers（YAML）' },
]

function fmtTime(ms?: number) {
  if (!ms) return ''
  const d = new Date(ms)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fmtSize(n?: number) {
  const v = Number(n || 0)
  if (v < 1024) return `${v}B`
  if (v < 1024 * 1024) return `${Math.round(v / 1024)}KB`
  return `${Math.round(v / 1024 / 1024)}MB`
}

export default function SkillsStudio() {
  const [kind, setKind] = useState<Kind>('skills')
  const [templates, setTemplates] = useState<YamlTemplates>({})
  const [files, setFiles] = useState<YamlFile[]>([])
  const [active, setActive] = useState<string>('')
  const [content, setContent] = useState('')
  const [newName, setNewName] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [err, setErr] = useState('')
  const [msg, setMsg] = useState('')

  const loadTemplates = async () => {
    try {
      const t = await apiGet<YamlTemplates>('/api/yaml/templates')
      setTemplates(t)
    } catch {
    }
  }

  const loadList = async (k: Kind) => {
    setErr('')
    setMsg('')
    setLoading(true)
    try {
      const res = await apiGet<YamlFile[]>(`/api/yaml/${encodeURIComponent(k)}`)
      setFiles(res)
      if (res.length && !active) {
        const first = res.slice().sort((a, b) => (b.mtime || 0) - (a.mtime || 0))[0].name
        await openFile(k, first)
      }
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setLoading(false)
    }
  }

  const openFile = async (k: Kind, name: string) => {
    setErr('')
    setMsg('')
    const r = await apiGet<YamlRead>(`/api/yaml/${encodeURIComponent(k)}/${encodeURIComponent(name)}`)
    setActive(r.name)
    setContent(r.content || '')
  }

  useEffect(() => {
    loadTemplates()
  }, [])

  useEffect(() => {
    setActive('')
    setContent('')
    loadList(kind)
  }, [kind])

  const sorted = useMemo(() => {
    return files.slice().sort((a, b) => (b.mtime || 0) - (a.mtime || 0))
  }, [files])

  const create = async () => {
    setErr('')
    setMsg('')
    const raw = newName.trim()
    if (!raw) {
      setErr('请输入文件名')
      return
    }
    const name = raw.endsWith('.yml') ? raw : `${raw}.yml`
    const tpl = templates[kind] || ''
    setSaving(true)
    try {
      await apiSend(`/api/yaml/${encodeURIComponent(kind)}/${encodeURIComponent(name)}`, 'PUT', { content: tpl })
      await apiSend('/api/config/reload', 'POST', {})
      setNewName('')
      await loadList(kind)
      await openFile(kind, name)
      setMsg('已创建并重载')
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setSaving(false)
    }
  }

  const save = async () => {
    setErr('')
    setMsg('')
    if (!active) {
      setErr('请先选择或新建文件')
      return
    }
    setSaving(true)
    try {
      await apiSend(`/api/yaml/${encodeURIComponent(kind)}/${encodeURIComponent(active)}`, 'PUT', { content })
      await apiSend('/api/config/reload', 'POST', {})
      await loadList(kind)
      setMsg('已保存并重载')
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setSaving(false)
    }
  }

  const del = async () => {
    setErr('')
    setMsg('')
    if (!active) {
      setErr('请先选择文件')
      return
    }
    setSaving(true)
    try {
      await apiSend(`/api/yaml/${encodeURIComponent(kind)}/${encodeURIComponent(active)}`, 'DELETE')
      await apiSend('/api/config/reload', 'POST', {})
      setActive('')
      setContent('')
      await loadList(kind)
      setMsg('已删除并重载')
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Shell
      title="Skills / Rules / MCP"
      subtitle="允许自定义 YAML（不使用 mock）"
      right={
        <button
          onClick={() => loadList(kind)}
          disabled={loading}
          className="inline-flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-sm text-slate-200 hover:bg-slate-800 disabled:opacity-60"
        >
          <RefreshCw className="h-4 w-4" />
          刷新
        </button>
      }
    >
      <div className="grid gap-4 lg:grid-cols-3">
        <section className="lg:col-span-1">
          <div className="rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="text-sm font-semibold">类型</div>
            <div className="mt-3 space-y-2">
              {kinds.map((k) => (
                <button
                  key={k.kind}
                  onClick={() => setKind(k.kind)}
                  className={`w-full rounded-lg border px-3 py-2 text-left text-sm ${kind === k.kind ? 'border-blue-700 bg-blue-950/30 text-blue-100' : 'border-slate-800 bg-slate-950 text-slate-200 hover:bg-slate-900'}`}
                >
                  <div className="flex items-center justify-between">
                    <span className="font-semibold">{k.label}</span>
                    <span className="text-xs text-slate-400">{kind === k.kind ? '当前' : ''}</span>
                  </div>
                  <div className="mt-1 text-xs text-slate-400">{k.hint}</div>
                </button>
              ))}
            </div>
          </div>

          <div className="mt-4 rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="text-sm font-semibold">新建文件</div>
            <div className="mt-3 flex gap-2">
              <input
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                placeholder="new-file.yml"
                className="w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-600"
              />
              <button
                onClick={() => create()}
                disabled={saving}
                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-500 disabled:opacity-60"
              >
                <FilePlus2 className="h-4 w-4" />
                新建
              </button>
            </div>
            <div className="mt-2 text-xs text-slate-400">新建会自动套用模板，并在保存后触发 YAML reload。</div>
          </div>
        </section>

        <section className="lg:col-span-2">
          <div className="rounded-xl border border-slate-800 bg-slate-900">
            <div className="flex items-center justify-between border-b border-slate-800 px-4 py-3">
              <div>
                <div className="text-sm font-semibold">文件列表</div>
                <div className="mt-1 text-xs text-slate-400">点击打开编辑</div>
              </div>
              <div className="text-xs text-slate-500">{loading ? '加载中…' : `${files.length} files`}</div>
            </div>
            <div className="max-h-[220px] overflow-auto p-2">
              {sorted.length === 0 ? (
                <div className="px-3 py-8 text-center text-sm text-slate-400">暂无文件</div>
              ) : (
                <div className="grid gap-2 md:grid-cols-2">
                  {sorted.map((f) => (
                    <button
                      key={f.name}
                      onClick={() => openFile(kind, f.name).catch((e) => setErr(String(e.message || e)))}
                      className={`rounded-lg border px-3 py-2 text-left text-sm ${active === f.name ? 'border-blue-700 bg-blue-950/30 text-blue-100' : 'border-slate-800 bg-slate-950 text-slate-200 hover:bg-slate-900'}`}
                    >
                      <div className="font-semibold">{f.name}</div>
                      <div className="mt-1 text-xs text-slate-500">{fmtTime(f.mtime)} · {fmtSize(f.size)}</div>
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="mt-4 rounded-xl border border-slate-800 bg-slate-900">
            <div className="flex flex-col gap-3 border-b border-slate-800 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
              <div className="text-sm font-semibold">编辑器</div>
              <div className="flex items-center gap-2">
                <div className="text-xs text-slate-400">{active ? `当前：${active}` : '未选择文件'}</div>
                <button
                  onClick={() => save()}
                  disabled={saving}
                  className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-500 disabled:opacity-60"
                >
                  <Save className="h-4 w-4" />
                  保存
                </button>
                <button
                  onClick={() => del()}
                  disabled={saving}
                  className="inline-flex items-center gap-2 rounded-lg border border-rose-900/60 bg-rose-950/30 px-3 py-2 text-sm text-rose-200 hover:bg-rose-950 disabled:opacity-60"
                >
                  <Trash2 className="h-4 w-4" />
                  删除
                </button>
              </div>
            </div>
            {err ? <div className="px-4 py-3 text-sm text-rose-300">{err}</div> : null}
            {msg ? <div className="px-4 pt-3 text-sm text-emerald-200">{msg}</div> : null}
            <div className="p-4">
              <textarea
                value={content}
                onChange={(e) => setContent(e.target.value)}
                spellCheck={false}
                className="h-[460px] w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-3 font-mono text-xs text-slate-100 outline-none focus:border-blue-600"
              />
            </div>
          </div>
        </section>
      </div>
    </Shell>
  )
}

