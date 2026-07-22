import React, { useEffect, useState, useRef } from 'react';
import { api } from '../api/client.js';
import { Clock, Zap, RefreshCw, RotateCcw, X } from 'lucide-react';

function StatCard({ label, value, color }) {
  return (
    <div className="card" style={{ textAlign: 'center', padding: '16px 12px' }}>
      <div style={{ fontSize: 28, fontWeight: 700, color: color || 'var(--text)' }}>{value}</div>
      <div className="fs-11 text-muted mt-4">{label}</div>
    </div>
  );
}

export default function QueueMonitor() {
  const [stats, setStats] = useState(null);
  const [jobs, setJobs] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [countdown, setCountdown] = useState(5);
  const intervalRef = useRef(null);

  const load = () => {
    Promise.all([
      api.queueStats ? api.queueStats() : Promise.resolve({}),
      api.listJobs(undefined),
    ]).then(([s, j]) => {
      setStats(s);
      setJobs(j.content.filter(j => ['PENDING', 'SENDING', 'RETRYING'].includes(j.status)));
    });
    setCountdown(5);
  };

  useEffect(() => {
    load();
    intervalRef.current = setInterval(() => {
      setCountdown(c => {
        if (c <= 1) { load(); return 5; }
        return c - 1;
      });
    }, 1000);
    return () => clearInterval(intervalRef.current);
  }, []);

  const requeue = async (jobId) => {
    setBusyId(jobId);
    try { await api.requeueJob(jobId); load(); } finally { setBusyId(null); }
  };
  const cancel = async (jobId) => {
    setBusyId(jobId);
    try { await api.cancelJob(jobId); load(); } finally { setBusyId(null); }
  };

  return (
    <div>
      {/* Stats */}
      <div className="grid grid-5 mb-24" style={{ gap: 12 }}>
        <StatCard label="Pending" value={stats?.pendingCount ?? '—'} color="var(--blue)" />
        <StatCard label="Sending" value={stats?.sendingCount ?? '—'} color="var(--purple)" />
        <StatCard label="Retrying" value={stats?.retryingCount ?? '—'} color="var(--amber)" />
        <StatCard label="Throughput/hr" value={stats?.throughputPerHour ?? '—'} color="var(--green)" />
        <StatCard label="Avg Process Time" value={stats?.avgProcessingTimeMs ? `${stats.avgProcessingTimeMs}ms` : '—'} />
      </div>

      {/* Auto-refresh header */}
      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div className="section-title"><Clock size={14} /> Active Queue</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className="fs-11 text-muted">Auto-refresh in <strong>{countdown}s</strong></span>
            <button className="btn btn-ghost btn-sm" onClick={load}><RefreshCw size={13} /> Refresh now</button>
          </div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>ID</th><th>Rule</th><th>Channel</th><th>To</th><th>Status</th><th>Attempt</th><th>Next Retry</th><th>Created</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {jobs === null ? (
                <tr><td colSpan={9} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
              ) : jobs.length === 0 ? (
                <tr>
                  <td colSpan={9}>
                    <div className="empty-state">
                      <div className="empty-state-icon"><Zap size={28} /></div>
                      Queue is empty — all clear!
                    </div>
                  </td>
                </tr>
              ) : jobs.map(j => (
                <tr key={j.jobId}>
                  <td className="td-mono">#{j.jobId}</td>
                  <td className="fs-12">{j.ruleCode || 'direct'}</td>
                  <td><span className={`badge badge-${(j.channel||'EMAIL').toLowerCase()}`}>{j.channel||'EMAIL'}</span></td>
                  <td className="fs-12 text-secondary">{(j.toAddresses||[]).join(', ')}</td>
                  <td><span className={`badge badge-${j.status.toLowerCase()}`}>{j.status}</span></td>
                  <td className="td-muted">{j.attemptCount}/{j.maxRetryCount}</td>
                  <td className="td-muted">{j.nextRetryAt ? new Date(j.nextRetryAt).toLocaleTimeString() : '—'}</td>
                  <td className="td-muted">{new Date(j.createdAt).toLocaleString()}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <button className="btn btn-green btn-sm" disabled={busyId === j.jobId} onClick={() => requeue(j.jobId)}>
                        <RotateCcw size={11} />
                      </button>
                      <button className="btn btn-ghost btn-sm" disabled={busyId === j.jobId} onClick={() => cancel(j.jobId)}>
                        <X size={11} />
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
