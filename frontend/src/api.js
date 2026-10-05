const BASE = ''

async function request(path, options = {}) {
  const res = await fetch(BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...options
  })
  if (!res.ok) {
    let msg = `请求失败 (${res.status})`
    try {
      const text = await res.text()
      if (text) msg = text
    } catch { /* ignore */ }
    throw new Error(msg)
  }
  return res.json()
}

export const api = {
  // 业主
  owners: () => request('/api/owners'),
  createOwner: (data) => request('/api/owners', { method: 'POST', body: JSON.stringify(data) }),

  // 袋号生命周期
  bags: (ownerId) => request('/api/bags' + (ownerId ? `?ownerId=${ownerId}` : '')),
  issue: (ownerId, count) => request('/api/bags/issue', { method: 'POST', body: JSON.stringify({ ownerId, count }) }),
  returnBag: (bagNo) => request(`/api/bags/${encodeURIComponent(bagNo)}/return`, { method: 'POST', body: '{}' }),
  markLost: (bagNo) => request(`/api/bags/${encodeURIComponent(bagNo)}/lost`, { method: 'POST', body: '{}' }),
  markDamaged: (bagNo, note) => request(`/api/bags/${encodeURIComponent(bagNo)}/damage`, { method: 'POST', body: JSON.stringify({ note }) }),

  // 扫码清运
  scan: (bagNo, operator) => request('/api/scan', { method: 'POST', body: JSON.stringify({ bagNo, operator }) }),

  // 对账与账单
  statement: (ownerId, month) => request(`/api/bills/statement?ownerId=${ownerId}&month=${month}`),
  bills: (params = {}) => request('/api/bills?' + new URLSearchParams(params)),
  generate: (month, ownerId) => request('/api/bills/generate', { method: 'POST', body: JSON.stringify({ month, ownerId }) }),
  settle: (billId) => request(`/api/bills/${billId}/settle`, { method: 'POST', body: '{}' }),
  adjust: (billId, reason, items) => request(`/api/bills/${billId}/adjustments`, { method: 'POST', body: JSON.stringify({ reason, items }) }),
  pay: (payload) => request('/api/payments', { method: 'POST', body: JSON.stringify(payload) })
}

export function currentMonth() {
  return new Date().toISOString().slice(0, 7)
}
