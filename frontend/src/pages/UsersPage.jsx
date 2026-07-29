import React, { useEffect, useState } from 'react';
import { api, auth } from '../api/client.js';
import { Users, Key, Shield, Plus, Pencil, X, CheckCircle, AlertTriangle, XCircle } from 'lucide-react';

const ROLES = ['ADMIN', 'RAFM_HEAD', 'MANAGER', 'ANALYST', 'VIEWER'];

function RoleBadge({ role }) { return <span className={`badge badge-${role.toLowerCase()}`}>{role}</span>; }
function StatusBadge({ s }) { return <span className={`badge badge-${s.toLowerCase()}`}>{s}</span>; }

function toForm(u) {
  return { username: u.username, displayName: u.name || '', email: u.email || '', role: u.role, password: '', active: u.status !== 'INACTIVE' };
}

export default function UsersPage() {
  const [users, setUsers] = useState(null);
  const [keys, setKeys] = useState(null);
  const [tab, setTab] = useState('users');
  const [editing, setEditing] = useState(null); // { form, userId | null }
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [pageError, setPageError] = useState(null);
  const [pageOk, setPageOk] = useState(null);

  const isAdmin = auth.isAdmin();
  const load = () => api.users().then(setUsers);

  useEffect(() => {
    load();
    if (api.apiKeys) api.apiKeys().then(setKeys);
  }, []);

  const openCreate = () => { setError(null); setEditing({ form: { username: '', displayName: '', email: '', role: 'VIEWER', password: '', active: true }, userId: null } ); };
  const openEdit = (u) => { setError(null); setEditing({ form: toForm(u), userId: u.userId }); };

  const save = async () => {
    setError(null);
    setSaving(true);
    try {
      const payload = { ...editing.form, password: editing.form.password || null };
      if (editing.userId) {
        await api.updateUser(editing.userId, payload);
      } else {
        await api.createUser(payload);
      }
      setEditing(null);
      setPageOk(editing.userId ? 'User updated' : 'User created');
      load();
    } catch (e) {
      setError(e?.response?.data?.error || e?.response?.data?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      {pageError && <div className="error-banner mb-16"><XCircle size={14} />{pageError}</div>}
      {pageOk && <div className="success-banner mb-16"><CheckCircle size={14} />{pageOk}</div>}

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
            {isAdmin && (
              <button className="btn btn-primary btn-sm" onClick={openCreate}><Plus size={13} /> New User</button>
            )}
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Name</th><th>Username</th><th>Email</th><th>Role</th><th>Status</th>{isAdmin && <th></th>}</tr>
              </thead>
              <tbody>
                {users === null ? (
                  <tr><td colSpan={isAdmin ? 6 : 5} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
                ) : users.map(u => (
                  <tr key={u.userId}>
                    <td className="fw-500">{u.name}</td>
                    <td className="fs-12 td-mono">{u.username}</td>
                    <td className="fs-12 text-secondary">{u.email || '—'}</td>
                    <td><RoleBadge role={u.role} /></td>
                    <td><StatusBadge s={u.status} /></td>
                    {isAdmin && (
                      <td><button className="btn btn-ghost btn-sm" onClick={() => openEdit(u)}><Pencil size={12} /> Edit</button></td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!isAdmin && (
            <div style={{ padding: '10px 16px', borderTop: '1px solid var(--border)' }} className="fs-12 text-muted">
              Only ADMIN can create or edit users.
            </div>
          )}
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

      {editing && (
        <>
          <div className="drawer-overlay" onClick={() => !saving && setEditing(null)} />
          <div className="drawer">
            <div className="drawer-header">
              <div className="drawer-title">{editing.userId ? 'Edit User' : 'New User'}</div>
              <button className="btn btn-ghost btn-icon" onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              {error && <div className="error-banner mb-16"><AlertTriangle size={14} />{error}</div>}

              <div className="form-group mb-12">
                <label className="form-label">Username</label>
                <input className="input" value={editing.form.username}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, username: e.target.value } }))}
                  placeholder="e.g. jsmith" />
              </div>
              <div className="form-group mb-12">
                <label className="form-label">Display Name</label>
                <input className="input" value={editing.form.displayName}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, displayName: e.target.value } }))} />
              </div>
              <div className="form-group mb-12">
                <label className="form-label">Email</label>
                <input className="input" type="email" value={editing.form.email}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, email: e.target.value } }))}
                  placeholder="used for the CURRENT_USER recipient type" />
              </div>
              <div className="form-group mb-12">
                <label className="form-label">Role</label>
                <select className="select" value={editing.form.role}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, role: e.target.value } }))}>
                  {ROLES.map(r => <option key={r} value={r}>{r}</option>)}
                </select>
              </div>
              <div className="form-group mb-12">
                <label className="form-label">{editing.userId ? 'Reset Password (optional)' : 'Password'}</label>
                <input className="input" type="password" value={editing.form.password}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, password: e.target.value } }))}
                  placeholder={editing.userId ? 'leave blank to keep current password' : 'required'} />
              </div>

              <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, marginBottom: 16 }}>
                <span className="toggle">
                  <input type="checkbox" checked={editing.form.active}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, active: e.target.checked } }))} />
                  <span className="toggle-slider" />
                </span>
                Active
              </label>

              <button className="btn btn-primary" onClick={save}
                disabled={saving || !editing.form.username || !editing.form.role || (!editing.userId && !editing.form.password)}>
                {saving ? 'Saving…' : <><CheckCircle size={13} /> {editing.userId ? 'Save Changes' : 'Create User'}</>}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
