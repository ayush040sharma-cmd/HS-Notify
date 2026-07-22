import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Search, RefreshCw, X, FileText } from 'lucide-react';

const LEVELS = ['', 'INFO', 'WARN', 'ERROR', 'DEBUG'];

function LevelBadge({ level }) {
  return <span className={`badge badge-${level.toLowerCase()}`}>{level}</span>;
}

export default function LogsPage() {
  const [logs, setLogs] = useState(null);
  const [levelFilter, setLevelFilter] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(false);

  const load = () => {
    setLoading(true);
    if (api.systemLogs) {
      api.systemLogs(levelFilter || undefined).then(r => setLogs(r.content)).finally(() => setLoading(false));
    } else {
      setLogs([]); setLoading(false);
    }
  };

  useEffect(() => { load(); }, [levelFilter]);

  const filtered = (logs || []).filter(l => {
    if (!search) return true;
    const q = search.toLowerCase();
    return l.message?.toLowerCase().includes(q) || l.component?.toLowerCase().includes(q);
  });

  return (
    <div>
      <div className="filter-bar mb-16">
        <div style={{ position: 'relative', flex: 1, minWidth: 200 }}>
          <Search size={13} style={{ position: 'absolute', left: 10, top: '50%', transform: 'translateY(-50%)', color: 'var(--text-muted)' }} />
          <input className="input" style={{ paddingLeft: 30 }} placeholder="Search component, message…" value={search} onChange={e => setSearch(e.target.value)} />
        </div>
        <select className="select" value={levelFilter} onChange={e => setLevelFilter(e.target.value)}>
          {LEVELS.map(l => <option key={l} value={l}>{l || 'All levels'}</option>)}
        </select>
        <button className="btn btn-ghost btn-sm" onClick={load} disabled={loading}><RefreshCw size={13} className={loading ? 'pulse' : ''} /> Refresh</button>
        {(levelFilter || search) && (
          <button className="btn btn-ghost btn-sm" onClick={() => { setLevelFilter(''); setSearch(''); }}>
            <X size={13} /> Clear
          </button>
        )}
      </div>

      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', gap: 8 }}>
          <FileText size={13} color="var(--text-muted)" />
          <span className="fs-12 text-secondary">{filtered.length} log entries</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Timestamp</th><th>Level</th><th>Component</th><th>Message</th></tr>
            </thead>
            <tbody>
              {logs === null ? (
                <tr><td colSpan={4} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
              ) : filtered.length === 0 ? (
                <tr><td colSpan={4} className="empty-state">No logs match the filter</td></tr>
              ) : filtered.slice().reverse().map(l => (
                <tr key={l.logId}>
                  <td className="td-mono">{new Date(l.ts).toLocaleString()}</td>
                  <td><LevelBadge level={l.level} /></td>
                  <td className="fs-12" style={{ fontFamily: 'monospace', color: 'var(--text-secondary)' }}>{l.component}</td>
                  <td className={`log-message log-level-${l.level.toLowerCase()}`}>{l.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
