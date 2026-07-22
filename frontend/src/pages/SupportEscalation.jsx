import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Shield, Mail, Hash, Webhook, CheckCircle } from 'lucide-react';

function ChannelIcon({ ch }) {
  if (ch === 'SLACK') return <Hash size={13} color="var(--emerald)" />;
  if (ch === 'WEBHOOK') return <Webhook size={13} color="var(--orange)" />;
  return <Mail size={13} color="var(--indigo)" />;
}

export default function SupportEscalation() {
  const [config, setConfig] = useState(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(null);

  useEffect(() => {
    if (api.escalationConfig) {
      api.escalationConfig().then(c => { setConfig(c); setForm(c); });
    }
  }, []);

  const setDelay = (i, value) => setForm(f => ({
    ...f,
    chain: f.chain.map((step, idx) => idx === i ? { ...step, delayMinutes: Number(value) } : step)
  }));

  const handleSave = async e => {
    e.preventDefault();
    setSaved(false);
    setSaving(true);
    try {
      const payload = { chain: form.chain.map(s => ({ order: s.order, recipient: s.recipient, delayMinutes: s.delayMinutes })) };
      const updated = await api.updateEscalationConfig(payload);
      setConfig(updated);
      setForm(updated);
      setSaved(true);
    } finally {
      setSaving(false);
    }
  };

  if (!config) return <div className="text-muted">Loading…</div>;

  return (
    <div style={{ maxWidth: 680 }}>
      {saved && <div className="success-banner mb-16"><CheckCircle size={14} /> Escalation configuration saved.</div>}

      <form onSubmit={handleSave}>
        <div className="card mb-16">
          <div className="card-title mb-16"><Shield size={14} /> Escalation Chain</div>
          <div className="fs-12 text-muted mb-16">
            When a notification fails beyond its retry limit (and <code style={{ fontFamily: 'monospace', fontSize: 11 }}>onFinalFailure=ESCALATE</code>), the escalation chain fires in order.
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            {(form?.chain || []).length === 0 && (
              <div className="text-muted fs-12">No escalation steps configured yet.</div>
            )}
            {(form?.chain || []).map((step, i) => (
              <div key={i} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '14px 16px', background: 'var(--surface)', border: '1px solid var(--border)', borderRadius: 10 }}>
                <div style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--indigo-dim)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <span className="fw-700 fs-12" style={{ color: 'var(--indigo)' }}>{step.order}</span>
                </div>
                <div style={{ flex: 1, display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                    <ChannelIcon ch={step.channel} />
                    <span className={`badge badge-${step.channel.toLowerCase()}`}>{step.channel}</span>
                  </div>
                  <span className="fs-12 text-secondary">{step.recipient}</span>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <input className="input" type="number" min="0" style={{ width: 70 }} value={step.delayMinutes}
                    onChange={e => setDelay(i, e.target.value)} />
                  <span className="fs-11 text-muted">min delay</span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <button type="submit" className="btn btn-primary" disabled={saving || (form?.chain || []).length === 0}>
          {saving ? 'Saving…' : 'Save Escalation Config'}
        </button>
      </form>
    </div>
  );
}
