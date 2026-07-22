import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Activity, RefreshCw, CheckCircle, XCircle, AlertTriangle } from 'lucide-react';

function StatusChip({ status }) {
  const up = status === 'UP';
  const stub = status === 'STUB';
  return (
    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
      <span className={`dot dot-${up ? 'green' : stub ? 'amber' : 'red'}${up ? ' pulse' : ''}`} />
      <span className={`fw-600 fs-12 text-${up ? 'green' : stub ? 'amber' : 'red'}`}>{status}</span>
    </span>
  );
}

export default function WatchdogPage() {
  const [wdog, setWdog] = useState(null);
  const [log, setLog] = useState(null);
  const [health, setHealth] = useState(null);

  const load = () => {
    Promise.all([
      api.watchdogStatus(),
      api.watchdogHealthLog(),
      api.serviceHealth ? api.serviceHealth() : Promise.resolve([]),
    ]).then(([w, l, h]) => {
      setWdog(w);
      setLog(l.content);
      setHealth(h);
    });
  };
  useEffect(() => { load(); }, []);

  const fmt = iso => iso ? new Date(iso).toLocaleString() : '—';

  return (
    <div>
      {/* Status Banner */}
      {wdog && (
        <div style={{
          padding: '14px 20px', borderRadius: 12, marginBottom: 20,
          background: wdog.currentStatus === 'UP' ? 'var(--green-dim)' : 'var(--red-dim)',
          border: `1px solid ${wdog.currentStatus === 'UP' ? 'rgba(34,197,94,0.2)' : 'rgba(239,68,68,0.2)'}`,
          display: 'flex', alignItems: 'center', justifyContent: 'space-between'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span className={`dot dot-${wdog.currentStatus === 'UP' ? 'green' : 'red'} pulse`} />
            <div>
              <div className="fw-700" style={{ color: wdog.currentStatus === 'UP' ? 'var(--green)' : 'var(--red)' }}>
                Service {wdog.currentStatus}
              </div>
              <div className="fs-12 text-secondary mt-4">
                Last up: {fmt(wdog.lastUpAt)} · Restarts: {wdog.totalRestarts} · Escalations sent: {wdog.escalationsSent}
              </div>
            </div>
          </div>
          <button className="btn btn-ghost btn-sm" onClick={load}><RefreshCw size={13} /></button>
        </div>
      )}

      <div className="grid grid-2 mb-16" style={{ gap: 16 }}>
        {/* Watchdog Config */}
        <div className="card">
          <div className="card-title mb-12"><Activity size={14} /> Watchdog Config</div>
          {wdog && [
            ['Poll interval', `${wdog.pollIntervalSeconds}s`],
            ['Fail threshold', `${wdog.failThreshold} consecutive`],
            ['Consecutive failures', wdog.consecutiveFailures],
            ['Total restarts', wdog.totalRestarts],
            ['Escalations sent', wdog.escalationsSent],
            ['Last down', fmt(wdog.lastDownAt)],
          ].map(([k, v]) => (
            <div key={k} className="config-row">
              <span className="config-key">{k}</span>
              <span className="config-val">{String(v)}</span>
            </div>
          ))}
        </div>

        {/* Service Health */}
        <div className="card">
          <div className="card-title mb-12"><CheckCircle size={14} /> Dependency Health</div>
          {(health || []).map((h, i) => (
            <div key={i} className="health-item">
              <div className="health-name">
                <span className={`dot dot-${h.status === 'UP' ? 'green' : h.status === 'STUB' ? 'amber' : 'red'}`} />
                <span className="fs-13">{h.name}</span>
              </div>
              <div className="health-meta">
                <StatusChip status={h.status} />
                {h.responseTimeMs != null && <div className="health-rt">{h.responseTimeMs}ms</div>}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Health Log */}
      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)' }}>
          <div className="section-title"><Activity size={14} /> Health Check Log</div>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Time</th><th>Status</th><th>Response Time</th><th>Detail</th><th>Action Taken</th></tr>
            </thead>
            <tbody>
              {log === null ? (
                <tr><td colSpan={5} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
              ) : log.map(l => (
                <tr key={l.healthLogId}>
                  <td className="td-mono">{new Date(l.checkedAt).toLocaleTimeString()}</td>
                  <td><StatusChip status={l.status} /></td>
                  <td className="td-muted">{l.responseTimeMs != null ? `${l.responseTimeMs}ms` : '—'}</td>
                  <td className="fs-12 text-secondary">{l.detail}</td>
                  <td>
                    {l.actionTaken !== 'NONE' ? (
                      <span className="badge badge-warn">{l.actionTaken}</span>
                    ) : (
                      <span className="text-muted fs-12">—</span>
                    )}
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
