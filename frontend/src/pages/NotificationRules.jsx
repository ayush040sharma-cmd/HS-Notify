import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { CheckCircle, XCircle, Clock, ChevronRight, X, RefreshCw, ListChecks, Plus, Pencil, AlertTriangle, Paperclip, UploadCloud } from 'lucide-react';

function RuleStatus({ status }) { return <span className={`badge badge-${status.toLowerCase().replace('_', '_')}`}>{status.replace('_', ' ')}</span>; }
function ChannelBadge({ c }) { return <span className={`badge badge-${(c||'EMAIL').toLowerCase()}`}>{c||'EMAIL'}</span>; }

const emptyForm = {
  ruleCode: '', triggerEvent: '', triggerSource: '', templateCode: '',
  recipientMode: 'group', recipientGroupCode: '', toEmails: '', ccEmails: '',
  escalationChainCode: '', maxRetryCount: 3, retryBackoffSeconds: 60, retryBackoffMultiplier: 2.0,
  onFinalFailure: 'ESCALATE',
  attachmentType: 'NONE', attachmentReportIdentifier: '', attachmentOutputFormat: 'PDF',
  attachmentOnFailure: 'SEND_WITHOUT_ATTACHMENT', attachmentFileName: '',
};

function toForm(r) {
  const attachmentType = r.attachmentType || 'NONE';
  const identifier = r.attachmentReportIdentifier || '';
  return {
    ruleCode: r.ruleCode, triggerEvent: r.triggerEvent, triggerSource: r.triggerSource || '',
    templateCode: r.template?.templateCode || '',
    recipientMode: 'group', recipientGroupCode: r.recipientGroupCode || '', toEmails: '', ccEmails: '',
    escalationChainCode: r.escalationChainCode || '', maxRetryCount: r.maxRetryCount,
    retryBackoffSeconds: r.retryBackoffSeconds, retryBackoffMultiplier: r.retryBackoffMultiplier,
    onFinalFailure: r.onFinalFailure,
    attachmentType,
    attachmentReportIdentifier: identifier,
    attachmentOutputFormat: r.attachmentOutputFormat || 'PDF',
    attachmentOnFailure: r.attachmentOnFailure || 'SEND_WITHOUT_ATTACHMENT',
    attachmentFileName: attachmentType === 'UPLOAD' ? identifier.split(/[\\/]/).pop() : '',
  };
}

function splitEmails(s) { return (s || '').split(',').map(v => v.trim()).filter(Boolean); }

function toPayload(form) {
  const payload = {
    ruleCode: form.ruleCode, triggerEvent: form.triggerEvent, triggerSource: form.triggerSource,
    templateCode: form.templateCode,
    escalationChainCode: form.escalationChainCode || null,
    maxRetryCount: Number(form.maxRetryCount), retryBackoffSeconds: Number(form.retryBackoffSeconds),
    retryBackoffMultiplier: Number(form.retryBackoffMultiplier), onFinalFailure: form.onFinalFailure,
    attachmentType: form.attachmentType || 'NONE',
    attachmentReportIdentifier: form.attachmentType === 'NONE' ? null : (form.attachmentReportIdentifier || null),
    attachmentOutputFormat: form.attachmentType === 'NONE' ? null : (form.attachmentOutputFormat || null),
    attachmentOnFailure: form.attachmentType === 'NONE' ? null : form.attachmentOnFailure,
  };
  if (form.recipientMode === 'custom') {
    payload.toEmails = splitEmails(form.toEmails);
    payload.ccEmails = splitEmails(form.ccEmails);
  } else {
    payload.recipientGroupCode = form.recipientGroupCode;
  }
  return payload;
}

const ATTACHMENT_TYPES = [
  { value: 'NONE', label: 'None' },
  { value: 'PR_RECORD', label: 'PR Record' },
  { value: 'UPLOAD', label: 'Image / PDF' },
];

export default function NotificationRules() {
  const [rules, setRules] = useState(null);
  const [options, setOptions] = useState(null);
  const [selected, setSelected] = useState(null);
  const [editing, setEditing] = useState(null); // { form, ruleId | null }
  const [busyId, setBusyId] = useState(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [ok, setOk] = useState(null);
  const [uploadingAttachment, setUploadingAttachment] = useState(false);

  const load = () => {
    api.listRules().then(setRules).catch(e => setError(e?.response?.data?.message || 'Failed to load rules'));
  };
  useEffect(() => { load(); api.ruleFormOptions().then(setOptions); }, []);

  const act = async (fn, id) => {
    setBusyId(id); setError(null); setOk(null);
    try {
      await fn(id);
      setOk('Updated successfully');
      load();
      setSelected(s => s?.ruleId === id ? null : s);
    } catch (e) {
      setError(e?.response?.data?.message || e?.response?.data?.error || 'Action failed');
    } finally {
      setBusyId(null);
    }
  };

  const openCreate = () => { setError(null); setEditing({ form: { ...emptyForm }, ruleId: null }); };
  const openEdit = (r) => { setError(null); setEditing({ form: toForm(r), ruleId: r.ruleId }); setSelected(null); };

  const uploadAttachmentFile = async (file) => {
    if (!file) return;
    setError(null);
    setUploadingAttachment(true);
    try {
      const uploaded = await api.uploadAttachment(file);
      const outputFormat = file.name.toLowerCase().endsWith('.pdf') ? 'PDF' : 'IMAGE';
      setEditing(s => ({ ...s, form: {
        ...s.form, attachmentReportIdentifier: uploaded.path, attachmentFileName: file.name, attachmentOutputFormat: outputFormat,
      } }));
    } catch (e) {
      setError(e?.response?.data?.error || 'File upload failed');
    } finally {
      setUploadingAttachment(false);
    }
  };

  const save = async () => {
    setError(null);
    setSaving(true);
    try {
      const payload = toPayload(editing.form);
      if (editing.ruleId) {
        await api.updateRule(editing.ruleId, payload);
      } else {
        await api.createRule(payload);
      }
      setEditing(null);
      setOk(editing.ruleId ? 'Rule updated' : 'Rule created as DRAFT');
      load();
    } catch (e) {
      setError(e?.response?.data?.error || e?.response?.data?.message || 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      {error && <div className="error-banner mb-16"><XCircle size={14} />{error}</div>}
      {ok && <div className="success-banner mb-16"><CheckCircle size={14} />{ok}</div>}

      <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 16 }}>
        <button className="btn btn-primary btn-sm" onClick={openCreate}><Plus size={13} /> New Rule</button>
      </div>

      <div className="card" style={{ padding: 0 }}>
        <div style={{ padding: '14px 16px', borderBottom: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div className="section-title"><ListChecks size={14} /> All Rules</div>
          <button className="btn btn-ghost btn-sm" onClick={load}><RefreshCw size={13} /></button>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Rule Code</th><th>Trigger</th><th>Template</th><th>Channel</th><th>Status</th>
                <th>Retries</th><th>On Failure</th><th>Created By</th><th>Approved By</th><th></th>
              </tr>
            </thead>
            <tbody>
              {rules === null ? (
                <tr><td colSpan={10} style={{ textAlign: 'center', padding: 24, color: 'var(--text-muted)' }}>Loading…</td></tr>
              ) : rules.map(r => (
                <tr key={r.ruleId} onClick={() => setSelected(r)} style={{ cursor: 'pointer' }}>
                  <td>
                    <code style={{ fontFamily: 'monospace', fontSize: 12, color: 'var(--blue)' }}>{r.ruleCode}</code>
                    {r.attachmentType && r.attachmentType !== 'NONE' && (
                      <Paperclip size={11} color="var(--text-muted)" style={{ marginLeft: 6, verticalAlign: 'middle' }} />
                    )}
                  </td>
                  <td className="fs-12">{r.triggerEvent}</td>
                  <td className="fs-12 td-mono">{r.template?.templateCode || '—'}</td>
                  <td><ChannelBadge c={r.template?.channel} /></td>
                  <td><RuleStatus status={r.status} /></td>
                  <td className="td-muted">{r.maxRetryCount}× / {r.retryBackoffSeconds}s</td>
                  <td className="fs-12">{r.onFinalFailure}</td>
                  <td className="td-muted">{r.createdBy}</td>
                  <td className="td-muted">{r.approvedBy || '—'}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                      <button className="btn btn-ghost btn-icon" onClick={(e) => { e.stopPropagation(); openEdit(r); }} title="Edit rule">
                        <Pencil size={13} />
                      </button>
                      <ChevronRight size={14} color="var(--text-muted)" />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Rule detail drawer (read-only view + status actions) */}
      {selected && (
        <>
          <div className="drawer-overlay" onClick={() => setSelected(null)} />
          <div className="drawer">
            <div className="drawer-header">
              <div>
                <div className="drawer-title">{selected.ruleCode}</div>
                <div className="fs-12 text-secondary mt-4">{selected.triggerEvent}</div>
              </div>
              <button className="btn btn-ghost btn-icon" onClick={() => setSelected(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              <div className="drawer-section">
                <div className="drawer-section-title">Status</div>
                <div style={{ display: 'flex', gap: 8 }}>
                  <RuleStatus status={selected.status} />
                  <span className={`badge badge-${selected.active ? 'active' : 'inactive'}`}>{selected.active ? 'Active' : 'Inactive'}</span>
                </div>
              </div>

              <div className="drawer-section">
                <div className="drawer-section-title">Template</div>
                <div className="drawer-row"><span className="drawer-key">Code</span><span className="drawer-val monospace">{selected.template?.templateCode}</span></div>
                <div className="drawer-row"><span className="drawer-key">Channel</span><span className="drawer-val"><ChannelBadge c={selected.template?.channel} /></span></div>
              </div>

              <div className="drawer-section">
                <div className="drawer-section-title">Recipients &amp; Escalation</div>
                <div className="drawer-row"><span className="drawer-key">Recipient group</span><span className="drawer-val monospace">{selected.recipientGroupCode || '—'}</span></div>
                <div className="drawer-row"><span className="drawer-key">Escalation chain</span><span className="drawer-val monospace">{selected.escalationChainCode || '—'}</span></div>
              </div>

              <div className="drawer-section">
                <div className="drawer-section-title">Attachment</div>
                <div className="drawer-row"><span className="drawer-key">Type</span><span className="drawer-val">{ATTACHMENT_TYPES.find(t => t.value === (selected.attachmentType || 'NONE'))?.label || 'None'}</span></div>
                {selected.attachmentType && selected.attachmentType !== 'NONE' && (
                  <>
                    <div className="drawer-row"><span className="drawer-key">Source</span><span className="drawer-val monospace">{selected.attachmentType === 'UPLOAD' ? (selected.attachmentReportIdentifier || '').split(/[\\/]/).pop() : selected.attachmentReportIdentifier}</span></div>
                    <div className="drawer-row"><span className="drawer-key">Format</span><span className="drawer-val">{selected.attachmentOutputFormat}</span></div>
                    <div className="drawer-row"><span className="drawer-key">On failure</span><span className="drawer-val">{selected.attachmentOnFailure}</span></div>
                  </>
                )}
              </div>

              <div className="drawer-section">
                <div className="drawer-section-title">Retry Config</div>
                <div className="drawer-row"><span className="drawer-key">Max retries</span><span className="drawer-val">{selected.maxRetryCount}</span></div>
                <div className="drawer-row"><span className="drawer-key">Initial backoff</span><span className="drawer-val">{selected.retryBackoffSeconds}s</span></div>
                <div className="drawer-row"><span className="drawer-key">Multiplier</span><span className="drawer-val">{selected.retryBackoffMultiplier}×</span></div>
                <div className="drawer-row"><span className="drawer-key">On final failure</span><span className="drawer-val">{selected.onFinalFailure}</span></div>
              </div>

              <div className="drawer-section">
                <div className="drawer-section-title">Maker-Checker</div>
                <div className="drawer-row"><span className="drawer-key">Created by</span><span className="drawer-val">{selected.createdBy}</span></div>
                <div className="drawer-row"><span className="drawer-key">Approved by</span><span className="drawer-val">{selected.approvedBy || '—'}</span></div>
              </div>

              {/* Actions */}
              <div className="drawer-section">
                <div className="drawer-section-title">Actions</div>
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                  <button className="btn btn-secondary btn-sm" onClick={() => openEdit(selected)}><Pencil size={12} /> Edit Rule</button>
                  {(selected.status === 'DRAFT' || selected.status === 'DISABLED') && (
                    <button className="btn btn-amber btn-sm" disabled={busyId === selected.ruleId} onClick={() => act(api.submitRuleForReview, selected.ruleId)}>
                      <Clock size={12} /> Submit for Review
                    </button>
                  )}
                  {selected.status === 'PENDING_REVIEW' && (
                    <button className="btn btn-green btn-sm" disabled={busyId === selected.ruleId} onClick={() => act(api.approveRule, selected.ruleId)}>
                      <CheckCircle size={12} /> Approve &amp; Activate
                    </button>
                  )}
                  {selected.status === 'ACTIVE' && (
                    <button className="btn btn-danger btn-sm" disabled={busyId === selected.ruleId} onClick={() => act(api.disableRule, selected.ruleId)}>
                      <XCircle size={12} /> Disable Rule
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Create / edit form drawer */}
      {editing && (
        <>
          <div className="drawer-overlay" onClick={() => !saving && setEditing(null)} />
          <div className="drawer">
            <div className="drawer-header">
              <div className="drawer-title">{editing.ruleId ? 'Edit Rule' : 'New Rule'}</div>
              <button className="btn btn-ghost btn-icon" onClick={() => setEditing(null)}><X size={16} /></button>
            </div>
            <div className="drawer-body">
              {error && <div className="error-banner mb-16"><AlertTriangle size={14} />{error}</div>}

              <div className="form-group mb-12">
                <label className="form-label">Rule Code</label>
                <input className="input" value={editing.form.ruleCode}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, ruleCode: e.target.value } }))}
                  placeholder="e.g. CASE_NOTE_RULE" />
              </div>

              <div className="form-row mb-12">
                <div className="form-group">
                  <label className="form-label">Trigger Event</label>
                  <input className="input" value={editing.form.triggerEvent}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, triggerEvent: e.target.value } }))}
                    placeholder="e.g. CASE_NOTE_ADDED" />
                </div>
                <div className="form-group">
                  <label className="form-label">Trigger Source</label>
                  <input className="input" value={editing.form.triggerSource}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, triggerSource: e.target.value } }))}
                    placeholder="e.g. ALARM_CLOSURE" />
                </div>
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Template</label>
                <select className="select" value={editing.form.templateCode}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, templateCode: e.target.value } }))}>
                  <option value="">— choose a template —</option>
                  {(options?.templates || []).map(t => (
                    <option key={t.templateCode} value={t.templateCode}>{t.templateCode} ({t.channel}, {t.status})</option>
                  ))}
                </select>
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Recipients</label>
                <div className="tabs mb-8">
                  <div className={`tab ${editing.form.recipientMode === 'group' ? 'active' : ''}`}
                    onClick={() => setEditing(s => ({ ...s, form: { ...s.form, recipientMode: 'group' } }))}>
                    Existing Group
                  </div>
                  <div className={`tab ${editing.form.recipientMode === 'custom' ? 'active' : ''}`}
                    onClick={() => setEditing(s => ({ ...s, form: { ...s.form, recipientMode: 'custom' } }))}>
                    Enter Emails
                  </div>
                </div>

                {editing.form.recipientMode === 'group' ? (
                  <select className="select" value={editing.form.recipientGroupCode}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, recipientGroupCode: e.target.value } }))}>
                    <option value="">— choose a recipient group —</option>
                    {(options?.recipientGroups || []).map(g => (
                      <option key={g.groupCode} value={g.groupCode}>{g.groupCode}{g.description ? ` — ${g.description}` : ''}</option>
                    ))}
                  </select>
                ) : (
                  <>
                    <input className="input mb-8" value={editing.form.toEmails}
                      onChange={e => setEditing(s => ({ ...s, form: { ...s.form, toEmails: e.target.value } }))}
                      placeholder="To — comma-separated email addresses" />
                    <input className="input" value={editing.form.ccEmails}
                      onChange={e => setEditing(s => ({ ...s, form: { ...s.form, ccEmails: e.target.value } }))}
                      placeholder="CC (optional) — comma-separated email addresses" />
                    <div className="fs-11 text-muted mt-4">This creates a dedicated recipient list for this rule.</div>
                  </>
                )}
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Escalation Chain (optional)</label>
                <select className="select" value={editing.form.escalationChainCode}
                  onChange={e => setEditing(s => ({ ...s, form: { ...s.form, escalationChainCode: e.target.value } }))}>
                  <option value="">— none —</option>
                  {(options?.escalationChains || []).map(c => (
                    <option key={c.chainCode} value={c.chainCode}>{c.chainCode}{c.description ? ` — ${c.description}` : ''}</option>
                  ))}
                </select>
              </div>

              <div className="form-group mb-12">
                <label className="form-label">Attachment</label>
                <div className="tabs mb-8">
                  {ATTACHMENT_TYPES.map(t => (
                    <div key={t.value} className={`tab ${editing.form.attachmentType === t.value ? 'active' : ''}`}
                      onClick={() => setEditing(s => ({ ...s, form: { ...s.form, attachmentType: t.value } }))}>
                      {t.label}
                    </div>
                  ))}
                </div>

                {editing.form.attachmentType === 'PR_RECORD' && (
                  <>
                    <input className="input" value={editing.form.attachmentReportIdentifier}
                      onChange={e => setEditing(s => ({ ...s, form: { ...s.form, attachmentReportIdentifier: e.target.value } }))}
                      placeholder="Report identifier — e.g. PR_CLOSURE_REPORT" />
                    <div className="fs-11 text-muted mt-4">Fetched from the report service and attached as a PDF at send time.</div>
                  </>
                )}

                {editing.form.attachmentType === 'UPLOAD' && (
                  <>
                    <label className="btn btn-secondary btn-sm" style={{ cursor: 'pointer', width: 'fit-content' }}>
                      <UploadCloud size={13} /> {uploadingAttachment ? 'Uploading…' : 'Choose file (PDF or image)'}
                      <input type="file" accept=".pdf,image/*" style={{ display: 'none' }} disabled={uploadingAttachment}
                        onChange={e => uploadAttachmentFile(e.target.files?.[0])} />
                    </label>
                    {editing.form.attachmentFileName && (
                      <div className="fs-12 mt-8" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                        <Paperclip size={12} color="var(--green)" />
                        {editing.form.attachmentFileName}
                        <span className="badge badge-active">{editing.form.attachmentOutputFormat}</span>
                      </div>
                    )}
                    <div className="fs-11 text-muted mt-4">This exact file is attached to every send of this rule.</div>
                  </>
                )}

                {editing.form.attachmentType !== 'NONE' && (
                  <div className="form-group mt-8">
                    <label className="form-label">If attachment generation fails</label>
                    <select className="select" value={editing.form.attachmentOnFailure}
                      onChange={e => setEditing(s => ({ ...s, form: { ...s.form, attachmentOnFailure: e.target.value } }))}>
                      <option value="SEND_WITHOUT_ATTACHMENT">Send the email without the attachment</option>
                      <option value="HOLD_JOB">Hold the job for manual review</option>
                      <option value="FAIL_JOB">Fail the job</option>
                    </select>
                  </div>
                )}
              </div>

              <div className="form-row mb-12">
                <div className="form-group">
                  <label className="form-label">Max Retry Count</label>
                  <input className="input" type="number" min="1" value={editing.form.maxRetryCount}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, maxRetryCount: e.target.value } }))} />
                </div>
                <div className="form-group">
                  <label className="form-label">Retry Backoff (seconds)</label>
                  <input className="input" type="number" min="1" value={editing.form.retryBackoffSeconds}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, retryBackoffSeconds: e.target.value } }))} />
                </div>
              </div>

              <div className="form-row mb-12">
                <div className="form-group">
                  <label className="form-label">Backoff Multiplier</label>
                  <input className="input" type="number" step="0.1" min="1" value={editing.form.retryBackoffMultiplier}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, retryBackoffMultiplier: e.target.value } }))} />
                </div>
                <div className="form-group">
                  <label className="form-label">On Final Failure</label>
                  <select className="select" value={editing.form.onFinalFailure}
                    onChange={e => setEditing(s => ({ ...s, form: { ...s.form, onFinalFailure: e.target.value } }))}>
                    <option value="ESCALATE">ESCALATE</option>
                    <option value="HOLD_FOR_MANUAL">HOLD_FOR_MANUAL</option>
                    <option value="DROP">DROP</option>
                  </select>
                </div>
              </div>

              {!editing.ruleId && (
                <div className="fs-12 text-muted mb-16">New rules start as DRAFT — submit for review and get a different operator to approve before it goes live.</div>
              )}

              <button className="btn btn-primary" onClick={save}
                disabled={saving || !editing.form.ruleCode || !editing.form.triggerEvent || !editing.form.templateCode ||
                  (editing.form.recipientMode === 'group' ? !editing.form.recipientGroupCode : !splitEmails(editing.form.toEmails).length) ||
                  (editing.form.attachmentType !== 'NONE' && !editing.form.attachmentReportIdentifier) ||
                  uploadingAttachment}>
                {saving ? 'Saving…' : <><CheckCircle size={13} /> {editing.ruleId ? 'Save Changes' : 'Create Rule'}</>}
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
