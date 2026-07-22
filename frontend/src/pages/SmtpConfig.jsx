import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Mail, Shield, CheckCircle, RefreshCw } from 'lucide-react';

export default function SmtpConfig() {
  const [config, setConfig] = useState(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState(null);

  useEffect(() => {
    if (api.smtpConfig) {
      api.smtpConfig().then(c => { setConfig(c); setForm({ ...c }); });
    }
  }, []);

  const handleChange = e => setForm(f => ({ ...f, [e.target.name]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));
  const handleSave = async e => {
    e.preventDefault();
    setSaved(false);
    setSaving(true);
    try {
      const updated = await api.updateSmtpConfig(form);
      setConfig(updated);
      setForm({ ...updated });
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
          <CheckCircle size={14} /> SMTP configuration saved successfully.
        </div>
      )}

      <form onSubmit={handleSave}>
        <div className="card mb-16">
          <div className="card-title mb-16"><Mail size={14} /> Server Settings</div>
          <div className="form-row mb-12">
            <div className="form-group">
              <label className="form-label">SMTP Host</label>
              <input className="input" name="host" value={form.host} onChange={handleChange} placeholder="smtp.example.com" />
            </div>
            <div className="form-group">
              <label className="form-label">Port</label>
              <input className="input" name="port" type="number" value={form.port} onChange={handleChange} placeholder="587" />
            </div>
          </div>
          <div className="form-row mb-12">
            <div className="form-group">
              <label className="form-label">Username</label>
              <input className="input" name="username" value={form.username || ''} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label className="form-label">Max Emails / Minute</label>
              <input className="input" name="maxPerMinute" type="number" value={form.maxPerMinute} onChange={handleChange} />
            </div>
          </div>
          <label style={{ display: 'flex', alignItems: 'center', gap: 8, cursor: 'pointer', fontSize: 13 }}>
            <label className="toggle"><input type="checkbox" name="useTls" checked={form.useTls} onChange={handleChange} /><span className="toggle-slider" /></label>
            Use TLS (STARTTLS)
          </label>
        </div>

        <div className="card mb-16">
          <div className="card-title mb-16"><Shield size={14} /> Sender Identity</div>
          <div className="form-row mb-12">
            <div className="form-group">
              <label className="form-label">From Name</label>
              <input className="input" name="fromName" value={form.fromName || ''} onChange={handleChange} />
            </div>
            <div className="form-group">
              <label className="form-label">From Email</label>
              <input className="input" name="fromEmail" type="email" value={form.fromEmail || ''} onChange={handleChange} />
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: 10 }}>
          <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save Configuration'}</button>
          <button type="button" className="btn btn-secondary" onClick={() => setForm({ ...config })}>
            <RefreshCw size={13} /> Reset
          </button>
        </div>
      </form>

      <div className="card mt-16" style={{ background: 'var(--surface)' }}>
        <div className="card-title mb-8"><Mail size={13} /> Current Effective Config</div>
        {[['Host', config.host], ['Port', config.port], ['TLS', config.useTls ? 'Yes' : 'No'], ['From', `${config.fromName} <${config.fromEmail}>`], ['Max/min', config.maxPerMinute]].map(([k, v]) => (
          <div key={k} className="config-row">
            <span className="config-key">{k}</span>
            <span className="config-val">{String(v)}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
