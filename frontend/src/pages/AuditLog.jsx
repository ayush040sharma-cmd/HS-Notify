import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { ListChecks, RefreshCw } from 'lucide-react';

function eventBadgeClass(eventType) {
  if (eventType?.includes('FAIL') || eventType?.includes('FAILED')) return 'badge-failed';
  if (eventType?.includes('SUCCESS') || eventType?.includes('SENT')) return 'badge-sent';
  if (eventType?.includes('ESCALAT')) return 'badge-escalated';
  if (eventType?.includes('RETRY')) return 'badge-retrying';
  return 'badge-pending';
}

export default function AuditLog() {
  const [entries, setEntries] = useState(null);
  const [error, setError] = useState(null);

  const load = () => {
    api.listAudit(0, 100)
      .then(r => setEntries(r.content))
      .catch(e => setError(e?.response?.data?.message || 'Could not load audit log'));
  };
  useEffect(() => { load(); }, []);

  if (error) return <div className="error-banner">{error}</div>;

  return (
    <div>
      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div className="section-title"><ListChecks size={14} /> Audit Events</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span className="fs-11 text-muted">Append-only. No edits or deletes.</span>
            <button className="btn btn-ghost btn-sm" onClick={load}><RefreshCw size={13} /></button>
          </div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Time</th><th>Event</th><th>Job</th><th>Rule</th><th>Detail</th><th>Actor</th><th>Response</th></tr>
            </thead>
            <tbody>
              {entries === null ? (
                <tr><td colSpan={7} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
              ) : entries.length === 0 ? (
                <tr><td colSpan={7} className="empty-state">No audit events recorded yet</td></tr>
              ) : entries.map(e => (
                <tr key={e.auditId}>
                  <td className="td-mono">{new Date(e.occurredAt).toLocaleString()}</td>
                  <td><span className={`badge ${eventBadgeClass(e.eventType)}`}>{e.eventType}</span></td>
                  <td className="td-mono">#{e.job?.jobId ?? '—'}</td>
                  <td className="fs-12 text-secondary">{e.rule?.ruleCode ?? '—'}</td>
                  <td className="fs-12 text-secondary" style={{ maxWidth: 320 }}>{e.eventDetail}</td>
                  <td className="td-muted">{e.actor}</td>
                  <td className="td-muted">{e.responseTimeMs != null ? `${e.responseTimeMs}ms` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
