import React, { useState, useEffect } from 'react';
import { api } from '../api/client.js';
import { Send, CheckCircle, AlertTriangle, Paperclip, X, Zap } from 'lucide-react';

export default function SendNotification() {
  return (
    <div>
      <div style={{ marginBottom: 20 }}>
        <SendViaAction />
      </div>
      <div className="grid grid-2" style={{ gap: 20 }}>
        <SendViaRule />
        <SendDirect />
      </div>
    </div>
  );
}

/**
 * The dynamic notification wizard: pick an action from the Notification
 * Action Registry, render its form_schema (if any) field-by-field, and
 * submit through the unified POST /api/v1/notify engine. No hardcoded
 * per-action form — DynamicField below renders purely off field metadata.
 */
function SendViaAction() {
  const [actions, setActions] = useState([]);
  const [actionCode, setActionCode] = useState('');
  const [schema, setSchema] = useState(null);
  const [values, setValues] = useState({});
  const [recipientGroups, setRecipientGroups] = useState([]);
  const [result, setResult] = useState(null);
  const [notices, setNotices] = useState([]);
  const [error, setError] = useState(null);
  const [sending, setSending] = useState(false);
  const [loadingSchema, setLoadingSchema] = useState(false);

  useEffect(() => {
    api.listActions().then(list => {
      const enabled = list.filter(a => a.enabled);
      setActions(enabled);
      if (enabled.length > 0) setActionCode(enabled[0].code);
    }).catch(() => setActions([]));
    api.ruleFormOptions().then(o => setRecipientGroups(o.recipientGroups || [])).catch(() => {});
  }, []);

  useEffect(() => {
    if (!actionCode) { setSchema(null); return; }
    setLoadingSchema(true);
    setValues({});
    api.actionSchema(actionCode).then(res => {
      setSchema(res.schema);
      const initial = {};
      (res.schema?.fields || []).forEach(f => { if (f.defaultValue) initial[f.fieldKey] = f.defaultValue; });
      setValues(initial);
    }).catch(() => setSchema(null)).finally(() => setLoadingSchema(false));
  }, [actionCode]);

  const setValue = (key, val) => setValues(v => ({ ...v, [key]: val }));

  const isVisible = (field) => {
    if (!field.conditionalOnFieldKey) return true;
    return String(values[field.conditionalOnFieldKey] ?? '') === String(field.conditionalOnValue ?? '');
  };

  const submit = async () => {
    setError(null); setResult(null); setNotices([]);

    const toRaw = values['to_address'] ?? '';
    const to = String(toRaw).split(',').map(s => s.trim()).filter(Boolean);
    if (to.length === 0) { setError('At least one To address is required'); return; }
    const cc = String(values['cc_address'] ?? '').split(',').map(s => s.trim()).filter(Boolean);
    const bcc = String(values['bcc_address'] ?? '').split(',').map(s => s.trim()).filter(Boolean);

    // Everything except the well-known recipient/flag/subject keys becomes
    // template/context payload — this is what lets the wizard stay generic
    // across any action's schema without per-action frontend code.
    const reserved = new Set(['to_address', 'cc_address', 'bcc_address', 'subject',
      'include_case_link', 'include_pr_records', 'include_attachment']);
    const payload = {};
    Object.entries(values).forEach(([k, v]) => {
      if (!reserved.has(k) && v !== '' && v !== undefined && v !== null) payload[k] = v;
    });

    setSending(true);
    try {
      const request = {
        action: actionCode,
        recipients: { to, cc, bcc },
        subject: values.subject || undefined,
        comment: !schema ? (values.comment || undefined) : undefined,
        payload,
        attachmentOptions: {
          includeCaseLink: !!values.include_case_link,
          includePrRecords: !!values.include_pr_records,
          includeAttachment: !!values.include_attachment,
        },
      };
      const res = await api.notify(request);
      setResult(res.job);
      setNotices(res.notices || []);
    } catch (e) {
      setError(e?.response?.data?.error || e?.response?.data?.message || 'Send failed');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="card">
      <div className="card-title mb-16"><Zap size={14} /> Notification Wizard</div>
      <ResultBanner result={result} error={error} />
      {notices.length > 0 && (
        <div className="fs-12 text-muted mb-12">{notices.join(' ')}</div>
      )}

      <div className="form-group mb-16">
        <label className="form-label">Notification Action</label>
        {actions.length === 0 ? (
          <div className="fs-12 text-muted">No enabled actions — create one on the Notification Actions page.</div>
        ) : (
          <select className="select" value={actionCode} onChange={e => setActionCode(e.target.value)}>
            {actions.map(a => <option key={a.code} value={a.code}>{a.displayName} ({a.code})</option>)}
          </select>
        )}
      </div>

      {loadingSchema && <div className="fs-12 text-muted">Loading form…</div>}

      {!loadingSchema && !schema && actionCode && (
        <div className="fs-12 text-muted mb-12">
          No dynamic form defined for this action yet — using a generic To/Subject/Comment form.
        </div>
      )}

      {!loadingSchema && !schema && actionCode && (
        <>
          <div className="form-group mb-12">
            <label className="form-label">To</label>
            <input className="input" value={values.to_address || ''} onChange={e => setValue('to_address', e.target.value)} placeholder="Comma-separated email addresses" />
          </div>
          <div className="form-group mb-12">
            <label className="form-label">Subject</label>
            <input className="input" value={values.subject || ''} onChange={e => setValue('subject', e.target.value)} />
          </div>
          <div className="form-group mb-16">
            <label className="form-label">Comment</label>
            <textarea className="textarea" rows={3} value={values.comment || ''} onChange={e => setValue('comment', e.target.value)} />
          </div>
        </>
      )}

      {!loadingSchema && schema && (
        <div className="mb-16">
          {schema.fields.filter(isVisible).map(field => (
            <DynamicField key={field.fieldKey} field={field} value={values[field.fieldKey]}
              onChange={v => setValue(field.fieldKey, v)} recipientGroups={recipientGroups} />
          ))}
        </div>
      )}

      <button className="btn btn-primary" onClick={submit} disabled={sending || !actionCode}>
        {sending ? 'Sending…' : <><Send size={13} /> Send</>}
      </button>
    </div>
  );
}

/** Renders one field purely from its metadata — no per-action/per-field-key branching. */
function DynamicField({ field, value, onChange, recipientGroups }) {
  const label = <label className="form-label">{field.label}{field.required ? ' *' : ''}</label>;

  switch (field.fieldType) {
    case 'TEXTAREA':
      return (
        <div className="form-group mb-12">
          {label}
          <textarea className="textarea" rows={3} value={value || ''} placeholder={field.placeholder || ''}
            onChange={e => onChange(e.target.value)} />
          {field.helpText && <div className="fs-11 text-muted mt-4">{field.helpText}</div>}
        </div>
      );
    case 'DROPDOWN':
      return (
        <div className="form-group mb-12">
          {label}
          <select className="select" value={value || ''} onChange={e => onChange(e.target.value)}>
            <option value="">— select —</option>
            {(field.options || []).map(o => <option key={o.optionValue} value={o.optionValue}>{o.optionLabel}</option>)}
          </select>
        </div>
      );
    case 'RADIO':
      return (
        <div className="form-group mb-12">
          {label}
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {(field.options || []).map(o => (
              <label key={o.optionValue} style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13, cursor: 'pointer' }}>
                <input type="radio" name={field.fieldKey} checked={value === o.optionValue} onChange={() => onChange(o.optionValue)} />
                {o.optionLabel}
              </label>
            ))}
          </div>
        </div>
      );
    case 'CHECKBOX':
      return (
        <div className="form-group mb-12">
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
            <span className="toggle">
              <input type="checkbox" checked={!!value} onChange={e => onChange(e.target.checked)} />
              <span className="toggle-slider" />
            </span>
            {field.label}
          </label>
          {field.helpText && <div className="fs-11 text-muted mt-4">{field.helpText}</div>}
        </div>
      );
    case 'DATE':
      return (
        <div className="form-group mb-12">
          {label}
          <input className="input" type="date" value={value || ''} onChange={e => onChange(e.target.value)} />
        </div>
      );
    case 'EMAIL':
      return (
        <div className="form-group mb-12">
          {label}
          <input className="input" type="email" value={value || ''} placeholder={field.placeholder || ''} onChange={e => onChange(e.target.value)} />
        </div>
      );
    case 'FILE_UPLOAD':
      return (
        <div className="form-group mb-12">
          {label}
          <input type="file" onChange={async e => {
            const file = e.target.files?.[0];
            if (!file) return;
            const uploaded = await api.uploadAttachment(file);
            onChange(uploaded.path);
          }} />
          {value && <div className="fs-11 text-muted mt-4">Uploaded: {value}</div>}
        </div>
      );
    case 'DYNAMIC_LOOKUP':
      if (field.lookupSource === 'recipient-groups') {
        return (
          <div className="form-group mb-12">
            {label}
            <select className="select" value={value || ''} onChange={e => onChange(e.target.value)}>
              <option value="">— select —</option>
              {recipientGroups.map(g => <option key={g.groupCode} value={g.groupCode}>{g.groupCode}</option>)}
            </select>
          </div>
        );
      }
      return (
        <div className="form-group mb-12">
          {label}
          <input className="input" value={value || ''} placeholder={`(dynamic lookup: ${field.lookupSource || 'unconfigured'})`} onChange={e => onChange(e.target.value)} />
        </div>
      );
    default: // TEXTBOX and anything unrecognized
      return (
        <div className="form-group mb-12">
          {label}
          <input className="input" value={value || ''} placeholder={field.placeholder || ''} onChange={e => onChange(e.target.value)} />
          {field.helpText && <div className="fs-11 text-muted mt-4">{field.helpText}</div>}
        </div>
      );
  }
}

function ResultBanner({ result, error }) {
  if (error) return (
    <div className="error-banner mb-12"><AlertTriangle size={14} />{error}</div>
  );
  if (result) return (
    <div className="success-banner mb-12">
      <CheckCircle size={14} />
      Job #{result.jobId} created — status: <strong>{result.status}</strong>
      {result.channel && <span className={`badge badge-${result.channel.toLowerCase()}`} style={{ marginLeft: 8 }}>{result.channel}</span>}
    </div>
  );
  return null;
}

function SendViaRule() {
  const [ruleCode, setRuleCode] = useState('PR_CLOSE_RULE');
  const [recipientOverride, setRecipientOverride] = useState('');
  const [contextJson, setContextJson] = useState('{"pr_id":"1234","account_name":"Acme Corp","closed_by":"analyst.jdoe","close_date":"2026-06-26"}');
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [sending, setSending] = useState(false);

  const submit = async () => {
    setError(null); setResult(null);
    let context;
    try { context = JSON.parse(contextJson); }
    catch { setError('Context must be valid JSON'); return; }
    setSending(true);
    try {
      const job = await api.sendByRule({
        ruleCode, idempotencyKey: `ui-${ruleCode}-${Date.now()}`,
        sourceReference: 'MANUAL_TEST', sourceType: 'MANUAL',
        context, recipientOverride: recipientOverride || null
      });
      setResult(job);
    } catch (e) {
      setError(e?.response?.data?.message || 'Send failed');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="card">
      <div className="card-title mb-16"><Send size={14} /> Send via Rule</div>
      <ResultBanner result={result} error={error} />
      <div className="form-group mb-12">
        <label className="form-label">Rule Code</label>
        <input className="input" value={ruleCode} onChange={e => setRuleCode(e.target.value)} placeholder="e.g. PR_CLOSE_RULE" />
      </div>
      <div className="form-group mb-12">
        <label className="form-label">Recipient Override (optional)</label>
        <input className="input" value={recipientOverride} onChange={e => setRecipientOverride(e.target.value)} placeholder="Override — leave blank to use rule's recipient group" />
      </div>
      <div className="form-group mb-16">
        <label className="form-label">Context JSON</label>
        <textarea className="textarea" rows={5} value={contextJson} onChange={e => setContextJson(e.target.value)} />
      </div>
      <button className="btn btn-primary" onClick={submit} disabled={sending || !ruleCode}>
        {sending ? 'Sending…' : <><Send size={13} /> Send via Rule</>}
      </button>
    </div>
  );
}

function SendDirect() {
  const [to, setTo] = useState('');
  const [cc, setCc] = useState('');
  const [mode, setMode] = useState('custom'); // 'custom' | 'template'
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [templates, setTemplates] = useState([]);
  const [templateCode, setTemplateCode] = useState('');
  const [variables, setVariables] = useState({});
  const [file, setFile] = useState(null);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    api.listTemplates().then(list => {
      const usable = list.filter(t => t.channel === 'EMAIL' && t.status === 'ACTIVE');
      setTemplates(usable);
      if (usable.length > 0) setTemplateCode(usable[0].templateCode);
    });
  }, []);

  const selectedTemplate = templates.find(t => t.templateCode === templateCode);

  const submit = async () => {
    setError(null); setResult(null);

    if (!to) { setError('To is required'); return; }
    if (mode === 'custom' && (!subject || !body)) { setError('Subject and body are required'); return; }
    if (mode === 'template' && !templateCode) { setError('Choose a template'); return; }

    setSending(true);
    try {
      let attachmentPath = null;
      if (file) {
        const uploaded = await api.uploadAttachment(file);
        attachmentPath = uploaded.path;
      }

      const payload = {
        to: to.split(',').map(s => s.trim()).filter(Boolean),
        cc: cc ? cc.split(',').map(s => s.trim()).filter(Boolean) : [],
        attachmentPath,
      };
      if (mode === 'template') {
        payload.templateCode = templateCode;
        payload.context = variables;
      } else {
        payload.subject = subject;
        payload.htmlBody = body;
      }

      const job = await api.sendDirect(payload);
      setResult(job);
    } catch (e) {
      setError(e?.response?.data?.message || 'Send failed');
    } finally {
      setSending(false);
    }
  };

  return (
    <div className="card">
      <div className="card-title mb-16"><Send size={14} /> Send Direct Email</div>
      <ResultBanner result={result} error={error} />

      <div className="form-group mb-12">
        <label className="form-label">To</label>
        <input className="input" value={to} onChange={e => setTo(e.target.value)} placeholder="Comma-separated email addresses" />
      </div>
      <div className="form-group mb-16">
        <label className="form-label">CC (optional)</label>
        <input className="input" value={cc} onChange={e => setCc(e.target.value)} placeholder="Comma-separated" />
      </div>

      <div className="tabs mb-16">
        <div className={`tab ${mode === 'custom' ? 'active' : ''}`} onClick={() => setMode('custom')}>Custom</div>
        <div className={`tab ${mode === 'template' ? 'active' : ''}`} onClick={() => setMode('template')}>From Template</div>
      </div>

      {mode === 'custom' ? (
        <>
          <div className="form-group mb-12">
            <label className="form-label">Subject</label>
            <input className="input" value={subject} onChange={e => setSubject(e.target.value)} />
          </div>
          <div className="form-group mb-16">
            <label className="form-label">HTML Body</label>
            <textarea className="textarea" rows={5} value={body} onChange={e => setBody(e.target.value)} placeholder="<p>Your message here…</p>" />
          </div>
        </>
      ) : (
        <>
          <div className="form-group mb-12">
            <label className="form-label">Template</label>
            {templates.length === 0 ? (
              <div className="fs-12 text-muted">No active EMAIL templates yet — create one on the Templates page.</div>
            ) : (
              <select className="select" value={templateCode} onChange={e => { setTemplateCode(e.target.value); setVariables({}); }}>
                {templates.map(t => <option key={t.templateCode} value={t.templateCode}>{t.templateCode}</option>)}
              </select>
            )}
          </div>
          {selectedTemplate && (
            <div className="form-group mb-16">
              <label className="form-label">Variables</label>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(selectedTemplate.allowedVariables || []).map(v => (
                  <input
                    key={v}
                    className="input"
                    placeholder={`{{${v}}}`}
                    value={variables[v] || ''}
                    onChange={e => setVariables(vars => ({ ...vars, [v]: e.target.value }))}
                  />
                ))}
                {(selectedTemplate.allowedVariables || []).length === 0 && (
                  <div className="fs-12 text-muted">This template has no variables.</div>
                )}
              </div>
            </div>
          )}
        </>
      )}

      <div className="form-group mb-16">
        <label className="form-label">Attachment (optional)</label>
        {file ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Paperclip size={13} /> <span className="fs-12">{file.name}</span>
            <button type="button" className="btn btn-ghost btn-icon" onClick={() => setFile(null)}><X size={13} /></button>
          </div>
        ) : (
          <input type="file" onChange={e => setFile(e.target.files?.[0] || null)} />
        )}
      </div>

      <button className="btn btn-primary" onClick={submit} disabled={sending}>
        {sending ? 'Sending…' : <><Send size={13} /> Send Direct</>}
      </button>
    </div>
  );
}
