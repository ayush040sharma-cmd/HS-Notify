import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { LayoutTemplate, X, Plus, Pencil, Trash2, CheckCircle, AlertTriangle, XCircle, ArrowUp, ArrowDown } from 'lucide-react';

const FIELD_TYPES = ['TEXTBOX', 'TEXTAREA', 'DROPDOWN', 'CHECKBOX', 'RADIO', 'DATE', 'EMAIL', 'FILE_UPLOAD', 'DYNAMIC_LOOKUP'];
const OPTION_TYPES = ['DROPDOWN', 'RADIO'];

let keySeq = 0;
const nextKey = () => `f${Date.now()}_${keySeq++}`;

function emptyField() {
  return {
    _key: nextKey(), fieldKey: '', label: '', fieldType: 'TEXTBOX',
    placeholder: '', helpText: '', required: false, defaultValue: '',
    lookupSource: '', conditionalOnFieldKey: '', conditionalOnValue: '',
    maxLength: '', options: [],
  };
}

function schemaToForm(schema) {
  return {
    name: schema.name, description: schema.description || '',
    fields: (schema.fields || []).map(f => ({
      _key: nextKey(),
      fieldKey: f.fieldKey, label: f.label, fieldType: f.fieldType,
      placeholder: f.placeholder || '', helpText: f.helpText || '',
      required: !!f.required, defaultValue: f.defaultValue || '',
      lookupSource: f.lookupSource || '',
      conditionalOnFieldKey: f.conditionalOnFieldKey || '', conditionalOnValue: f.conditionalOnValue || '',
      maxLength: (f.validations || []).find(v => v.validationType === 'MAX_LENGTH')?.validationValue || '',
      options: (f.options || []).map(o => ({ optionValue: o.optionValue, optionLabel: o.optionLabel })),
    })),
  };
}

function formToPayload(form) {
  return {
    name: form.name,
    description: form.description || null,
    fields: form.fields.map((f, i) => {
      const validations = [];
      if (f.maxLength) validations.push({ validationType: 'MAX_LENGTH', validationValue: String(f.maxLength), errorMessage: `Must be under ${f.maxLength} characters` });
      if (f.fieldType === 'EMAIL') validations.push({ validationType: 'EMAIL_FORMAT', errorMessage: 'Must be a valid email address' });
      return {
        fieldKey: f.fieldKey, label: f.label, fieldType: f.fieldType,
        placeholder: f.placeholder || null, helpText: f.helpText || null,
        required: f.required, displayOrder: i * 10,
        defaultValue: f.defaultValue || null,
        lookupSource: f.fieldType === 'DYNAMIC_LOOKUP' ? (f.lookupSource || null) : null,
        conditionalOnFieldKey: f.conditionalOnFieldKey || null,
        conditionalOnValue: f.conditionalOnFieldKey ? (f.conditionalOnValue || null) : null,
        validations,
        options: OPTION_TYPES.includes(f.fieldType) ? f.options.filter(o => o.optionValue) : [],
      };
    }),
  };
}

export default function FormSchemas() {
  const [schemas, setSchemas] = useState(null);
  const [editing, setEditing] = useState(null); // { form, schemaId | null }
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [pageError, setPageError] = useState(null);
  const [pageOk, setPageOk] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = () => api.listFormSchemas().then(setSchemas);
  useEffect(() => { load(); }, []);

  const openCreate = () => { setError(null); setEditing({ form: { name: '', description: '', fields: [] }, schemaId: null }); };
  const openEdit = (schema) => { setError(null); setEditing({ form: schemaToForm(schema), schemaId: schema.id }); };

  const save = async () => {
    setError(null);
    setSaving(true);
    try {
      const payload = formToPayload(editing.form);
      if (editing.schemaId) {
        await api.updateFormSchema(editing.schemaId, payload);
      } else {
        await api.createFormSchema(payload);
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
    if (!window.confirm(`Delete form schema "${schema.name}"? This cannot be undone.`)) return;
    setPageError(null); setPageOk(null); setBusyId(schema.id);
    try {
      await api.deleteFormSchema(schema.id);
      setPageOk('Form schema deleted');
      load();
    } catch (e) {
      setPageError(e?.response?.data?.error || e?.response?.data?.message || 'Delete failed');
    } finally {
      setBusyId(null);
    }
  };

  const updateField = (idx, patch) => {
    setEditing(s => ({ ...s, form: { ...s.form, fields: s.form.fields.map((f, i) => i === idx ? { ...f, ...patch } : f) } }));
  };
  const addField = () => setEditing(s => ({ ...s, form: { ...s.form, fields: [...s.form.fields, emptyField()] } }));
  const removeField = (idx) => setEditing(s => ({ ...s, form: { ...s.form, fields: s.form.fields.filter((_, i) => i !== idx) } }));
  const moveField = (idx, dir) => setEditing(s => {
    const fields = [...s.form.fields];
    const j = idx + dir;
    if (j < 0 || j >= fields.length) return s;
    [fields[idx], fields[j]] = [fields[j], fields[idx]];
    return { ...s, form: { ...s.form, fields } };
  });

  return (
    <div>
      {pageError && <div className="error-banner mb-16"><XCircle size={14} />{pageError}</div>}
      {pageOk && <div className="success-banner mb-16"><CheckCircle size={14} />{pageOk}</div>}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <div className="fs-12 text-muted">
          Reusable form definitions — link one to a Notification Action to drive the dynamic Send Notification wizard.
        </div>
        <button className="btn btn-primary btn-sm" onClick={openCreate}><Plus size={13} /> New Form Schema</button>
      </div>

      <div className="grid grid-3" style={{ gap: 16 }}>
        {schemas === null ? (
          [1, 2].map(i => <div key={i} className="card"><div className="skeleton" style={{ height: 100 }} /></div>)
        ) : schemas.map(s => (
          <div key={s.id} className="template-card">
            <div className="template-card-header">
              <div style={{ fontWeight: 700, marginBottom: 4 }}>{s.name}</div>
              <div className="fs-12 text-secondary">v{s.version} · {(s.fields || []).length} field{(s.fields || []).length === 1 ? '' : 's'}</div>
            </div>
            <div className="template-card-body">
              <div className="fs-12 text-secondary">{s.description || 'No description'}</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 8 }}>
                {(s.fields || []).slice(0, 6).map(f => <span key={f.id} className="var-chip">{f.fieldKey}</span>)}
              </div>
            </div>
            <div className="template-card-footer">
              <button className="btn btn-ghost btn-sm" onClick={() => openEdit(s)}><Pencil size={12} /> Edit</button>
              <button className="btn btn-ghost btn-sm" disabled={busyId === s.id} onClick={() => remove(s)}><Trash2 size={12} /> Delete</button>
            </div>
          </div>
        ))}
        {schemas && schemas.length === 0 && (
          <div className="text-muted fs-12">No form schemas yet — click "New Form Schema" to build one.</div>
        )}
      </div>

      {editing && (
        <>
          <div className="drawer-overlay" onClick={() => !saving && setEditing(null)} />
          <div className="drawer" style={{ width: 520 }}>
            <div className="drawer-header">
              <div className="drawer-title">{editing.schemaId ? 'Edit Form Schema' : 'New Form Schema'}</div>
              <button className="btn btn-ghost btn-icon" onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              {error && <div className="error-banner mb-16"><AlertTriangle size={14} />{error}</div>}

              <div className="form-group mb-12">
                <label className="form-label">Name</label>
                <input className="input" value={editing.form.name}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, name: e.target.value } }))}
                  placeholder="e.g. Vendor Notification Form" />
              </div>
              <div className="form-group mb-16">
                <label className="form-label">Description</label>
                <input className="input" value={editing.form.description}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, description: e.target.value } }))} />
              </div>

              <div className="drawer-section-title mb-8">Fields</div>
              {editing.form.fields.map((f, idx) => (
                <div key={f._key} className="card" style={{ padding: 12, marginBottom: 10 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 8 }}>
                    <span className="fs-11 text-muted">Field {idx + 1}</span>
                    <div style={{ display: 'flex', gap: 4 }}>
                      <button className="btn btn-ghost btn-icon" disabled={idx === 0} onClick={() => moveField(idx, -1)}><ArrowUp size={12} /></button>
                      <button className="btn btn-ghost btn-icon" disabled={idx === editing.form.fields.length - 1} onClick={() => moveField(idx, 1)}><ArrowDown size={12} /></button>
                      <button className="btn btn-ghost btn-icon" onClick={() => removeField(idx)}><Trash2 size={12} /></button>
                    </div>
                  </div>

                  <div className="form-row mb-8">
                    <div className="form-group">
                      <label className="form-label">Field Key</label>
                      <input className="input" value={f.fieldKey} onChange={e => updateField(idx, { fieldKey: e.target.value })} placeholder="e.g. severity" />
                    </div>
                    <div className="form-group">
                      <label className="form-label">Label</label>
                      <input className="input" value={f.label} onChange={e => updateField(idx, { label: e.target.value })} placeholder="e.g. Severity" />
                    </div>
                  </div>

                  <div className="form-row mb-8">
                    <div className="form-group">
                      <label className="form-label">Type</label>
                      <select className="select" value={f.fieldType} onChange={e => updateField(idx, { fieldType: e.target.value })}>
                        {FIELD_TYPES.map(t => <option key={t} value={t}>{t}</option>)}
                      </select>
                    </div>
                    <div className="form-group">
                      <label className="form-label">Placeholder</label>
                      <input className="input" value={f.placeholder} onChange={e => updateField(idx, { placeholder: e.target.value })} />
                    </div>
                  </div>

                  {f.fieldType === 'DYNAMIC_LOOKUP' && (
                    <div className="form-group mb-8">
                      <label className="form-label">Lookup Source</label>
                      <input className="input" value={f.lookupSource} onChange={e => updateField(idx, { lookupSource: e.target.value })} placeholder="e.g. recipient-groups" />
                    </div>
                  )}

                  {OPTION_TYPES.includes(f.fieldType) && (
                    <div className="form-group mb-8">
                      <label className="form-label">Options</label>
                      {f.options.map((o, oi) => (
                        <div key={oi} style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
                          <input className="input" style={{ flex: 1 }} placeholder="value" value={o.optionValue}
                            onChange={e => updateField(idx, { options: f.options.map((x, i) => i === oi ? { ...x, optionValue: e.target.value } : x) })} />
                          <input className="input" style={{ flex: 1 }} placeholder="label" value={o.optionLabel}
                            onChange={e => updateField(idx, { options: f.options.map((x, i) => i === oi ? { ...x, optionLabel: e.target.value } : x) })} />
                          <button className="btn btn-ghost btn-icon" onClick={() => updateField(idx, { options: f.options.filter((_, i) => i !== oi) })}><X size={12} /></button>
                        </div>
                      ))}
                      <button className="btn btn-ghost btn-sm" onClick={() => updateField(idx, { options: [...f.options, { optionValue: '', optionLabel: '' }] })}>
                        <Plus size={12} /> Add option
                      </button>
                    </div>
                  )}

                  {(f.fieldType === 'TEXTBOX' || f.fieldType === 'TEXTAREA' || f.fieldType === 'EMAIL') && (
                    <div className="form-group mb-8">
                      <label className="form-label">Max Length (optional)</label>
                      <input className="input" type="number" value={f.maxLength} onChange={e => updateField(idx, { maxLength: e.target.value })} />
                    </div>
                  )}

                  <div className="form-row mb-8">
                    <div className="form-group">
                      <label className="form-label">Show only if field key… (optional)</label>
                      <input className="input" value={f.conditionalOnFieldKey} onChange={e => updateField(idx, { conditionalOnFieldKey: e.target.value })} placeholder="e.g. priority" />
                    </div>
                    <div className="form-group">
                      <label className="form-label">…equals</label>
                      <input className="input" value={f.conditionalOnValue} onChange={e => updateField(idx, { conditionalOnValue: e.target.value })} disabled={!f.conditionalOnFieldKey} />
                    </div>
                  </div>

                  <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
                    <span className="toggle">
                      <input type="checkbox" checked={f.required} onChange={e => updateField(idx, { required: e.target.checked })} />
                      <span className="toggle-slider" />
                    </span>
                    Required
                  </label>
                </div>
              ))}

              <button className="btn btn-secondary btn-sm mb-16" onClick={addField}><Plus size={12} /> Add Field</button>

              <button className="btn btn-primary" onClick={save}
                disabled={saving || !editing.form.name || editing.form.fields.some(f => !f.fieldKey || !f.label)}>
                {saving ? 'Saving…' : <><CheckCircle size={13} /> {editing.schemaId ? 'Save Changes' : 'Create Schema'}</>}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
