import React, { useEffect, useState, useCallback } from 'react';
import { api } from '../api/client.js';
import { Search, Filter, RefreshCw, X, ChevronRight } from 'lucide-react';

const STATUSES = ['', 'PENDING', 'SENDING', 'SENT', 'RETRYING', 'FAILED', 'ESCALATED', 'CANCELLED'];
const CHANNELS = ['', 'EMAIL', 'WEBHOOK', 'SLACK', 'SMS'];

function StatusBadge({ s }) { return <span className={`badge badge-${s.toLowerCase()}`}>{s}</span>; }
function ChannelBadge({ c }) { const ch = c || 'EMAIL'; return <span className={`badge badge-${ch.toLowerCase()}`}>{ch}</span>; }

export default function NotificationHistory() {
  const [jobs, setJobs] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [channelFilter, setChannelFilter] = useState('');
  const [search, setSearch] = useState('');
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    api.listJobs(statusFilter || undefined)
      .then(r => setJobs(r.content))
      .finally(() => setLoading(false));
  }, [statusFilter]);

  useEffect(() => { load(); }, [load]);

  const filtered = (jobs || []).filter(j => {
    if (channelFilter && (j.channel || 'EMAIL') !== channelFilter) return false;
    if (search) {
      const q = search.toLowerCase();
      if (!j.subject?.toLowerCase().includes(q) && !j.ruleCode?.toLowerCase().includes(q) && !(j.toAddresses || []).join().toLowerCase().includes(q)) return false;
    }
    return true;
  });

  return (
    <div>
      {/* Filter Bar */}
      <div className="filter-bar mb-16">
        <div style={{ position: 'relative', flex: 1, minWidth: 200 }}>
          <Search size={13} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input className="input" style={{ paddingLeft: 30 }} placeholder="Search subject, rule, recipient…" value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <select className="select" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
          {STATUSES.map(s => <option key={s} value={s}>{s || 'All statuses'}</option>)}
        </select>
        <select className="select" value={channelFilter} onChange={e => setChannelFilter(e.target.value)}>
          {CHANNELS.map(c => <option key={c} value={c}>{c || 'All channels'}</option>)}
        </select>
        <div className="filter-divider" />
        <button className="btn btn-ghost btn-sm" onClick={load} disabled={loading}>
          <RefreshCw size={13} className={loading ? 'pulse' : ''} /> Refresh
        </button>
        {(statusFilter || channelFilter || search) && (
          <button className="btn btn-ghost btn-sm" onClick={() => { setStatusFilter(''); setChannelFilter(''); setSearch(''); }}>
            <X size={13} /> Clear
          </button>
        )}
      </div>

      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8 }}>
          <span className="fs-12 text-secondary">{filtered.length} notifications</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th><th>Rule</th><th>Channel</th><th>To</th><th>Subject</th>
                <th>Status</th><th>Attempts</th><th>Created</th><th>Sent</th><th></th>
              </tr>
            </thead>
            <tbody>
              {jobs === null ? (
                <tr><td colSpan={10} style={{ padding: 24, textAlign: 'center', color: 'var(--text-muted)' }}>Loading…</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={10} className="empty-state">No notifications match this filter</td></tr>
              ) : filtered.map(j => (
                <tr key={j.jobId} onClick={() => setSelected(j)}>
                  <td className="td-mono">#{j.jobId}</td>
                  <td className="fs-12">{j.ruleCode || <span className="text-muted">direct</span>}</td>
                  <td><ChannelBadge c={j.channel} /></td>
                  <td className="fs-12 text-secondary">{(j.toAddresses || []).join(', ')}</td>
                  <td className="fs-12">{j.subject}</td>
                  <td><StatusBadge s={j.status} /></td>
                  <td className="td-muted">{j.attemptCount}/{j.maxRetryCount}</td>
                  <td className="td-muted">{new Date(j.createdAt).toLocaleString()}</td>
                  <td className="td-muted">{j.sentAt ? new Date(j.sentAt).toLocaleString() : '—'}</td>
                  <td><ChevronRight size={14} color="var(--text-muted)" /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Detail Drawer */}
      {selected && (
        <>
          <div className="drawer-overlay" onClick={() => setSelected(null)} />
          <div className="drawer">
            <div className="drawer-header">
              <div>
                <div className="drawer-title">Job #{selected.jobId}</div>
                <div className="fs-12 text-secondary mt-4">{selected.subject}</div>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setSelected(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              <div className="drawer-section">
                <div className="drawer-section-title">Status</div>
                <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                  <StatusBadge s={selected.status} />
                  <ChannelBadge c={selected.channel} />
                </div>
              </div>
              <div className="drawer-section">
                <div className="drawer-section-title">Routing</div>
                <div className="drawer-row"><span className="drawer-key">Rule</span><span className="drawer-val">{selected.ruleCode || '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">To</span><span className="drawer-val">{(selected.toAddresses || []).join(', ')}</span></div>
                <div className="drawer-row"><span className="drawer-key">CC</span><span className="drawer-val">{(selected.ccAddresses || []).join(', ') || '—'}</span></div>
              </div>
              <div className="drawer-section">
                <div className="drawer-section-title">Delivery</div>
                <div className="drawer-row"><span className="drawer-key">Attempts</span><span className="drawer-val">{selected.attemptCount}/{selected.maxRetryCount}</span></div>
                <div className="drawer-row"><span className="drawer-key">Attachment</span><span className="drawer-val">{selected.attachmentStatus || '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Created</span><span className="drawer-val">{new Date(selected.createdAt).toLocaleString()}</span></div>
                <div className="drawer-row"><span className="drawer-key">Sent</span><span className="drawer-val">{selected.sentAt ? new Date(selected.sentAt).toLocaleString() : '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Next retry</span><span className="drawer-val">{selected.nextRetryAt ? new Date(selected.nextRetryAt).toLocaleString() : '—'}</span></div>
              </div>
              {selected.lastError && (
                <div className="drawer-section">
                  <div className="drawer-section-title">Last Error</div>
                  <div style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--red)', background: 'var(--red-dim)', padding: '10px 12px', borderRadius: 8, lineHeight: 1.5 }}>
                    {selected.lastError}
                  </div>
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
