import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';

export default function RulesTemplates() {
  const [rules, setRules] = useState(null);
  const [templates, setTemplates] = useState(null);
  const [error, setError] = useState(null);
  const [busyId, setBusyId] = useState(null);

  const load = () => {
    Promise.all([api.listRules(), api.listTemplates()])
      .then(([r, t]) => { setRules(r); setTemplates(t); })
      .catch(e => setError(e.response?.data?.message || 'Could not load rules/templates'));
  };

  useEffect(load, []);

  const act = async (fn, ruleId) => {
    setBusyId(ruleId);
    try { await fn(ruleId); load(); } finally { setBusyId(null); }
  };

  return (
    <div className="page">
      {error && <div className="error-banner">{error}</div>}

      <div className="section-title" style={{ marginTop: 0 }}>Notification rules</div>
      {rules === null ? (
        <div className="empty-state">Loading…</div>
      ) : rules.length === 0 ? (
        <div className="empty-state">No rules configured yet for this tenant.</div>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Rule code</th><th>Trigger event</th><th>Template</th><th>Retry policy</th>
              <th>On final failure</th><th>Status</th><th>Active</th><th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {rules.map(r => (
              <tr key={r.ruleId}>
                <td>{r.ruleCode}</td>
                <td>{r.triggerEvent}</td>
                <td>{r.template?.templateCode}</td>
                <td>{r.maxRetryCount}x, {r.retryBackoffSeconds}s backoff</td>
                <td>{r.onFinalFailure}</td>
                <td><span className={`badge badge-${r.status.toLowerCase()}`}>{r.status}</span></td>
                <td><span className={`badge ${r.active ? 'badge-yes' : 'badge-draft'}`}>{r.active ? 'YES' : 'NO'}</span></td>
                <td>
                  {r.status === 'DRAFT' && (
                    <button className="secondary" disabled={busyId === r.ruleId} onClick={() => act(api.submitRuleForReview, r.ruleId)}>Submit for review</button>
                  )}
                  {r.status === 'PENDING_REVIEW' && (
                    <button className="secondary" disabled={busyId === r.ruleId} onClick={() => act(api.approveRule, r.ruleId)}>Approve & activate</button>
                  )}
                  {r.status === 'ACTIVE' && (
                    <button className="secondary" disabled={busyId === r.ruleId} onClick={() => act(api.disableRule, r.ruleId)}>Disable</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <div className="section-title">Templates</div>
      {templates === null ? (
        <div className="empty-state">Loading…</div>
      ) : templates.length === 0 ? (
        <div className="empty-state">No templates configured yet.</div>
      ) : (
        <table>
          <thead>
            <tr><th>Template code</th><th>Channel</th><th>Subject</th><th>Allowed variables</th><th>PII masked</th><th>Status</th></tr>
          </thead>
          <tbody>
            {templates.map(t => (
              <tr key={t.templateId}>
                <td>{t.templateCode} <span className="muted">v{t.version}</span></td>
                <td><span className={`badge badge-channel-${(t.channel || 'EMAIL').toLowerCase()}`}>{t.channel || 'EMAIL'}</span></td>
                <td>{t.subjectTemplate}</td>
                <td className="muted">{(t.allowedVariables || []).join(', ')}</td>
                <td className="muted">{(t.piiMaskFields || []).join(', ') || '—'}</td>
                <td><span className={`badge badge-${t.status.toLowerCase()}`}>{t.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
