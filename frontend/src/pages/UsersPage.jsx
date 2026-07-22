import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Users, Key, RefreshCw, Shield, Clock } from 'lucide-react';

function RoleBadge({ role }) { return <span className={`badge badge-${role.toLowerCase()}`}>{role}</span>; }
function StatusBadge({ s }) { return <span className={`badge badge-${s.toLowerCase()}`}>{s}</span>; }

export default function UsersPage() {
  const [users, setUsers] = useState(null);
  const [keys, setKeys] = useState(null);
  const [tab, setTab] = useState('users');

  useEffect(() => {
    if (api.users) api.users().then(setUsers);
    if (api.apiKeys) api.apiKeys().then(setKeys);
  }, []);

  return (
    <div>
      <div className="tabs">
        <div className={`tab ${tab === 'users' ? 'active' : ''}`} onClick={() => setTab('users')}>
          Users {users && <span className="badge badge-info" style={{ marginLeft: 6, padding: '1px 6px' }}>{users.length}</span>}
        </div>
        <div className={`tab ${tab === 'keys' ? 'active' : ''}`} onClick={() => setTab('keys')}>
          API Keys {keys && <span className="badge badge-info" style={{ marginLeft: 6, padding: '1px 6px' }}>{keys.length}</span>}
        </div>
      </div>

      {tab === 'users' && (
        <div className="card" style={{ padding: 0 }}>
          <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div className="section-title"><Users size={14} /> User Management</div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Name</th><th>Email</th><th>Role</th><th>Status</th><th>Last Login</th></tr>
              </thead>
              <tbody>
                {users === null ? (
                  <tr><td colSpan={5} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
                ) : users.map(u => (
                  <tr key={u.userId}>
                    <td className="fw-500">{u.name}</td>
                    <td className="fs-12 text-secondary">{u.email}</td>
                    <td><RoleBadge role={u.role} /></td>
                    <td><StatusBadge s={u.status} /></td>
                    <td className="td-muted">{u.lastLogin ? new Date(u.lastLogin).toLocaleString() : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'keys' && (
        <div className="card" style={{ padding: 0 }}>
          <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div className="section-title"><Key size={14} /> API Keys</div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Label</th><th>Prefix</th><th>Tenant</th><th>Status</th><th>Created</th><th>Last Used</th></tr>
              </thead>
              <tbody>
                {keys === null ? (
                  <tr><td colSpan={6} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
                ) : keys.map(k => (
                  <tr key={k.keyId}>
                    <td className="fw-500">{k.label}</td>
                    <td className="td-mono">{k.prefix}••••••••</td>
                    <td><span className="badge badge-email">{k.tenantCode}</span></td>
                    <td><StatusBadge s={k.status} /></td>
                    <td className="td-muted">{new Date(k.createdAt).toLocaleDateString()}</td>
                    <td className="td-muted">{k.lastUsedAt ? new Date(k.lastUsedAt).toLocaleString() : '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ padding: '12px 16px', borderTop: '1px solid var(--border)' }}>
            <div className="fs-12 text-muted" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <Shield size={12} /> Keys are BCrypt-hashed in the database. The full key is only shown once at creation time. Use <code style={{ fontFamily: 'monospace', fontSize: 11, color: 'var(--text-secondary)' }}>X-HS-API-Key</code> header in all API requests.
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
