import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Zap, X, ChevronRight, Plus, Pencil, Trash2, CheckCircle, AlertTriangle, XCircle } from 'lucide-react';

function StatusBadge({ enabled }) {
  return <span className={`badge badge-${enabled ? 'active' : 'inactive'}`}>{enabled ? 'Enabled' : 'Disabled'}</span>;
}

const CHANNELS = ['EMAIL', 'WEBHOOK', 'SLACK', 'WHATSAPP', 'SMS'];

const emptyForm = {
  code: '', displayName: '', description: '', enabled: true, approvalRequired: false,
  defaultChannel: 'EMAIL', icon: '', displayOrder: 0, roleRequired: '', formSchemaId: '',
  attachmentSchemaId: '',
};

function toForm(a) {
  return {
    code: a.code, displayName: a.displayName, description: a.description || '',
    enabled: a.enabled, approvalRequired: a.approvalRequired,
    defaultChannel: a.defaultChannel || 'EMAIL', icon: a.icon || '',
    displayOrder: a.displayOrder ?? 0, roleRequired: a.roleRequired || '',
    formSchemaId: a.formSchemaId ?? '',
    attachmentSchemaId: a.attachmentSchemaId ?? '',
  };
}

function toPayload(form) {
  return {
    code: form.code,
    displayName: form.displayName,
    description: form.description || null,
    enabled: form.enabled,
    approvalRequired: form.approvalRequired,
    defaultChannel: form.defaultChannel,
    icon: form.icon || null,
    displayOrder: Number(form.displayOrder) || 0,
    roleRequired: form.roleRequired || null,
    formSchemaId: form.formSchemaId === '' ? null : Number(form.formSchemaId),
    attachmentSchemaId: form.attachmentSchemaId === '' ? null : Number(form.attachmentSchemaId),
  };
}

export default function NotificationActions() {
  const [actions, setActions] = useState(null);
  const [schemas, setSchemas] = useState([]);
  const [attachmentSchemas, setAttachmentSchemas] = useState([]);
  const [selected, setSelected] = useState(null);
  const [editing, setEditing] = useState(null); // { form, actionId | null }
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [pageError, setPageError] = useState(null);
  const [pageOk, setPageOk] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = () => api.listActions().then(setActions);
  useEffect(() => {
    load();
    api.listFormSchemas().then(setSchemas).catch(() => setSchemas([]));
    api.listAttachmentSchemas().then(setAttachmentSchemas).catch(() => setAttachmentSchemas([]));
  }, []);

  const openCreate = () => { setError(null); setEditing({ form: { ...emptyForm }, actionId: null }); };
  const openEdit = (a) => { setError(null); setEditing({ form: toForm(a), actionId: a.id }); setSelected(null); };

  const save = async () => {
    setError(null);
    setSaving(true);
    try {
      const payload = toPayload(editing.form);
      if (editing.actionId) {
        await api.updateAction(editing.actionId, payload);
      } else {
        await api.createAction(payload);
      }
      setEditing(null);
      load();
    } catch (e) {
      setError(e?.response?.data?.error || e?.response?.data?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (action) => {
    if (!window.confirm(`Delete action "${action.code}"? This cannot be undone.`)) return;
    setPageError(null); setPageOk(null); setBusyId(action.id);
    try {
      await api.deleteAction(action.id);
      setPageOk('Action deleted');
      setSelected(null);
      load();
    } catch (e) {
      setPageError(e?.response?.data?.error || e?.response?.data?.message || 'Delete failed');
    } finally {
      setBusyId(null);
    }
  };

  const schemaName = (id) => schemas.find(s => s.id === id)?.name || null;
  const attachmentSchemaName = (id) => attachmentSchemas.find(s => s.id === id)?.name || null;

  return (
    <div>
      {pageError && <div className="error-banner mb-16"><XCircle size={14} />{pageError}</div>}
      {pageOk && <div className="success-banner mb-16"><CheckCircle size={14} />{pageOk}</div>}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div className="fs-12 text-muted">
          The Notification Action Registry — replaces hardcoded scenario codes. Every enabled action here can be
          used as the <code>action</code> value for POST /api/v1/notify or the legacy <code>scenario</code> field.
        </div>
        <button className="btn btn-primary btn-sm" onClick={openCreate}><Plus size={13} /> New Action</button>
      </div>

      <div className="grid grid-3" style={{ gap: 16 }}>
        {actions === null ? (
          [1, 2, 3].map(i => <div key={i} className="card"><div className="skeleton" style={{ height: 120 }} /></div>)
        ) : actions.map(a => (
          <div key={a.id} className="template-card" onClick={() => setSelected(a)} style={{ cursor: 'pointer' }}>
            <div className="template-card-header">
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
                <div>
                  <div style={{ fontFamily: 'monospace', fontSize: 13, fontWeight: 700, color: 'var(--blue)', marginBottom: 4 }}>{a.code}</div>
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <StatusBadge enabled={a.enabled} />
                    <span className={`badge badge-${(a.defaultChannel || 'email').toLowerCase()}`}>{a.defaultChannel}</span>
                    {a.approvalRequired && <span className="badge badge-pending_review">Approval required</span>}
                  </div>
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>#{a.displayOrder}</div>
              </div>
            </div>
            <div className="template-card-body">
              <div style={{ fontWeight: 600, marginBottom: 4 }}>{a.displayName}</div>
              <div className="fs-12 text-secondary">{a.description || 'No description'}</div>
              {a.formSchemaId && (
                <div className="fs-11 text-muted mt-8">Form: {schemaName(a.formSchemaId) || `#${a.formSchemaId}`}</div>
              )}
            </div>
            <div className="template-card-footer">
              <button className="btn btn-ghost btn-sm" onClick={(e) => { e.stopPropagation(); openEdit(a); }}><Pencil size={12} /> Edit</button>
              <button className="btn btn-ghost btn-sm" disabled={busyId === a.id} onClick={(e) => { e.stopPropagation(); remove(a); }}><Trash2 size={12} /> Delete</button>
              <button className="btn btn-ghost btn-sm" onClick={(e) => { e.stopPropagation(); setSelected(a); }}>Details <ChevronRight size={12} /></button>
            </div>
          </div>
        ))}
        {actions && actions.length === 0 && (
          <div className="text-muted fs-12">No actions yet — click "New Action" to create one.</div>
        )}
      </div>

      {selected && (
        <>
          <div className="drawer-overlay" onClick={() => setSelected(null)} />
          <div className="drawer">
            <div className="drawer-header">
              <div>
                <div className="drawer-title">{selected.code}</div>
                <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                  <StatusBadge enabled={selected.enabled} />
                </div>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setSelected(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              <div className="drawer-section">
                <div className="drawer-section-title">Metadata</div>
                <div className="drawer-row"><span className="drawer-key">Display Name</span><span className="drawer-val">{selected.displayName}</span></div>
                <div className="drawer-row"><span className="drawer-key">Description</span><span className="drawer-val">{selected.description || '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Default Channel</span><span className="drawer-val">{selected.defaultChannel}</span></div>
                <div className="drawer-row"><span className="drawer-key">Display Order</span><span className="drawer-val">{selected.displayOrder}</span></div>
                <div className="drawer-row"><span className="drawer-key">Approval Required</span><span className="drawer-val">{selected.approvalRequired ? 'Yes' : 'No'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Role Required</span><span className="drawer-val">{selected.roleRequired || '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Form Schema</span><span className="drawer-val">{selected.formSchemaId ? (schemaName(selected.formSchemaId) || `#${selected.formSchemaId}`) : '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Attachment Schema</span><span className="drawer-val">{selected.attachmentSchemaId ? (attachmentSchemaName(selected.attachmentSchemaId) || `#${selected.attachmentSchemaId}`) : '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Created By</span><span className="drawer-val">{selected.createdBy}</span></div>
              </div>
              <div className="drawer-section">
                <div className="drawer-section-title">Actions</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(selected)}><Pencil size={12} /> Edit Action</button>
                  <button className="btn btn-danger btn-sm" disabled={busyId === selected.id} onClick={() => remove(selected)}><Trash2 size={12} /> Delete Action</button>
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      {editing && (
        <>
          <div className="drawer-overlay" onClick={() => !saving && setEditing(null)} />
          <div className="drawer">
            <div className="drawer-header">
              <div className="drawer-title">{editing.actionId ? 'Edit Action' : 'New Action'}</div>
              <button className="btn btn-ghost btn-icon" onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              {error && <div className="error-banner mb-16"><AlertTriangle size={14} />{error}</div>}

              <div className="form-group mb-12">
                <label className="form-label">Code</label>
                <input className="input" value={editing.form.code}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, code: e.target.value.toUpperCase() } }))}
                  placeholder="e.g. VENDOR_EMAIL" />
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Display Name</label>
                <input className="input" value={editing.form.displayName}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, displayName: e.target.value } }))}
                  placeholder="e.g. Vendor Notification" />
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Description</label>
                <textarea className="textarea" rows={2} value={editing.form.description}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, description: e.target.value } }))} />
              </div>

              <div className="form-row mb-12">
                <div className="form-group">
                  <label className="form-label">Default Channel</label>
                  <select className="select" value={editing.form.defaultChannel}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, defaultChannel: e.target.value } }))}>
                    {CHANNELS.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Display Order</label>
                  <input className="input" type="number" value={editing.form.displayOrder}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, displayOrder: e.target.value } }))} />
                </div>
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Form Schema (optional — for the dynamic Send Notification wizard)</label>
                <select className="select" value={editing.form.formSchemaId}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, formSchemaId: e.target.value } }))}>
                  <option value="">— none —</option>
                  {schemas.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Attachment Schema (optional — providers to generate and zip on send)</label>
                <select className="select" value={editing.form.attachmentSchemaId}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, attachmentSchemaId: e.target.value } }))}>
                  <option value="">— none —</option>
                  {attachmentSchemas.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Role Required (optional)</label>
                <input className="input" value={editing.form.roleRequired}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, roleRequired: e.target.value } }))}
                  placeholder="e.g. MANAGER — not yet enforced (see RBAC phase)" />
              </div>

              <div style={{ display: 'flex', gap: 24, marginBottom: 16 }}>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                  <span className="toggle">
                    <input type="checkbox" checked={editing.form.enabled}
                      onChange={e => setEditing(s => ({ ...s, form: { ...s.form, enabled: e.target.checked } }))} />
                    <span className="toggle-slider" />
                  </span>
                  Enabled
                </label>
                <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                  <span className="toggle">
                    <input type="checkbox" checked={editing.form.approvalRequired}
                      onChange={e => setEditing(s => ({ ...s, form: { ...s.form, approvalRequired: e.target.checked } }))} />
                    <span className="toggle-slider" />
                  </span>
                  Approval required
                </label>
              </div>

              <button className="btn btn-primary" onClick={save}
                disabled={saving || !editing.form.code || !editing.form.displayName}>
                {saving ? 'Saving…' : <><CheckCircle size={13} /> {editing.actionId ? 'Save Changes' : 'Create Action'}</>}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
