import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import { Activity, LogOut, UserCircle } from 'lucide-react';
import { auth } from '../api/client.js';

const PAGE_TITLES = {
  '/': 'Dashboard',
  '/metrics': 'Metrics',
  '/history': 'Notification History',
  '/failed': 'Failed & Escalated',
  '/queue': 'Queue Monitor',
  '/send': 'Send Notification',
  '/rules': 'Notification Rules',
  '/templates': 'Templates',
  '/channels/email': 'Email (SMTP)',
  '/channels/whatsapp': 'WhatsApp',
  '/escalation': 'Support Escalation',
  '/watchdog': 'Watchdog',
  '/logs': 'System Logs',
  '/audit': 'Audit Log',
  '/users': 'Users & API Keys',
  '/profile': 'Profile & Settings',
};

export default function Layout({ children }) {
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const title = PAGE_TITLES[pathname] || 'HS Notify';

  async function handleLogout() {
    await auth.logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app-shell">
      <Sidebar />
      <div className="main-area">
        <header className="topbar">
          <span className="topbar-title">{title}</span>
          <div className="topbar-pill">
            <Activity size={13} color="var(--green)" />
            <span style={{ color: 'var(--green)', fontWeight: 700, fontSize: 12 }}>LIVE</span>
          </div>
          <span className={`badge badge-${auth.role().toLowerCase()}`} title="Your role">{auth.role()}</span>
          <button className="btn btn-ghost btn-sm" onClick={() => navigate('/profile')} title="Profile & settings">
            <UserCircle size={13} /> {auth.currentUser()?.username || 'admin'}
          </button>
          <button className="btn btn-ghost btn-sm" onClick={handleLogout} title="Log out">
            <LogOut size={13} /> Logout
          </button>
        </header>
        <div className="page-content fade-in">
          {children}
        </div>
      </div>
    </div>
  );
}
