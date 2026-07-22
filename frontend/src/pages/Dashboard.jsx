import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Link } from 'react-router-dom';
import {
  Send, AlertTriangle, Clock, CheckCircle, Activity,
  RefreshCw, Database, MailCheck, Cpu
} from 'lucide-react';

const fmt = (iso) => iso ? new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '—';
const fmtFull = (iso) => iso ? new Date(iso).toLocaleString() : '—';

function StatusBadge({ status }) {
  return <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>;
}

function ChannelBadge({ channel }) {
  const ch = channel || 'EMAIL';
  return <span className={`badge badge-${ch.toLowerCase()}`}>{ch}</span>;
}

function KpiCard({ label, value, icon: Icon, color, sub }) {
  return (
    <div className="kpi-card">
      <div className="kpi-top">
        <div>
          <div className="kpi-label">{label}</div>
          <div className="kpi-value" style={{ color }}>{value}</div>
        </div>
        <div className="kpi-icon" style={{ background: `${color}18` }}>
          <Icon size={18} color={color} />
        </div>
      </div>
      {sub && <div className="kpi-trend">{sub}</div>}
    </div>
  );
}

export default function Dashboard() {
  const [summary, setSummary] = useState(null);
  const [jobs, setJobs] = useState([]);
  const [health, setHealth] = useState([]);
  const [error, setError] = useState(null);

  const load = () => {
    Promise.all([
      api.dashboardSummary(),
      api.listJobs(undefined, 0, 7),
      api.serviceHealth ? api.serviceHealth() : Promise.resolve([]),
    ]).then(([s, j, h]) => {
      setSummary(s);
      setJobs(j.content);
      setHealth(h);
    }).catch(e => setError(e?.response?.data?.message || 'Could not reach backend'));
  };

  useEffect(() => { load(); }, []);

  if (error) {
    return (
      <div>
        <div className="error-banner">
          <AlertTriangle size={15} />
          {error}. Check that the backend is running on port 8089. Showing mock data.
        </div>
      </div>
    );
  }

  if (!summary) {
    return (
      <div className="grid grid-4" style={{ gap: 16 }}>
        {[1,2,3,4].map(i => <div key={i} className="kpi-card"><div className="skeleton" style={{ height: 60 }} /></div>)}
      </div>
    );
  }

  const healthOk = summary.serviceStatus === 'UP';

  return (
    <div>
      {/* KPI Strip */}
      <div className="grid grid-4 mb-24" style={{ gap: 16 }}>
        <KpiCard label="Sent (24h)" value={summary.emailsSent24h} icon={Send} color="var(--green)"
          sub={<span>{summary.totalSentAllTime} all-time</span>} />
        <KpiCard label="Failed Jobs (24h)" value={summary.failedJobs24h} icon={AlertTriangle} color="var(--red)"
          sub={<span>{summary.totalFailedAllTime} all-time</span>} />
        <KpiCard label="Pending" value={summary.pendingJobs} icon={Clock} color="var(--amber)"
          sub="In queue right now" />
        <KpiCard label="Active Rules" value={summary.activeRules} icon={CheckCircle} color="var(--blue)"
          sub="Across all channels" />
      </div>

      {/* Second Row: Success Rate + Health */}
      <div className="grid mb-24" style={{ gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        {/* Success rate */}
        <div className="card">
          <div className="section-header mb-12">
            <div className="section-title"><Activity size={14} /> Delivery Health</div>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6 }}>
                <span className="fs-12 text-secondary">Success rate</span>
                <span className="fw-700" style={{ color: summary.successRate >= 90 ? 'var(--green)' : 'var(--amber)' }}>{summary.successRate}%</span>
              </div>
              <div style={{ height: 6, background: 'var(--border)', borderRadius: 3, overflow: 'hidden' }}>
                <div style={{ width: `${summary.successRate}%`, height: '100%', background: summary.successRate >= 90 ? 'var(--green)' : 'var(--amber)', borderRadius: 3, transition: 'width 0.6s ease' }} />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span className="fs-12 text-secondary">Avg delivery</span>
              <span className="fw-600 fs-13">{summary.avgDeliveryMs} ms</span>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '10px 14px', background: healthOk ? 'var(--green-dim)' : 'var(--red-dim)', borderRadius: 8 }}>
              <span className={`dot dot-${healthOk ? 'green' : 'red'} pulse`} />
              <span className="fw-600 fs-12" style={{ color: healthOk ? 'var(--green)' : 'var(--red)' }}>
                Service {summary.serviceStatus}
              </span>
            </div>
          </div>
        </div>

        {/* Service health */}
        <div className="card">
          <div className="section-header mb-12">
            <div className="section-title"><Cpu size={14} /> System Status</div>
          </div>
          <div>
            {health.slice(0, 4).map((h, i) => (
              <div key={i} className="health-item">
                <div className="health-name">
                  <span className={`dot dot-${h.status === 'UP' ? 'green' : h.status === 'STUB' ? 'amber' : 'red'}`} />
                  <span className="fs-12">{h.name}</span>
                </div>
                <div className="health-meta">
                  <div className={`health-status-text fs-12 text-${h.status === 'UP' ? 'green' : h.status === 'STUB' ? 'amber' : 'red'}`}>{h.status}</div>
                  {h.responseTimeMs != null && <div className="health-rt">{h.responseTimeMs}ms</div>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Recent Jobs */}
      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '16px 20px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div className="section-title"><MailCheck size={14} /> Recent Jobs</div>
          <Link to="/history" className="btn btn-ghost btn-sm">View all →</Link>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th><th>Rule</th><th>Channel</th><th>To</th><th>Subject</th><th>Status</th><th>Time</th>
              </tr>
            </thead>
            <tbody>
              {jobs.length === 0 ? (
                <tr><td colSpan={7} className="empty-state">No jobs yet</td></tr>
              ) : jobs.map(j => (
                <tr key={j.jobId}>
                  <td className="td-mono">#{j.jobId}</td>
                  <td className="fs-12">{j.ruleCode || <span className="text-muted">direct</span>}</td>
                  <td><ChannelBadge channel={j.channel} /></td>
                  <td className="fs-12 text-secondary truncate" style={{ maxWidth: 180 }}>{(j.toAddresses || []).join(', ')}</td>
                  <td className="fs-12 truncate" style={{ maxWidth: 220 }}>{j.subject}</td>
                  <td><StatusBadge status={j.status} /></td>
                  <td className="td-muted">{fmt(j.createdAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
