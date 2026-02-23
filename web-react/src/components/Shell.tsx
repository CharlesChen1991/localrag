import { Link, NavLink } from 'react-router-dom'
import { Boxes, Database, FileCode2, Home, MessageCircle, Search, Users } from 'lucide-react'

export default function Shell(props: { title: string; subtitle?: string; children: React.ReactNode; right?: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <header className="sticky top-0 z-10 border-b border-slate-800 bg-slate-950/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <div className="flex items-center gap-3">
            <Link to="/agents" className="flex items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2">
              <span className="text-sm font-semibold tracking-tight">Agent Console</span>
            </Link>
            <nav className="hidden items-center gap-1 md:flex">
              <NavLink
                to="/"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-slate-900 text-slate-50' : 'text-slate-300 hover:bg-slate-900'}`
                }
              >
                <Home className="h-4 w-4" />
                工作台
              </NavLink>
              <NavLink
                to="/agents"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-slate-900 text-slate-50' : 'text-slate-300 hover:bg-slate-900'}`
                }
              >
                <Users className="h-4 w-4" />
                Agent Console
              </NavLink>
              <NavLink
                to="/skills"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-slate-900 text-slate-50' : 'text-slate-300 hover:bg-slate-900'}`
                }
              >
                <FileCode2 className="h-4 w-4" />
                Skills/Rules
              </NavLink>
              <NavLink
                to="/chat"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-slate-900 text-slate-50' : 'text-slate-300 hover:bg-slate-900'}`
                }
              >
                <MessageCircle className="h-4 w-4" />
                对话
              </NavLink>
              <NavLink
                to="/rag"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-slate-900 text-slate-50' : 'text-slate-300 hover:bg-slate-900'}`
                }
              >
                <Search className="h-4 w-4" />
                RAG
              </NavLink>
              <NavLink
                to="/local-dev"
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-lg px-3 py-2 text-sm ${isActive ? 'bg-slate-900 text-slate-50' : 'text-slate-300 hover:bg-slate-900'}`
                }
              >
                <Database className="h-4 w-4" />
                本地开发服务
              </NavLink>
            </nav>
          </div>
          <div className="flex items-center gap-2">
            {props.right}
            <a
              href="/api/config"
              target="_blank"
              rel="noreferrer"
              className="hidden items-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-3 py-2 text-sm text-slate-300 hover:bg-slate-800 md:flex"
            >
              <Boxes className="h-4 w-4" />
              API
            </a>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <div className="mb-4">
          <div className="text-sm font-semibold text-slate-50">{props.title}</div>
          {props.subtitle ? <div className="mt-1 text-xs text-slate-400">{props.subtitle}</div> : null}
        </div>
        {props.children}
      </main>
    </div>
  )
}
