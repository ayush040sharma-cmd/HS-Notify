import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';

export default function Watchdog() {
  const [status, setStatus] = useState(null);
  const [log, setLog] = useState(null);
  const [error, setError] = useState(null);

  const load = () => {
    Promise.all([api.watchdogStatus(), api.watchdogHealthLog(0, 30)])
      .then(([s, l]) => { setStatus(s); setLog(l.content); })
      .catch(e => setError(e.response?.data?.message || 'Could not reach watchdog'));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 15000);
    return () => clearInterval(interval);
  }, []);

  if (error) return <div className="page"><div className="error-banner">{error}</div></div>;
  if (!status) return <div className="page"><div className="empty-state">Loading…</div></div>;

  return (
    <div className="page">
      <div className="card-grid">
        <Metric label="Consecutive failures" value={status.consecutiveFailures} color="var(--danger)" />
        <Metric label="Total restarts" value={status.totalRestarts} color="var(--warning)" />
        <Metric label="Escalations sent" value={status.escalationsSent} color="var(--info)" />
        <Metric label="Poll interval" value={`${status.pollIntervalSeconds}s`} color="var(--success)" />
        <Metric label="Fail threshold" value={status.failThreshold} color="var(--warning)" />
      </div>

      <div className="section-title">Watchdog health log</div>
      {(!log || log.length === 0) ? (
        <div className="empty-state">No health checks recorded yet — the watchdog polls every {status.pollIntervalSeconds}s and writes here automatically.</div>
      ) : (
        <table>
          <thead><tr><th>Time</th><th>Status</th><th>Response (ms)</th><th>Detail</th><th>Action taken</th></tr></thead>
          <tbody>
            {log.map(entry => (
              <tr key={entry.healthLogId}>
                <td>{new Date(entry.checkedAt).toLocaleTimeString()}</td>
                <td><span className={`badge badge-${entry.status.toLowerCase()}`}>{entry.status}</span></td>
                <td>{entry.responseTimeMs}</td>
                <td className="muted">{entry.detail}</td>
                <td className="muted">{entry.actionTaken}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function Metric({ label, value, color }) {
  return (
    <div className="metric-card">
      <div className="metric-label">{label}</div>
      <div className="metric-value" style={{ color }}>{value}</div>
    </div>
  );
}
