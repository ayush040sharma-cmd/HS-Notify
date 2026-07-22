import React, { useState, useEffect } from 'react';
import { api } from '../api/client.js';
import { Send, CheckCircle, AlertTriangle, Paperclip, X } from 'lucide-react';

export default function SendNotification() {
  return (
    <div className="grid grid-2" style={{ gap: 20 }}>
      <SendViaRule />
      <SendDirect />
    </div>
  );
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
