import { useState } from 'react'
import { Search } from 'lucide-react'
import Shell from '@/components/Shell'
import { apiSend } from '@/utils/http'
import type { RagSearchResponse } from '@/types/models'

export default function RagDebug() {
  const [query, setQuery] = useState('')
  const [topK, setTopK] = useState(8)
  const [res, setRes] = useState<RagSearchResponse | null>(null)
  const [err, setErr] = useState('')
  const [loading, setLoading] = useState(false)

  const run = async () => {
    const q = query.trim()
    if (!q) return
    setErr('')
    setLoading(true)
    try {
      const r = await apiSend<RagSearchResponse>('/api/rag/search', 'POST', { query: q, topK })
      setRes(r)
    } catch (e: any) {
      setErr(String(e.message || e))
    } finally {
      setLoading(false)
    }
  }

  return (
    <Shell title="RAG 调试" subtitle="查看一次检索召回结果（真实数据）">
      <div className="rounded-xl border border-slate-800 bg-slate-900 p-4">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
          <div className="flex-1">
            <div className="text-xs text-slate-400">Query</div>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') run()
              }}
              placeholder="输入检索 query（例如：缓存、接口、index）"
              className="mt-1 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-600"
            />
          </div>
          <div className="w-40">
            <div className="text-xs text-slate-400">TopK</div>
            <input
              value={topK}
              onChange={(e) => setTopK(Number(e.target.value || 0))}
              type="number"
              min={1}
              max={50}
              className="mt-1 w-full rounded-lg border border-slate-800 bg-slate-950 px-3 py-2 text-sm text-slate-100 outline-none focus:border-blue-600"
            />
          </div>
          <button
            onClick={() => run()}
            disabled={loading}
            className="mt-5 inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-semibold text-white hover:bg-blue-500 disabled:opacity-60 sm:mt-6"
          >
            <Search className="h-4 w-4" />
            检索
          </button>
        </div>

        {err ? <div className="mt-3 text-sm text-rose-300">{err}</div> : null}

        <div className="mt-3 text-xs text-slate-400">
          milvusEnabled={String(res?.milvusEnabled ?? '')} · esEnabled={String(res?.esEnabled ?? '')}
        </div>
      </div>

      <div className="mt-4 rounded-xl border border-slate-800 bg-slate-900">
        <div className="border-b border-slate-800 px-4 py-3 text-sm font-semibold">结果</div>
        <div className="p-4">
          {!res ? (
            <div className="py-10 text-center text-sm text-slate-400">输入 query 开始检索</div>
          ) : res.items.length === 0 ? (
            <div className="py-10 text-center text-sm text-slate-400">无结果</div>
          ) : (
            <div className="space-y-3">
              {res.items.map((it) => (
                <div key={it.chunkId} className="rounded-xl border border-slate-800 bg-slate-950 px-4 py-3">
                  <div className="text-sm font-semibold text-slate-100">{it.path}</div>
                  <div className="mt-1 text-xs text-slate-500">chunkId: {it.chunkId}</div>
                  <div className="mt-3 whitespace-pre-wrap text-xs text-slate-200">{it.preview}</div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </Shell>
  )
}

