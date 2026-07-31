import axios from 'axios';
import { mockApi } from './mockData.js';

const API_KEY = import.meta.env.VITE_API_KEY || 'dev-local-key-change-me';
// Shared secret required by LookupTokenFilter on GET /actions and
// /actions/{code}/schema (see SecurityConfig) — the dashboard is an
// authenticated caller too, but that filter runs before JWT/API-key
// resolution, so it must be sent like any other static header.
const LOOKUP_TOKEN = import.meta.env.VITE_LOOKUP_TOKEN || 'changeme-lookup-token';
const MOCK = import.meta.env.VITE_MOCK === 'true';
const TOKEN_KEY = 'hs_admin_token';

const client = axios.create({
  baseURL: '/api/v1',
  headers: {
    'X-HS-API-Key': API_KEY,
    'X-HS-Lookup-Token': LOOKUP_TOKEN,
    'Content-Type': 'application/json'
  }
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && !error.config?.url?.startsWith('/auth/')) {
      localStorage.removeItem(TOKEN_KEY);
      if (!window.location.hash.startsWith('#/login')) window.location.hash = '#/login';
    }
    return Promise.reject(error);
  }
);

// Gates the dashboard SPA behind a login screen. In VITE_MOCK mode there's no
// backend to authenticate against, so any non-empty credentials are accepted.
const USER_KEY = 'hs_admin_user';

// Role tiers, lowest to highest — used by hasMinRole() for UI gating. The
// backend (SecurityConfig path matchers) is the real enforcement point; this
// is UX only, so a stale/tampered client-side role can never grant real access.
const ROLE_RANK = { VIEWER: 0, ANALYST: 1, MANAGER: 2, RAFM_HEAD: 2, ADMIN: 3 };

export const auth = {
  isLoggedIn: () => !!localStorage.getItem(TOKEN_KEY),
  currentUser: () => {
    try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null'); }
    catch { return null; }
  },
  role: () => auth.currentUser()?.role || 'VIEWER',
  hasMinRole: (minRole) => (ROLE_RANK[auth.role()] ?? 0) >= (ROLE_RANK[minRole] ?? 0),
  isAdmin: () => auth.role() === 'ADMIN',
  login: async (username, password) => {
    if (MOCK) {
      if (!username || !password) {
        const err = new Error('Username and password required');
        err.response = { status: 401 };
        throw err;
      }
      localStorage.setItem(TOKEN_KEY, 'mock-session-token');
      localStorage.setItem(USER_KEY, JSON.stringify({ username, displayName: username, role: 'ADMIN', expiresInMinutes: null }));
      return { username };
    }
    const { data } = await client.post('/auth/login', { username, password });
    localStorage.setItem(TOKEN_KEY, data.token);
    localStorage.setItem(USER_KEY, JSON.stringify({
      username: data.username, displayName: data.displayName, role: data.role, expiresInMinutes: data.expiresInMinutes,
    }));
    return data;
  },
  logout: async () => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    if (!MOCK) {
      try { await client.post('/auth/logout'); } catch { /* stateless session — nothing to invalidate server-side */ }
    }
  },
};

const liveApi = {
  dashboardSummary: () => client.get('/dashboard/summary').then(r => r.data),
  serviceHealth: () => client.get('/service-health').then(r => r.data),

  watchdogStatus: () => client.get('/watchdog/status').then(r => r.data),
  watchdogHealthLog: (page = 0, size = 50) =>
    client.get(`/watchdog/health-log?page=${page}&size=${size}`).then(r => r.data),

  listJobs: (status, page = 0, size = 25) =>
    client.get('/jobs', { params: { status, page, size } }).then(r => r.data),
  requeueJob: (jobId) => client.post(`/jobs/${jobId}/requeue`).then(r => r.data),
  cancelJob: (jobId) => client.post(`/jobs/${jobId}/cancel`).then(r => r.data),
  queueStats: () => client.get('/jobs/stats').then(r => r.data),

  listRules: () => client.get('/rules').then(r => r.data),
  ruleFormOptions: () => client.get('/rules/form-options').then(r => r.data),
  createRule: (payload) => client.post('/rules', payload).then(r => r.data),
  updateRule: (ruleId, payload) => client.put(`/rules/${ruleId}`, payload).then(r => r.data),
  approveRule: (ruleId) => client.post(`/rules/${ruleId}/approve`).then(r => r.data),
  submitRuleForReview: (ruleId) => client.post(`/rules/${ruleId}/submit-for-review`).then(r => r.data),
  disableRule: (ruleId) => client.post(`/rules/${ruleId}/disable`).then(r => r.data),
  deleteRule: (ruleId) => client.delete(`/rules/${ruleId}`).then(r => r.data),

  listTemplates: () => client.get('/templates').then(r => r.data),
  createTemplate: (payload) => client.post('/templates', payload).then(r => r.data),
  updateTemplate: (templateId, payload) => client.put(`/templates/${templateId}`, payload).then(r => r.data),
  approveTemplate: (templateId) => client.post(`/templates/${templateId}/approve`).then(r => r.data),
  deleteTemplate: (templateId) => client.delete(`/templates/${templateId}`).then(r => r.data),

  listAudit: (page = 0, size = 50) =>
    client.get('/audit', { params: { page, size } }).then(r => r.data),

  sendByRule: (payload) => client.post('/notifications/send-by-rule', payload).then(r => r.data),
  sendDirect: (payload) => client.post('/notifications/send-direct', payload).then(r => r.data),
  uploadAttachment: (file) => {
    const form = new FormData();
    form.append('file', file);
    // Content-Type must be left for the browser to set (with the multipart boundary) —
    // the client instance's default 'application/json' header would otherwise win.
    return client.post('/attachments', form, { headers: { 'Content-Type': undefined } }).then(r => r.data);
  },

  systemLogs: (level) => client.get('/logs', { params: { level } }).then(r => r.data),
  metrics: () => client.get('/metrics').then(r => r.data),

  smtpConfig: () => client.get('/smtp-config').then(r => r.data),
  updateSmtpConfig: (payload) => client.put('/smtp-config', payload).then(r => r.data),

  whatsappConfig: () => client.get('/whatsapp-config').then(r => r.data),
  updateWhatsAppConfig: (payload) => client.put('/whatsapp-config', payload).then(r => r.data),

  escalationConfig: () => client.get('/escalation-config').then(r => r.data),
  updateEscalationConfig: (payload) => client.put('/escalation-config', payload).then(r => r.data),

  users: () => client.get('/users').then(r => r.data),
  createUser: (payload) => client.post('/users', payload).then(r => r.data),
  updateUser: (id, payload) => client.put(`/users/${id}`, payload).then(r => r.data),
  apiKeys: () => client.get('/users/api-keys').then(r => r.data),

  listActions: () => client.get('/actions').then(r => r.data),
  createAction: (payload) => client.post('/actions', payload).then(r => r.data),
  updateAction: (id, payload) => client.put(`/actions/${id}`, payload).then(r => r.data),
  deleteAction: (id) => client.delete(`/actions/${id}`).then(r => r.data),
  actionSchema: (code) => client.get(`/actions/${code}/schema`).then(r => r.data),

  listFormSchemas: () => client.get('/form-schemas').then(r => r.data),
  createFormSchema: (payload) => client.post('/form-schemas', payload).then(r => r.data),
  updateFormSchema: (id, payload) => client.put(`/form-schemas/${id}`, payload).then(r => r.data),
  deleteFormSchema: (id) => client.delete(`/form-schemas/${id}`).then(r => r.data),

  notify: (payload) => client.post('/notify', payload).then(r => r.data),

  listAttachmentProviders: () => client.get('/attachments/providers').then(r => r.data),

  listAttachmentSchemas: () => client.get('/attachment-schemas').then(r => r.data),
  createAttachmentSchema: (payload) => client.post('/attachment-schemas', payload).then(r => r.data),
  updateAttachmentSchema: (id, payload) => client.put(`/attachment-schemas/${id}`, payload).then(r => r.data),
  deleteAttachmentSchema: (id) => client.delete(`/attachment-schemas/${id}`).then(r => r.data),
};

// Switch to mock API when VITE_MOCK=true (no backend required)
export const api = MOCK ? mockApi : liveApi;

export default client;
