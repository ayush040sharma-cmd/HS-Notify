import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { AlertTriangle, RefreshCw, RotateCcw, X, XCircle } from 'lucide-react';

function StatusBadge({ s }) { return <span className={`badge badge-${s.toLowerCase()}`}>{s}</span>; }
function ChannelBadge({ c }) { const ch = c || 'EMAIL'; return <span className={`badge badge-${ch.toLowerCase()}`}>{ch}</span>; }

export default function FailedNotifications() {
  const [jobs, setJobs] = useState(null);
  const [tab, setTab] = useState('FAILED');
  const [busyId, setBusyId] = useState(null);

  const load = () => {
    Promise.all([
      api.listJobs('FAILED'),
      api.listJobs('ESCALATED'),
    ]).then(([f, e]) => {
      setJobs({ FAILED: f.content, ESCALATED: e.content });
    });
  };

  useEffect(() => { load(); }, []);

  const requeue = async (jobId) => {
    setBusyId(jobId);
    try { await api.requeueJob(jobId); load(); } finally { setBusyId(null); }
  };
  const cancel = async (jobId) => {
    setBusyId(jobId);
    try { await api.cancelJob(jobId); load(); } finally { setBusyId(null); }
  };

  const current = jobs?.[tab] || [];

  return (
    <div>
      <div className="tabs">
        <div className={`tab ${tab === 'FAILED' ? 'active' : ''}`} onClick={() => setTab('FAILED')}>
          Failed {jobs && <span className="badge badge-failed" style={{ marginLeft: 6, padding: '1px 6px' }}>{jobs.FAILED.length}</span>}
        </div>
        <div className={`tab ${tab === 'ESCALATED' ? 'active' : ''}`} onClick={() => setTab('ESCALATED')}>
          Escalated {jobs && <span className="badge badge-escalated" style={{ marginLeft: 6, padding: '1px 6px' }}>{jobs.ESCALATED.length}</span>}
        </div>
        <div style={{ marginLeft: 'auto', alignSelf: 'center' }}>
          <button className="btn btn-ghost btn-sm" onClick={load}><RefreshCw size={13} /> Refresh</button>
        </div>
      </div>

      {current.length > 0 && (
        <div className="error-banner mb-16">
          <AlertTriangle size={14} />
          {current.length} {tab.toLowerCase()} notification{current.length !== 1 ? 's' : ''} need attention
        </div>
      )}

      <div className="card" style={{ padding: 0 }}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th><th>Rule</th><th>Channel</th><th>To</th><th>Subject</th>
                <th>Attempts</th><th>Last Error</th><th>Created</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {jobs === null ? (
                <tr><td colSpan={9} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
              ) : current.length === 0 ? (
                <tr>
                  <td colSpan={9}>
                    <div className="empty-state">
                      <div className="empty-state-icon"><XCircle size={32} /></div>
                      No {tab.toLowerCase()} notifications
                    </div>
                  </td>
                </tr>
              ) : current.map(j => (
                <tr key={j.jobId}>
                  <td className="td-mono">#{j.jobId}</td>
                  <td className="fs-12">{j.ruleCode || <span className="text-muted">direct</span>}</td>
                  <td><ChannelBadge c={j.channel} /></td>
                  <td className="fs-12 text-secondary">{(j.toAddresses || []).join(', ')}</td>
                  <td className="fs-12">{j.subject}</td>
                  <td className="td-muted">{j.attemptCount}/{j.maxRetryCount}</td>
                  <td className="fs-12 text-red" style={{ maxWidth: 240 }}>{j.lastError || '—'}</td>
                  <td className="td-muted">{new Date(j.createdAt).toLocaleString()}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <button className="btn btn-green btn-sm" disabled={busyId === j.jobId} onClick={() => requeue(j.jobId)}>
                        <RotateCcw size={12} /> Requeue
                      </button>
                      <button className="btn btn-ghost btn-sm" disabled={busyId === j.jobId} onClick={() => cancel(j.jobId)}>
                        <X size={12} /> Cancel
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
