import React from 'react';
import { useParams, Link } from 'react-router-dom';
import { Mail, CheckCircle } from 'lucide-react';

const CHANNEL_INFO = {
  email: {
    icon: Mail, color: 'var(--indigo)', label: 'Email (SMTP)',
    status: 'LIVE',
    description: 'Sends HTML emails via JavaMailSender over SMTP. Supports attachments (PDF reports), CC lists, and full Thymeleaf template rendering.',
    config: [
      ['Implementation', 'EmailChannelSender.java'],
      ['Transport', 'SMTP (JavaMailSender)'],
      ['Attachment support', 'Yes — PDF via AttachmentService'],
      ['CC support', 'Yes'],
      ['Milestone', 'M1 — Live'],
    ],
    configLink: '/channels/email',
    configPage: '/smtp'
  },
};

export default function ChannelPage() {
  const { channel } = useParams();
  const info = CHANNEL_INFO[channel] || CHANNEL_INFO.email;
  const Icon = info.icon;

  return (
    <div style={{ maxWidth: 620 }}>
      <div className="card mb-16">
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16, marginBottom: 16 }}>
          <div className="kpi-icon" style={{ background: `${info.color}18`, width: 48, height: 48, borderRadius: 12 }}>
            <Icon size={22} color={info.color} />
          </div>
          <div style={{ flex: 1 }}>
            <div className="fw-700 fs-13">{info.label}</div>
            <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
              <span className="badge badge-active"><CheckCircle size={10} /> LIVE</span>
            </div>
          </div>
        </div>
        <p className="fs-13 text-secondary" style={{ lineHeight: 1.6 }}>{info.description}</p>
      </div>

      <div className="card mb-16">
        <div className="card-title mb-12">Technical Details</div>
        {info.config.map(([k, v]) => (
          <div key={k} className="config-row">
            <span className="config-key">{k}</span>
            <span className="config-val monospace">{v}</span>
          </div>
        ))}
      </div>

      {channel === 'email' && (
        <div className="card" style={{ background: 'var(--blue-dim)', border: '1px solid rgba(59,130,246,0.2)' }}>
          <div className="fw-600 fs-13 mb-8">SMTP Configuration</div>
          <p className="fs-12 text-secondary mb-12">Configure the SMTP server, port, credentials, and sender identity for outbound email.</p>
          <Link to="/channels/email" className="btn btn-primary btn-sm">Open SMTP Settings</Link>
        </div>
      )}
    </div>
  );
}
