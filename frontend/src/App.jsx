import React from 'react';
import { HashRouter, Routes, Route, Navigate, Outlet, useLocation } from 'react-router-dom';
import Layout from './components/Layout.jsx';
import Login from './pages/Login.jsx';
import { auth } from './api/client.js';

import Dashboard from './pages/Dashboard.jsx';
import NotificationHistory from './pages/NotificationHistory.jsx';
import FailedNotifications from './pages/FailedNotifications.jsx';
import QueueMonitor from './pages/QueueMonitor.jsx';
import SendNotification from './pages/SendNotification.jsx';
import NotificationRules from './pages/NotificationRules.jsx';
import Templates from './pages/Templates.jsx';
import NotificationActions from './pages/NotificationActions.jsx';
import FormSchemas from './pages/FormSchemas.jsx';
import SmtpConfig from './pages/SmtpConfig.jsx';
import WhatsAppConfig from './pages/WhatsAppConfig.jsx';
import ChannelPage from './pages/ChannelPage.jsx';
import SupportEscalation from './pages/SupportEscalation.jsx';
import WatchdogPage from './pages/WatchdogPage.jsx';
import LogsPage from './pages/LogsPage.jsx';
import AuditLog from './pages/AuditLog.jsx';
import MetricsPage from './pages/MetricsPage.jsx';
import UsersPage from './pages/UsersPage.jsx';
import UserProfile from './pages/UserProfile.jsx';

function ProtectedLayout() {
  const location = useLocation();
  if (!auth.isLoggedIn()) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return (
    <Layout>
      <Outlet />
    </Layout>
  );
}

export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<ProtectedLayout />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/metrics" element={<MetricsPage />} />
          <Route path="/history" element={<NotificationHistory />} />
          <Route path="/failed" element={<FailedNotifications />} />
          <Route path="/queue" element={<QueueMonitor />} />
          <Route path="/send" element={<SendNotification />} />
          <Route path="/rules" element={<NotificationRules />} />
          <Route path="/templates" element={<Templates />} />
          <Route path="/actions" element={<NotificationActions />} />
          <Route path="/form-schemas" element={<FormSchemas />} />
          <Route path="/channels/email" element={<SmtpConfig />} />
          <Route path="/channels/whatsapp" element={<WhatsAppConfig />} />
          <Route path="/channels/:channel" element={<ChannelPage />} />
          <Route path="/escalation" element={<SupportEscalation />} />
          <Route path="/watchdog" element={<WatchdogPage />} />
          <Route path="/logs" element={<LogsPage />} />
          <Route path="/audit" element={<AuditLog />} />
          <Route path="/users" element={<UsersPage />} />
          <Route path="/profile" element={<UserProfile />} />
        </Route>
      </Routes>
    </HashRouter>
  );
}
