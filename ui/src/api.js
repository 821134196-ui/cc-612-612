const BASE = '';

async function request(method, path, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error(data?.error || ('请求失败 ' + res.status));
  }
  return data;
}

export const api = {
  listOwners: () => request('GET', '/api/owners'),
  issue: (payload) => request('POST', '/api/bags/issue', payload),
  returnBags: (bagCodes) => request('POST', '/api/bags/return', { bagCodes }),
  scan: (bagCode, collector) => request('POST', '/api/bags/scan', { bagCode, collector }),
  markLost: (payload) => request('POST', '/api/bags/lost', payload),
  note: (payload) => request('POST', '/api/bags/note', payload),
  listBags: (ownerCode) => request('GET', `/api/bags?ownerCode=${encodeURIComponent(ownerCode)}`),
  listBills: (ownerCode) => request('GET', `/api/bills?ownerCode=${encodeURIComponent(ownerCode)}`),
  monthlyBill: (ownerCode, month) =>
    request('GET', `/api/bills/monthly?ownerCode=${encodeURIComponent(ownerCode)}&month=${encodeURIComponent(month)}`),
  bill: (id) => request('GET', `/api/bills/${id}`),
  adjust: (payload) => request('POST', '/api/bills/adjust', payload),
  settle: (id, paidVia) => request('POST', `/api/bills/${id}/settle`, { paidVia }),
  recentScans: (limit = 10) => request('GET', `/api/scans/recent?limit=${limit}`)
};

export function fen(f) {
  return (f / 100).toFixed(2);
}

export function fmtTime(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

export function fmtTs(ms) {
  return ms ? fmtTime(new Date(ms).toISOString()) : '';
}
