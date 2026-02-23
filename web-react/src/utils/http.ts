export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function parseJsonSafe(res: Response) {
  const ct = res.headers.get('content-type') || ''
  if (ct.includes('application/json')) {
    return res.json()
  }
  const text = await res.text()
  return { text }
}

export async function apiGet<T>(path: string): Promise<T> {
  const res = await fetch(path, { method: 'GET' })
  if (!res.ok) {
    const body = await parseJsonSafe(res)
    const msg = (body && (body.error || body.message || body.text)) ? String(body.error || body.message || body.text) : `HTTP ${res.status}`
    throw new ApiError(msg, res.status)
  }
  return (await res.json()) as T
}

export async function apiSend<T>(path: string, method: 'POST' | 'PUT' | 'DELETE', body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!res.ok) {
    const data = await parseJsonSafe(res)
    const msg = (data && (data.error || data.message || data.text)) ? String(data.error || data.message || data.text) : `HTTP ${res.status}`
    throw new ApiError(msg, res.status)
  }
  return (await res.json()) as T
}

