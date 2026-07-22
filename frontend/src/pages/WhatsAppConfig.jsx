import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { MessageCircle, KeyRound, CheckCircle, RefreshCw } from 'lucide-react';

export default function WhatsAppConfig() {
  const [config, setConfig] = useState(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(null);
  const [apiKeyInput, setApiKeyInput] = useState('');

  useEffect(() => {
    if (api.whatsappConfig) {
      api.whatsappConfig().then(c => { setConfig(c); setForm({ ...c }); });
    }
  }, []);

  const handleChange = e => setForm(f => ({ ...f, [e.target.name]: e.target.value }));
  const handleSave = async e => {
    e.preventDefault();
    setSaved(false);
    setSaving(true);
    try {
      const payload = { ...form, apiKey: apiKeyInput || null };
      const updated = await api.updateWhatsAppConfig(payload);
      setConfig(updated);
      setForm({ ...updated });
      setApiKeyInput('');
      setSaved(true);
    } finally {
      setSaving(false);
    }
  };

  if (!config) return <div className="text-muted">Loading…</div>;

  return (
    <div style={{ maxWidth: 680 }}>
      {saved && (
        <div className="success-banner mb-16">
          <CheckCircle size={14} /> WhatsApp configuration saved successfully.
        </div>
      )}

      <form onSubmit={handleSave}>
        <div className="card mb-16">
          <div className="card-title mb-16"><MessageCircle size={14} /> WhatsApp Business API</div>
          <div className="form-row mb-12">
            <div className="form-group">
              <label className="form-label">Business Account ID</label>
              <input className="input" name="businessAccountId" value={form.businessAccountId || ''} onChange={handleChange} placeholder="e.g. 1029384756" />
            </div>
            <div className="form-group">
              <label className="form-label">Phone Number ID</label>
              <input className="input" name="phoneNumberId" value={form.phoneNumberId || ''} onChange={handleChange} placeholder="e.g. 1122334455" />
            </div>
          </div>
          <div className="form-group mb-12">
            <label className="form-label">Webhook URL</label>
            <input className="input" name="webhookUrl" value={form.webhookUrl || ''} onChange={handleChange} placeholder="https://your-domain.example.com/webhooks/whatsapp" />
          </div>
        </div>

        <div className="card mb-16">
          <div className="card-title mb-16"><KeyRound size={14} /> API Credentials</div>
          <div className="form-group mb-12">
            <label className="form-label">API Key</label>
            <input
              className="input"
              type="password"
              value={apiKeyInput}
              onChange={e => setApiKeyInput(e.target.value)}
              placeholder={config.apiKeyConfigured ? '••••••••  (leave blank to keep the saved key)' : 'Not yet configured'}
            />
            <div className="fs-12 text-secondary mt-4">
              {config.apiKeyConfigured
                ? 'A key is already saved — enter a new value only to replace it.'
                : 'No key saved yet.'}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10 }}>
          <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save Configuration'}</button>
          <button type="button" className="btn btn-secondary" onClick={() => { setForm({ ...config }); setApiKeyInput(''); }}>
            <RefreshCw size={13} /> Reset
          </button>
        </div>
      </form>

      <div className="card mt-16" style={{ background: 'var(--surface)' }}>
        <div className="card-title mb-8"><MessageCircle size={13} /> Current Effective Config</div>
        {[
          ['Business Account ID', config.businessAccountId || '—'],
          ['Phone Number ID', config.phoneNumberId || '—'],
          ['Webhook URL', config.webhookUrl || '—'],
          ['API Key', config.apiKeyConfigured ? 'Configured' : 'Not configured'],
        ].map(([k, v]) => (
          <div key={k} className="config-row">
            <span className="config-key">{k}</span>
            <span className="config-val">{String(v)}</span>
          </div>
        ))}
        <p className="fs-12 text-secondary mt-12">
          Sending is still stubbed (<code>WhatsAppChannelSender</code>) — these values are saved for when the
          real WhatsApp Business API integration is wired up.
        </p>
      </div>
    </div>
  );
}
