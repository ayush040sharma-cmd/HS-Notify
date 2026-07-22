import React, { useState } from 'react';
import { auth } from '../api/client.js';
import { getTheme, applyTheme } from '../theme.js';
import { UserCircle, Sun, Moon, Shield, Clock } from 'lucide-react';

export default function UserProfile() {
  const user = auth.currentUser();
  const [theme, setTheme] = useState(getTheme());

  const pickTheme = (t) => {
    applyTheme(t);
    setTheme(t);
  };

  return (
    <div style={{ maxWidth: 560 }}>
      <div className="card mb-16">
        <div className="card-title mb-16"><UserCircle size={14} /> Account</div>
        <div className="config-row">
          <span className="config-key">Username</span>
          <span className="config-val">{user?.username || 'admin'}</span>
        </div>
        {user?.expiresInMinutes != null && (
          <div className="config-row">
            <span className="config-key"><Clock size={12} style={{ verticalAlign: -2 }} /> Session length</span>
            <span className="config-val">{user.expiresInMinutes} minutes</span>
          </div>
        )}
        <div className="fs-11 text-muted mt-8" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <Shield size={12} /> Dashboard login is a single shared operator credential — per-user accounts
          (the Users tab under Administration) aren't wired to individual passwords yet.
        </div>
      </div>

      <div className="card">
        <div className="card-title mb-16">Appearance</div>
        <div className="form-group">
          <label className="form-label">Theme</label>
          <div className="theme-toggle">
            <button className={theme === 'dark' ? 'active' : ''} onClick={() => pickTheme('dark')}>
              <Moon size={13} /> Dark
            </button>
            <button className={theme === 'light' ? 'active' : ''} onClick={() => pickTheme('light')}>
              <Sun size={13} /> Light
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
