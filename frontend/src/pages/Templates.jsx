import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Layers, X, ChevronRight, RefreshCw, Plus, Pencil, CheckCircle, AlertTriangle, XCircle } from 'lucide-react';

function ChannelBadge({ c }) { return <span className={`badge badge-${(c||'EMAIL').toLowerCase()}`}>{c||'EMAIL'}</span>; }
function StatusBadge({ s }) { return <span className={`badge badge-${s.toLowerCase()}`}>{s}</span>; }

const CHANNELS = ['EMAIL', 'WHATSAPP'];
const STATUSES = ['DRAFT', 'PENDING_REVIEW', 'ACTIVE', 'DISABLED'];

const emptyForm = {
  templateCode: '', channel: 'EMAIL', subjectTemplate: '', bodyTemplate: '',
  allowedVariables: '', piiMaskFields: '', status: 'DRAFT',
};

function toForm(t) {
  return {
    templateCode: t.templateCode, channel: t.channel, subjectTemplate: t.subjectTemplate,
    bodyTemplate: t.bodyTemplate, allowedVariables: (t.allowedVariables || []).join(', '),
    piiMaskFields: (t.piiMaskFields || []).join(', '), status: t.status,
  };
}

function toPayload(form) {
  return {
    templateCode: form.templateCode,
    channel: form.channel,
    subjectTemplate: form.subjectTemplate,
    bodyTemplate: form.bodyTemplate,
    allowedVariables: form.allowedVariables.split(',').map(s => s.trim()).filter(Boolean),
    piiMaskFields: form.piiMaskFields.split(',').map(s => s.trim()).filter(Boolean),
    status: form.status,
  };
}

export default function Templates() {
  const [templates, setTemplates] = useState(null);
  const [selected, setSelected] = useState(null);
  const [editing, setEditing] = useState(null); // { form, templateId | null }
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [pageError, setPageError] = useState(null);
  const [pageOk, setPageOk] = useState(null);
  const [approvingId, setApprovingId] = useState(null);

  const load = () => api.listTemplates().then(setTemplates);
  useEffect(() => { load(); }, []);

  const openCreate = () => { setError(null); setEditing({ form: { ...emptyForm }, templateId: null }); };
  const openEdit = (t) => { setError(null); setEditing({ form: toForm(t), templateId: t.templateId }); setSelected(null); };

  const approve = async (templateId) => {
    setPageError(null); setPageOk(null); setApprovingId(templateId);
    try {
      await api.approveTemplate(templateId);
      setPageOk('Template approved and activated');
      setSelected(null);
      load();
    } catch (e) {
      setPageError(e?.response?.data?.error || e?.response?.data?.message || 'Approval failed');
    } finally {
      setApprovingId(null);
    }
  };

  const save = async () => {
    setError(null);
    setSaving(true);
    try {
      const payload = toPayload(editing.form);
      if (editing.templateId) {
        await api.updateTemplate(editing.templateId, payload);
      } else {
        await api.createTemplate(payload);
      }
      setEditing(null);
      load();
    } catch (e) {
      setError(e?.response?.data?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      {pageError && <div className="error-banner mb-16"><XCircle size={14} />{pageError}</div>}
      {pageOk && <div className="success-banner mb-16"><CheckCircle size={14} />{pageOk}</div>}

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <button className="btn btn-primary btn-sm" onClick={openCreate}><Plus size={13} /> New Template</button>
      </div>

      <div className="grid grid-3" style={{ gap: 16 }}>
        {templates === null ? (
          [1,2,3].map(i => <div key={i} className="card"><div className="skeleton" style={{ height: 140 }} /></div>)
        ) : templates.map(t => (
          <div key={t.templateId} className="template-card" onClick={() => setSelected(t)} style={{ cursor: 'pointer' }}>
            <div className="template-card-header">
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
                <div>
                  <div style={{ fontFamily: 'monospace', fontSize: 13, fontWeight: 700, color: 'var(--blue)', marginBottom: 4 }}>{t.templateCode}</div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <ChannelBadge c={t.channel} />
                    <StatusBadge s={t.status} />
                  </div>
                </div>
                <div style={{ fontSize: 11, color: 'var(--text-muted)', padding: '3px 7px', background: 'var(--surface)', borderRadius: 5 }}>v{t.version}</div>
              </div>
            </div>
            <div className="template-card-body">
              <div className="fs-12 text-secondary mb-8">Subject template</div>
              <div className="template-preview">{t.subjectTemplate}</div>
              <div className="fs-12 text-secondary mb-4 mt-8">Variables</div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                {t.allowedVariables.map(v => <span key={v} className="var-chip">{`{{${v}}}`}</span>)}
              </div>
              {t.piiMaskFields.length > 0 && (
                <>
                  <div className="fs-12 text-amber mb-4 mt-8">PII masked</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                    {t.piiMaskFields.map(v => <span key={v} className="var-chip" style={{ background: 'var(--amber-dim)', color: 'var(--amber)' }}>{v}</span>)}
                  </div>
                </>
              )}
            </div>
            <div className="template-card-footer">
              <button className="btn btn-ghost btn-sm" onClick={(e) => { e.stopPropagation(); openEdit(t); }}><Pencil size={12} /> Edit</button>
              <button className="btn btn-ghost btn-sm" onClick={(e) => { e.stopPropagation(); setSelected(t); }}>View Details <ChevronRight size={12} /></button>
            </div>
          </div>
        ))}
        {templates && templates.length === 0 && (
          <div className="text-muted fs-12">No templates yet — click "New Template" to create one.</div>
        )}
      </div>

      {selected && (
        <>
          <div className="drawer-overlay" onClick={() => setSelected(null)} />
          <div className="drawer">
            <div className="drawer-header">
              <div>
                <div className="drawer-title">{selected.templateCode}</div>
                <div style={{ display: 'flex', gap: 6, marginTop: 6 }}>
                  <ChannelBadge c={selected.channel} />
                  <StatusBadge s={selected.status} />
                </div>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setSelected(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              <div className="drawer-section">
                <div className="drawer-section-title">Metadata</div>
                <div className="drawer-row"><span className="drawer-key">ID</span><span className="drawer-val">{selected.templateId}</span></div>
                <div className="drawer-row"><span className="drawer-key">Version</span><span className="drawer-val">v{selected.version}</span></div>
                <div className="drawer-row"><span className="drawer-key">Channel</span><span className="drawer-val"><ChannelBadge c={selected.channel} /></span></div>
                <div className="drawer-row"><span className="drawer-key">Status</span><span className="drawer-val"><StatusBadge s={selected.status} /></span></div>
              </div>

              <div className="drawer-section">
                <div className="drawer-section-title">Subject Template</div>
                <div className="template-preview" style={{ fontSize: 13 }}>{selected.subjectTemplate}</div>
              </div>

              {selected.bodyTemplate && (
                <div className="drawer-section">
                  <div className="drawer-section-title">Body Template</div>
                  <div className="template-preview" style={{ whiteSpace: 'pre-wrap', maxHeight: 200, overflowY: 'auto' }}>{selected.bodyTemplate}</div>
                </div>
              )}

              <div className="drawer-section">
                <div className="drawer-section-title">Allowed Variables (DLP whitelist)</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                  {selected.allowedVariables.map(v => <span key={v} className="var-chip">{`{{${v}}}`}</span>)}
                </div>
              </div>

              {selected.piiMaskFields.length > 0 && (
                <div className="drawer-section">
                  <div className="drawer-section-title">PII Mask Fields</div>
                  <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                    {selected.piiMaskFields.map(v => <span key={v} className="var-chip" style={{ background: 'var(--amber-dim)', color: 'var(--amber)' }}>{v}</span>)}
                  </div>
                  <div className="fs-11 text-muted mt-8">These fields are masked before rendering. Enforced by TemplateRenderingService — cannot be bypassed.</div>
                </div>
              )}

              <div className="drawer-section">
                <div className="drawer-section-title">Actions</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(selected)}><Pencil size={12} /> Edit Template</button>
                  {selected.status === 'PENDING_REVIEW' && (
                    <button className="btn btn-green btn-sm" disabled={approvingId === selected.templateId}
                      onClick={() => approve(selected.templateId)}>
                      <CheckCircle size={12} /> Approve &amp; Activate
                    </button>
                  )}
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
              <div className="drawer-title">{editing.templateId ? 'Edit Template' : 'New Template'}</div>
              <button className="btn btn-ghost btn-icon" onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              {error && <div className="error-banner mb-16"><AlertTriangle size={14} />{error}</div>}

              <div className="form-group mb-12">
                <label className="form-label">Template Code</label>
                <input className="input" value={editing.form.templateCode}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, templateCode: e.target.value } }))}
                  placeholder="e.g. CASE_NOTE_MAIL" />
              </div>

              <div className="form-row mb-12">
                <div className="form-group">
                  <label className="form-label">Channel</label>
                  <select className="select" value={editing.form.channel}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, channel: e.target.value } }))}>
                    {CHANNELS.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Status</label>
                  <select className="select" value={editing.form.status}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, status: e.target.value } }))}>
                    {STATUSES.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Subject Template</label>
                <input className="input" value={editing.form.subjectTemplate}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, subjectTemplate: e.target.value } }))}
                  placeholder="e.g. Case {{case_id}} update" />
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Body Template (HTML)</label>
                <textarea className="textarea" rows={5} value={editing.form.bodyTemplate}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, bodyTemplate: e.target.value } }))}
                  placeholder="<p>Hello, {{case_id}}...</p>" />
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Allowed Variables (comma-separated — DLP whitelist)</label>
                <input className="input" value={editing.form.allowedVariables}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, allowedVariables: e.target.value } }))}
                  placeholder="case_id, severity, reason" />
              </div>

              <div className="form-group mb-16">
                <label className="form-label">PII Mask Fields (comma-separated)</label>
                <input className="input" value={editing.form.piiMaskFields}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, piiMaskFields: e.target.value } }))}
                  placeholder="account_number" />
              </div>

              <button className="btn btn-primary" onClick={save} disabled={saving || !editing.form.templateCode || !editing.form.subjectTemplate}>
                {saving ? 'Saving…' : <><CheckCircle size={13} /> {editing.templateId ? 'Save Changes' : 'Create Template'}</>}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
