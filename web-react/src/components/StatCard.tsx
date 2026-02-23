export default function StatCard(props: { label: string; value: string; hint?: string; tone?: 'ok' | 'bad' | 'muted' }) {
  const tone = props.tone || 'muted'
  const cls =
    tone === 'ok'
      ? 'border-emerald-900/60 bg-emerald-950/20'
      : tone === 'bad'
        ? 'border-rose-900/60 bg-rose-950/20'
        : 'border-slate-800 bg-slate-900'
  return (
    <div className={`rounded-xl border px-4 py-3 ${cls}`}>
      <div className="text-xs text-slate-400">{props.label}</div>
      <div className="mt-1 text-sm font-semibold text-slate-100">{props.value}</div>
      {props.hint ? <div className="mt-1 text-xs text-slate-400">{props.hint}</div> : null}
    </div>
  )
}

