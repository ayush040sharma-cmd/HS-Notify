import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Package, X, Plus, Pencil, Trash2, CheckCircle, AlertTriangle, XCircle, ArrowUp, ArrowDown } from 'lucide-react';

function schemaToForm(schema) {
  return { name: schema.name, description: schema.description || '', providerKeys: [...(schema.providerKeys || [])] };
}

export default function AttachmentSchemas() {
  const [schemas, setSchemas] = useState(null);
  const [providers, setProviders] = useState([]);
  const [editing, setEditing] = useState(null); // { form, schemaId | null }
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [pageError, setPageError] = useState(null);
  const [pageOk, setPageOk] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = () => api.listAttachmentSchemas().then(setSchemas);
  useEffect(() => {
    load();
    api.listAttachmentProviders().then(setProviders).catch(() => setProviders([]));
  }, []);

  const openCreate = () => { setError(null); setEditing({ form: { name: '', description: '', providerKeys: [] }, schemaId: null }); };
  const openEdit = (schema) => { setError(null); setEditing({ form: schemaToForm(schema), schemaId: schema.id }); };

  const save = async () => {
    setError(null);
    setSaving(true);
    try {
      const payload = { name: editing.form.name, description: editing.form.description || null, providerKeys: editing.form.providerKeys };
      if (editing.schemaId) {
        await api.updateAttachmentSchema(editing.schemaId, payload);
      } else {
        await api.createAttachmentSchema(payload);
      }
      setEditing(null);
      load();
    } catch (e) {
      setError(e?.response?.data?.error || e?.response?.data?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  const remove = async (schema) => {
    if (!window.confirm(`Delete attachment schema "${schema.name}"? This cannot be undone.`)) return;
    setPageError(null); setPageOk(null); setBusyId(schema.id);
    try {
      await api.deleteAttachmentSchema(schema.id);
      setPageOk('Attachment schema deleted');
      load();
    } catch (e) {
      setPageError(e?.response?.data?.error || e?.response?.data?.message || 'Delete failed');
    } finally {
      setBusyId(null);
    }
  };

  const toggleProvider = (key) => {
    setEditing(s => {
      const has = s.form.providerKeys.includes(key);
      const providerKeys = has ? s.form.providerKeys.filter(k => k !== key) : [...s.form.providerKeys, key];
      return { ...s, form: { ...s.form, providerKeys } };
    });
  };

  const moveProvider = (idx, dir) => setEditing(s => {
    const keys = [...s.form.providerKeys];
    const j = idx + dir;
    if (j < 0 || j >= keys.length) return s;
    [keys[idx], keys[j]] = [keys[j], keys[idx]];
    return { ...s, form: { ...s.form, providerKeys: keys } };
  });

  const providerName = (key) => providers.find(p => p.key === key)?.displayName || key;

  return (
    <div>
      {pageError && <div className="error-banner mb-16"><XCircle size={14} />{pageError}</div>}
      {pageOk && <div className="success-banner mb-16"><CheckCircle size={14} />{pageOk}</div>}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div className="fs-12 text-muted">
          Named, ordered bundles of Attachment Provider keys — link one to a Notification Action so every send
          through that action generates and zips all of its attachments automatically.
        </div>
        <button className="btn btn-primary btn-sm" onClick={openCreate}><Plus size={13} /> New Attachment Schema</button>
      </div>

      <div className="grid grid-3" style={{ gap: 16 }}>
        {schemas === null ? (
          [1, 2].map(i => <div key={i} className="card"><div className="skeleton" style={{ height: 100 }} /></div>)
        ) : schemas.map(s => (
          <div key={s.id} className="template-card">
            <div className="template-card-header">
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 700 }}><Package size={14} /> {s.name}</div>
              <div className="fs-12 text-secondary">{(s.providerKeys || []).length} provider{(s.providerKeys || []).length === 1 ? '' : 's'}</div>
            </div>
            <div className="template-card-body">
              <div className="fs-12 text-secondary">{s.description || 'No description'}</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 8 }}>
                {(s.providerKeys || []).map(k => <span key={k} className="var-chip">{k}</span>)}
              </div>
            </div>
            <div className="template-card-footer">
              <button className="btn btn-ghost btn-sm" onClick={() => openEdit(s)}><Pencil size={12} /> Edit</button>
              <button className="btn btn-ghost btn-sm" disabled={busyId === s.id} onClick={() => remove(s)}><Trash2 size={12} /> Delete</button>
            </div>
          </div>
        ))}
        {schemas && schemas.length === 0 && (
          <div className="text-muted fs-12">No attachment schemas yet — click "New Attachment Schema" to build one.</div>
        )}
      </div>

      {editing && (
        <>
          <div className="drawer-overlay" onClick={() => !saving && setEditing(null)} />
          <div className="drawer" style={{ width: 480 }}>
            <div className="drawer-header">
              <div className="drawer-title">{editing.schemaId ? 'Edit Attachment Schema' : 'New Attachment Schema'}</div>
              <button className="btn btn-ghost btn-icon" onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              {error && <div className="error-banner mb-16"><AlertTriangle size={14} />{error}</div>}

              <div className="form-group mb-12">
                <label className="form-label">Name</label>
                <input className="input" value={editing.form.name}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, name: e.target.value } }))}
                  placeholder="e.g. Case Summary Bundle" />
              </div>
              <div className="form-group mb-16">
                <label className="form-label">Description</label>
                <input className="input" value={editing.form.description}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, description: e.target.value } }))} />
              </div>

              <div className="drawer-section-title mb-8">Providers</div>
              <div className="fs-11 text-muted mb-8">Check to include, order determines attachment order in the zip.</div>
              {providers.map(p => (
                <label key={p.key} style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13, marginBottom: 6 }}>
                  <input type="checkbox" checked={editing.form.providerKeys.includes(p.key)} onChange={() => toggleProvider(p.key)} />
                  <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{p.key}</span>
                  {!p.available && <span className="badge badge-inactive fs-11">unavailable</span>}
                </label>
              ))}

              {editing.form.providerKeys.length > 0 && (
                <>
                  <div className="drawer-section-title mb-8 mt-16">Order</div>
                  {editing.form.providerKeys.map((k, idx) => (
                    <div key={k} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
                      <span className="fs-11 text-muted" style={{ width: 16 }}>{idx + 1}.</span>
                      <span className="fs-12" style={{ flex: 1 }}>{providerName(k)}</span>
                      <button className="btn btn-ghost btn-icon" disabled={idx === 0} onClick={() => moveProvider(idx, -1)}><ArrowUp size={12} /></button>
                      <button className="btn btn-ghost btn-icon" disabled={idx === editing.form.providerKeys.length - 1} onClick={() => moveProvider(idx, 1)}><ArrowDown size={12} /></button>
                    </div>
                  ))}
                </>
              )}

              <button className="btn btn-primary mt-16" onClick={save} disabled={saving || !editing.form.name}>
                {saving ? 'Saving…' : <><CheckCircle size={13} /> {editing.schemaId ? 'Save Changes' : 'Create Schema'}</>}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
