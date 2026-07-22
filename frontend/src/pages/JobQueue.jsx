import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';

const STATUSES = ['', 'PENDING', 'SENDING', 'SENT', 'RETRYING', 'FAILED', 'ESCALATED', 'CANCELLED'];

export default function JobQueue() {
  const [jobs, setJobs] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = () => {
    api.listJobs(statusFilter || undefined, 0, 50)
      .then(r => setJobs(r.content))
      .catch(e => setError(e.response?.data?.message || 'Could not load job queue'));
  };

  useEffect(load, [statusFilter]);

  const requeue = async (jobId) => {
    setBusyId(jobId);
    try { await api.requeueJob(jobId); load(); } finally { setBusyId(null); }
  };
  const cancel = async (jobId) => {
    setBusyId(jobId);
    try { await api.cancelJob(jobId); load(); } finally { setBusyId(null); }
  };

  return (
    <div className="page">
      <div className="form-group" style={{ maxWidth: 240 }}>
        <label>Filter by status</label>
        <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
          {STATUSES.map(s => <option key={s} value={s}>{s || 'All'}</option>)}
        </select>
      </div>

      {error && <div className="error-banner">{error}</div>}

      {jobs === null ? (
        <div className="empty-state">Loading…</div>
      ) : jobs.length === 0 ? (
        <div className="empty-state">No jobs match this filter.</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>ID</th><th>Rule</th><th>Channel</th><th>To</th><th>Status</th><th>Attempts</th>
              <th>Next retry</th><th>Last error</th><th>Created</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {jobs.map(j => (
              <tr key={j.jobId}>
                <td>{j.jobId}</td>
                <td>{j.ruleCode || '—'}</td>
                <td><span className={`badge badge-channel-${(j.channel || 'EMAIL').toLowerCase()}`}>{j.channel || 'EMAIL'}</span></td>
                <td>{(j.toAddresses || []).join(', ')}</td>
                <td><span className={`badge badge-${j.status.toLowerCase()}`}>{j.status}</span></td>
                <td>{j.attemptCount}/{j.maxRetryCount}</td>
                <td>{j.nextRetryAt ? new Date(j.nextRetryAt).toLocaleTimeString() : '—'}</td>
                <td className="muted" style={{ maxWidth: 220 }}>{j.lastError || '—'}</td>
                <td>{new Date(j.createdAt).toLocaleString()}</td>
                <td>
                  {(j.status === 'FAILED' || j.status === 'ESCALATED') && (
                    <button className="secondary" disabled={busyId === j.jobId} onClick={() => requeue(j.jobId)}>Requeue</button>
                  )}
                  {j.status !== 'SENT' && j.status !== 'CANCELLED' && (
                    <button className="secondary" disabled={busyId === j.jobId} onClick={() => cancel(j.jobId)}>Cancel</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
