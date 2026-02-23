import { useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'
import Shell from '@/components/Shell'
import StatCard from '@/components/StatCard'
import { apiGet } from '@/utils/http'
import type { ServicesHealth } from '@/types/models'

const compose = `services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.12.2
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms1g -Xmx1g
    ports:
      - "9200:9200"
    volumes:
      - esdata:/usr/share/elasticsearch/data

  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
      - ETCD_ENABLE_V2=true
      - ALLOW_NONE_AUTHENTICATION=yes
      - ETCD_LISTEN_CLIENT_URLS=http://0.0.0.0:2379
      - ETCD_ADVERTISE_CLIENT_URLS=http://etcd:2379
    ports:
      - "2379:2379"
    volumes:
      - etcddata:/etcd

  minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    environment:
      - MINIO_ROOT_USER=minioadmin
      - MINIO_ROOT_PASSWORD=minioadmin
    command: ["server", "/minio_data"]
    ports:
      - "9000:9000"
    volumes:
      - miniodata:/minio_data

  milvus:
    image: milvusdb/milvus:v2.4.4
    command: ["milvus", "run", "standalone"]
    environment:
      - ETCD_ENDPOINTS=etcd:2379
      - MINIO_ADDRESS=minio:9000
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on:
      - etcd
      - minio
    volumes:
      - milvusdata:/var/lib/milvus

volumes:
  esdata:
  etcddata:
  miniodata:
  milvusdata:
`

export default function LocalDev() {
  const [health, setHealth] = useState<ServicesHealth | null>(null)
  const [err, setErr] = useState('')

  const load = async () => {
    setErr('')
    const h = await apiGet<ServicesHealth>('/api/services/health')
    setHealth(h)
  }

  useEffect(() => {
    load().catch((e) => setErr(String(e.message || e)))
  }, [])

  const milvusTone = health?.milvus.ok ? 'ok' : health?.milvus.enabled ? 'bad' : 'muted'
  const esTone = health?.elasticsearch.ok ? 'ok' : health?.elasticsearch.enabled ? 'bad' : 'muted'

  return (
    <Shell
      title="本地开发与服务"
      subtitle="docker-compose 一键启动 Milvus + Elasticsearch，并在页面展示探活与就绪"
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
          <div className="rounded-xl border border-slate-800 bg-slate-900 p-4">
            <div className="text-sm font-semibold">docker-compose.yml</div>
            <div className="mt-2 text-xs text-slate-400">在 `ai-assistant-prototype/` 下保存并执行 `docker compose up -d`。</div>
            <pre className="mt-3 max-h-[520px] overflow-auto rounded-lg border border-slate-800 bg-slate-950 p-3 text-xs text-slate-200">{compose}</pre>
          </div>
        </section>

        <aside className="space-y-4">
          {err ? <div className="rounded-xl border border-rose-900/60 bg-rose-950/20 px-4 py-3 text-sm text-rose-200">{err}</div> : null}
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
        </aside>
      </div>
    </Shell>
  )
}
