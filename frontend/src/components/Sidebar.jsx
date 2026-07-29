import React from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import {
  LayoutDashboard, Bell, ListChecks, Layers, Mail, MessageCircle,
  Shield, Activity, FileText, BarChart3,
  Users, Send, Clock, AlertTriangle, Zap, LayoutTemplate, Paperclip, Package
} from 'lucide-react';

const NavSection = ({ label, children }) => (
  <div>
    <div className="nav-section-label">{label}</div>
    {children}
  </div>
);

const Item = ({ to, icon: Icon, label }) => (
  <NavLink to={to} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}>
    <Icon size={15} />
    <span>{label}</span>
  </NavLink>
);

export default function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="sidebar-logo">
        <div className="sidebar-logo-icon">
          <img src="/subex-mark.svg" alt="Subex" width={40} height={40} />
        </div>
        <div>
          <div className="sidebar-logo-text">HS Notify</div>
        </div>
      </div>

      <NavSection label="Overview">
        <Item to="/" icon={LayoutDashboard} label="Dashboard" />
        <Item to="/metrics" icon={BarChart3} label="Metrics" />
      </NavSection>

      <NavSection label="Notifications">
        <Item to="/history" icon={Bell} label="History" />
        <Item to="/failed" icon={AlertTriangle} label="Failed & Escalated" />
        <Item to="/queue" icon={Clock} label="Queue Monitor" />
        <Item to="/send" icon={Send} label="Send Notification" />
      </NavSection>

      <NavSection label="Rules & Templates">
        <Item to="/rules" icon={ListChecks} label="Notification Rules" />
        <Item to="/templates" icon={Layers} label="Templates" />
        <Item to="/actions" icon={Zap} label="Notification Actions" />
        <Item to="/form-schemas" icon={LayoutTemplate} label="Form Schemas" />
        <Item to="/attachment-providers" icon={Paperclip} label="Attachment Providers" />
        <Item to="/attachment-schemas" icon={Package} label="Attachment Schemas" />
      </NavSection>

      <NavSection label="Channels">
        <Item to="/channels/email" icon={Mail} label="Email (SMTP)" />
        <Item to="/channels/whatsapp" icon={MessageCircle} label="WhatsApp" />
      </NavSection>

      <NavSection label="Configuration">
        <Item to="/escalation" icon={Shield} label="Escalation" />
        <Item to="/watchdog" icon={Activity} label="Watchdog" />
      </NavSection>

      <NavSection label="Operations">
        <Item to="/logs" icon={FileText} label="System Logs" />
        <Item to="/audit" icon={ListChecks} label="Audit Log" />
      </NavSection>

      <NavSection label="Administration">
        <Item to="/users" icon={Users} label="Users & Keys" />
      </NavSection>

      <div className="sidebar-footer">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end' }}>
          <span>v2.0.0</span>
        </div>
      </div>
    </aside>
  );
}
