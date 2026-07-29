import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { Paperclip, CheckCircle, XCircle } from 'lucide-react';

export default function AttachmentProviders() {
  const [providers, setProviders] = useState(null);

  useEffect(() => { api.listAttachmentProviders().then(setProviders); }, []);

  return (
    <div>
      <div className="fs-12 text-muted mb-16">
        The Attachment Provider registry — each entry is a pluggable generator that can be run (concurrently, and
        zipped together when more than one is requested) against a notify request via <code>attachmentOptions.providers</code>,
        or bundled into a reusable named set on the Attachment Schemas page. Providers marked unavailable are
        registered but not yet wired to a real data source.
      </div>

      <div className="grid grid-3" style={{ gap: 16 }}>
        {providers === null ? (
          [1, 2, 3].map(i => <div key={i} className="card"><div className="skeleton" style={{ height: 100 }} /></div>)
        ) : providers.map(p => (
          <div key={p.key} className="template-card">
            <div className="template-card-header">
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 }}>
                <div style={{ fontFamily: 'monospace', fontSize: 13, fontWeight: 700, color: 'var(--blue)' }}>{p.key}</div>
                <span className={`badge badge-${p.available ? 'active' : 'inactive'}`}>
                  {p.available ? <CheckCircle size={11} /> : <XCircle size={11} />} {p.available ? 'Available' : 'Unavailable'}
                </span>
              </div>
            </div>
            <div className="template-card-body">
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontWeight: 600, marginBottom: 4 }}>
                <Paperclip size={13} /> {p.displayName}
              </div>
              <div className="fs-12 text-secondary">{p.description}</div>
            </div>
          </div>
        ))}
        {providers && providers.length === 0 && (
          <div className="text-muted fs-12">No attachment providers registered.</div>
        )}
      </div>
    </div>
  );
}
