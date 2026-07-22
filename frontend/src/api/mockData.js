const now = new Date();
const h = (n) => new Date(now - n * 3600000).toISOString();
const m = (n) => new Date(now - n * 60000).toISOString();

export const MOCK = {
  dashboardSummary: {
    emailsSent24h: 47,
    failedJobs24h: 3,
    pendingJobs: 2,
    activeRules: 3,
    healthScorePercent: 92,
    serviceStatus: 'UP',
    successRate: 94.0,
    avgDeliveryMs: 312,
  },

  channelBreakdown: [
    { channel: 'EMAIL', count: 34, color: '#6366f1' },
    { channel: 'WEBHOOK', count: 8, color: '#f97316' },
    { channel: 'SLACK', count: 5, color: '#10b981' },
  ],

  watchdogStatus: {
    consecutiveFailures: 0,
    totalRestarts: 1,
    escalationsSent: 0,
    pollIntervalSeconds: 30,
    failThreshold: 3,
    lastUpAt: h(0),
    lastDownAt: h(6),
    currentStatus: 'UP'
  },

  watchdogHealthLog: {
    content: [
      { healthLogId: 6, checkedAt: m(0),  status: 'UP',   responseTimeMs: 12,  detail: 'Actuator health returned UP', actionTaken: 'NONE' },
      { healthLogId: 5, checkedAt: m(30), status: 'UP',   responseTimeMs: 18,  detail: 'Actuator health returned UP', actionTaken: 'NONE' },
      { healthLogId: 4, checkedAt: m(60), status: 'UP',   responseTimeMs: 38,  detail: 'Actuator health returned UP', actionTaken: 'NONE' },
      { healthLogId: 3, checkedAt: m(90), status: 'DOWN', responseTimeMs: null, detail: 'Connection refused', actionTaken: 'ESCALATION_SENT' },
      { healthLogId: 2, checkedAt: h(2),  status: 'UP',   responseTimeMs: 22,  detail: 'Actuator health returned UP', actionTaken: 'NONE' },
      { healthLogId: 1, checkedAt: h(3),  status: 'UP',   responseTimeMs: 15,  detail: 'Actuator health returned UP', actionTaken: 'NONE' },
    ]
  },

  listJobs: {
    content: [
      {
        jobId: 1, ruleCode: 'PR_CLOSE_RULE', channel: 'EMAIL', status: 'SENT',
        toAddresses: ['fraud-ops@zain.example.com'],
        ccAddresses: ['fraud-ops-lead@zain.example.com'],
        subject: 'PR 10293 Closed - Action Required',
        attachmentStatus: 'GENERATED',
        attemptCount: 1, maxRetryCount: 3,
        nextRetryAt: null, lastError: null,
        createdAt: h(2), sentAt: h(2)
      },
      {
        jobId: 2, ruleCode: 'PR_CLOSE_RULE', channel: 'EMAIL', status: 'FAILED',
        toAddresses: ['fraud-ops@zain.example.com'],
        ccAddresses: [],
        subject: 'PR 10301 Closed - Action Required',
        attachmentStatus: 'SKIPPED',
        attemptCount: 3, maxRetryCount: 3,
        nextRetryAt: null, lastError: 'Connection refused: smtp.zain-internal.example.com:587',
        createdAt: h(5), sentAt: null
      },
      {
        jobId: 3, ruleCode: 'PR_CLOSE_RULE', channel: 'WEBHOOK', status: 'RETRYING',
        toAddresses: ['https://hooks.zain-internal.example.com/notify'],
        ccAddresses: [],
        subject: 'PR 10305 Closed - Action Required',
        attachmentStatus: 'NOT_APPLICABLE',
        attemptCount: 1, maxRetryCount: 3,
        nextRetryAt: m(-5), lastError: 'Connection refused',
        createdAt: h(1), sentAt: null
      },
      {
        jobId: 4, ruleCode: null, channel: 'EMAIL', status: 'SENT',
        toAddresses: ['analyst@zain.example.com'],
        ccAddresses: [],
        subject: 'Test direct email',
        attachmentStatus: null,
        attemptCount: 1, maxRetryCount: 1,
        nextRetryAt: null, lastError: null,
        createdAt: h(0.5), sentAt: h(0.5)
      },
      {
        jobId: 5, ruleCode: 'CASE_ESCALATE_RULE', channel: 'SLACK', status: 'SENT',
        toAddresses: ['https://hooks.slack.com/services/T000/B000/example'],
        ccAddresses: [],
        subject: 'Case 4821 Escalated - HIGH',
        attachmentStatus: 'NOT_APPLICABLE',
        attemptCount: 1, maxRetryCount: 3,
        nextRetryAt: null, lastError: null,
        createdAt: h(0.2), sentAt: h(0.2)
      },
      {
        jobId: 6, ruleCode: 'ALARM_CLOSE_RULE', channel: 'WEBHOOK', status: 'SENT',
        toAddresses: ['https://api.zain-ops.example.com/webhook/alarms'],
        ccAddresses: [],
        subject: 'Alarm ALM-9923 Closed',
        attachmentStatus: 'NOT_APPLICABLE',
        attemptCount: 1, maxRetryCount: 3,
        nextRetryAt: null, lastError: null,
        createdAt: h(0.8), sentAt: h(0.8)
      },
      {
        jobId: 7, ruleCode: 'PR_CLOSE_RULE', channel: 'EMAIL', status: 'ESCALATED',
        toAddresses: ['fraud-ops@zain.example.com'],
        ccAddresses: [],
        subject: 'PR 10289 Closed - Action Required',
        attachmentStatus: 'FAILED',
        attemptCount: 3, maxRetryCount: 3,
        nextRetryAt: null, lastError: 'SMTP authentication failed',
        createdAt: h(8), sentAt: null
      },
    ]
  },

  listRules: [
    {
      ruleId: 1, ruleCode: 'PR_CLOSE_RULE', triggerEvent: 'PR_CLOSED',
      template: { templateCode: 'PR_CLOSE_MAIL', channel: 'EMAIL' },
      maxRetryCount: 3, retryBackoffSeconds: 60, retryBackoffMultiplier: 2.0,
      onFinalFailure: 'ESCALATE', status: 'ACTIVE', active: true,
      createdBy: 'system-seed', approvedBy: 'system-seed',
      createdAt: h(48), updatedAt: h(2)
    },
    {
      ruleId: 2, ruleCode: 'CASE_ESCALATE_RULE', triggerEvent: 'CASE_ESCALATED',
      template: { templateCode: 'CASE_ESCALATE_SLACK', channel: 'SLACK' },
      maxRetryCount: 3, retryBackoffSeconds: 120, retryBackoffMultiplier: 2.0,
      onFinalFailure: 'ESCALATE', status: 'PENDING_REVIEW', active: false,
      createdBy: 'analyst.jdoe', approvedBy: null,
      createdAt: h(12), updatedAt: h(12)
    },
    {
      ruleId: 3, ruleCode: 'ALARM_CLOSE_RULE', triggerEvent: 'ALARM_CLOSED',
      template: { templateCode: 'ALARM_CLOSE_WEBHOOK', channel: 'WEBHOOK' },
      maxRetryCount: 5, retryBackoffSeconds: 30, retryBackoffMultiplier: 1.5,
      onFinalFailure: 'HOLD_FOR_MANUAL', status: 'ACTIVE', active: true,
      createdBy: 'ops.admin', approvedBy: 'ops.lead',
      createdAt: h(24), updatedAt: h(4)
    },
  ],

  listTemplates: [
    {
      templateId: 1, templateCode: 'PR_CLOSE_MAIL', version: 1, channel: 'EMAIL',
      subjectTemplate: 'PR {{pr_id}} Closed - Action Required',
      bodyTemplate: '<p>Hello,</p><p>PR <strong>{{pr_id}}</strong> for account <strong>{{account_name}}</strong> was closed by {{closed_by}} on {{close_date}}.</p>',
      allowedVariables: ['pr_id', 'account_name', 'closed_by', 'close_date'],
      piiMaskFields: [],
      status: 'ACTIVE',
      createdAt: h(48), updatedAt: h(2)
    },
    {
      templateId: 2, templateCode: 'CASE_ESCALATE_SLACK', version: 1, channel: 'SLACK',
      subjectTemplate: 'Case {{case_id}} Escalated - {{severity}}',
      bodyTemplate: '*Case {{case_id}}* has been escalated to level {{escalation_level}}.\nReason: {{reason}}',
      allowedVariables: ['case_id', 'severity', 'escalation_level', 'reason'],
      piiMaskFields: ['account_number'],
      status: 'PENDING_REVIEW',
      createdAt: h(12), updatedAt: h(12)
    },
    {
      templateId: 3, templateCode: 'ALARM_CLOSE_WEBHOOK', version: 1, channel: 'WEBHOOK',
      subjectTemplate: 'Alarm {{alarm_id}} closed by {{closed_by}}',
      bodyTemplate: '{"alarm_id":"{{alarm_id}}","closed_by":"{{closed_by}}","reason":"{{close_reason}}"}',
      allowedVariables: ['alarm_id', 'closed_by', 'close_reason'],
      piiMaskFields: [],
      status: 'ACTIVE',
      createdAt: h(24), updatedAt: h(4)
    },
  ],

  listAudit: {
    content: [
      { auditId: 7, occurredAt: m(5),  eventType: 'SEND_SUCCESS',     job: { jobId: 6 }, rule: { ruleCode: 'ALARM_CLOSE_RULE' }, eventDetail: 'Webhook delivered 200 OK', actor: 'system', responseTimeMs: 145 },
      { auditId: 6, occurredAt: h(0.2), eventType: 'SEND_SUCCESS',    job: { jobId: 5 }, rule: { ruleCode: 'CASE_ESCALATE_RULE' }, eventDetail: 'Slack message delivered', actor: 'system', responseTimeMs: 234 },
      { auditId: 5, occurredAt: h(0.5), eventType: 'SEND_SUCCESS',    job: { jobId: 4 }, rule: null,                              eventDetail: 'Email delivered via SMTP relay', actor: 'system', responseTimeMs: 203 },
      { auditId: 4, occurredAt: h(1),   eventType: 'RETRY_SCHEDULED', job: { jobId: 3 }, rule: { ruleCode: 'PR_CLOSE_RULE' },     eventDetail: 'Retry 1/3 scheduled in 60s', actor: 'system', responseTimeMs: null },
      { auditId: 3, occurredAt: h(2),   eventType: 'SEND_SUCCESS',    job: { jobId: 1 }, rule: { ruleCode: 'PR_CLOSE_RULE' },     eventDetail: 'Email delivered via SMTP relay', actor: 'system', responseTimeMs: 842 },
      { auditId: 2, occurredAt: h(5),   eventType: 'SEND_FAILED',     job: { jobId: 2 }, rule: { ruleCode: 'PR_CLOSE_RULE' },     eventDetail: 'Max retries exhausted', actor: 'system', responseTimeMs: 5001 },
      { auditId: 1, occurredAt: h(8),   eventType: 'ESCALATION_SENT', job: { jobId: 7 }, rule: { ruleCode: 'PR_CLOSE_RULE' },     eventDetail: 'Escalation sent to fraud-ops-lead@zain.example.com', actor: 'system', responseTimeMs: null },
    ]
  },

  systemLogs: [
    { logId: 1, ts: m(0),   level: 'INFO',  component: 'RetryScheduler',    message: 'Scanned 2 RETRYING jobs. 1 eligible for retry.' },
    { logId: 2, ts: m(1),   level: 'WARN',  component: 'MailDispatchService', message: 'Webhook endpoint timeout after 5000ms for job #3' },
    { logId: 3, ts: m(5),   level: 'INFO',  component: 'WatchdogScheduler',  message: 'Health check passed. responseTimeMs=12' },
    { logId: 4, ts: m(15),  level: 'INFO',  component: 'SlackChannelSender', message: 'Slack webhook delivered. statusCode=200' },
    { logId: 5, ts: m(30),  level: 'INFO',  component: 'WatchdogScheduler',  message: 'Health check passed. responseTimeMs=18' },
    { logId: 6, ts: m(45),  level: 'INFO',  component: 'EmailChannelSender', message: 'MimeMessage sent. jobId=4 to=analyst@zain.example.com' },
    { logId: 7, ts: m(60),  level: 'INFO',  component: 'WatchdogScheduler',  message: 'Health check passed. responseTimeMs=38' },
    { logId: 8, ts: h(2),   level: 'INFO',  component: 'NotificationService', message: 'Job #1 submitted. ruleCode=PR_CLOSE_RULE tenantId=ZAIN' },
    { logId: 9, ts: h(5),   level: 'ERROR', component: 'EmailChannelSender', message: 'SMTP connection refused: smtp.zain-internal.example.com:587' },
    { logId: 10, ts: h(6),  level: 'ERROR', component: 'WatchdogScheduler',  message: 'Consecutive failures=3 exceeds threshold. Sending escalation.' },
    { logId: 11, ts: h(6),  level: 'INFO',  component: 'EscalationService',  message: 'Escalation email sent to fraud-ops-lead@zain.example.com' },
    { logId: 12, ts: h(8),  level: 'INFO',  component: 'RetryScheduler',     message: 'Job #7 final failure. onFinalFailure=ESCALATE' },
  ],

  metrics: {
    hourly: Array.from({ length: 24 }, (_, i) => ({
      hour: `${String(23 - i).padStart(2, '0')}:00`,
      sent: Math.floor(Math.random() * 8) + 1,
      failed: Math.floor(Math.random() * 2),
    })).reverse(),
    daily: Array.from({ length: 7 }, (_, i) => {
      const d = new Date(now - i * 86400000);
      return {
        day: d.toLocaleDateString('en-US', { weekday: 'short' }),
        sent: Math.floor(Math.random() * 60) + 20,
        failed: Math.floor(Math.random() * 8),
      };
    }).reverse(),
    successRate: [94, 91, 97, 88, 95, 93, 96],
    avgResponseMs: [312, 445, 289, 523, 301, 420, 310],
  },

  queueStats: {
    pendingCount: 2,
    sendingCount: 1,
    retryingCount: 1,
    failedCount: 3,
    escalatedCount: 1,
    oldestPendingAgeMinutes: 12,
    avgProcessingTimeMs: 312,
    throughputPerHour: 23,
  },

  smtpConfig: {
    host: 'subex-com.mail.protection.outlook.com',
    port: 25,
    username: '',
    useTls: false,
    useSsl: false,
    connectionTimeoutMs: 5000,
    fromName: 'HyperSense Notifications',
    fromEmail: 'support.alerts@subex.com',
  },

  whatsappConfig: {
    businessAccountId: '',
    phoneNumberId: '',
    webhookUrl: '',
    apiKeyConfigured: false,
  },

  users: [
    { userId: 1, name: 'System Seed', email: 'system@hs-notify.internal', role: 'ADMIN', status: 'ACTIVE', lastLogin: h(0) },
    { userId: 2, name: 'John Doe', email: 'analyst.jdoe@zain.example.com', role: 'OPERATOR', status: 'ACTIVE', lastLogin: h(3) },
    { userId: 3, name: 'Ops Lead', email: 'ops.lead@zain.example.com', role: 'APPROVER', status: 'ACTIVE', lastLogin: h(12) },
    { userId: 4, name: 'Ops Admin', email: 'ops.admin@zain.example.com', role: 'ADMIN', status: 'ACTIVE', lastLogin: h(6) },
    { userId: 5, name: 'Viewer User', email: 'viewer@zain.example.com', role: 'VIEWER', status: 'INACTIVE', lastLogin: h(72) },
  ],

  serviceHealth: [
    { name: 'Database (PostgreSQL)', status: 'UP', responseTimeMs: 4, details: 'Connection pool OK — 4/10 used' },
    { name: 'Mail Server (SMTP)', status: 'UP', responseTimeMs: 28, details: 'subex-com.mail.protection.outlook.com:25 reachable' },
    { name: 'Quartz Scheduler', status: 'UP', responseTimeMs: 1, details: 'RetryScheduler + WatchdogScheduler active' },
    { name: 'Report Service', status: 'STUB', responseTimeMs: null, details: 'Stub — wired at Milestone 4' },
    { name: 'Slack Webhook', status: 'UP', responseTimeMs: 145, details: 'Last delivery 200 OK' },
  ],

  escalationConfig: {
    chain: [
      { order: 1, recipient: 'fraud-ops-lead@zain.example.com', channel: 'EMAIL', delayMinutes: 0 },
      { order: 2, recipient: 'fraud-director@zain.example.com', channel: 'EMAIL', delayMinutes: 30 },
      { order: 3, recipient: 'https://hooks.slack.com/services/T000/B000/escalate', channel: 'SLACK', delayMinutes: 60 },
    ],
    maxEscalationLevel: 3,
    escalationCooldownMinutes: 120,
  },

  apiKeys: [
    { keyId: 1, prefix: 'dev-', label: 'Dev Local', tenantCode: 'ZAIN', status: 'ACTIVE', createdAt: h(720), lastUsedAt: h(0) },
    { keyId: 2, prefix: 'stg-', label: 'Staging CI', tenantCode: 'ZAIN', status: 'ACTIVE', createdAt: h(360), lastUsedAt: h(2) },
  ],
};

const delay = (ms = 300) => new Promise(r => setTimeout(r, ms));

let jobIdSeq = 8;

export const mockApi = {
  dashboardSummary:    () => delay().then(() => ({ ...MOCK.dashboardSummary })),
  channelBreakdown:    () => delay().then(() => [...MOCK.channelBreakdown]),
  watchdogStatus:      () => delay().then(() => ({ ...MOCK.watchdogStatus })),
  watchdogHealthLog:   () => delay().then(() => ({ ...MOCK.watchdogHealthLog })),
  serviceHealth:       () => delay().then(() => [...MOCK.serviceHealth]),
  listJobs: (status) => delay().then(() => ({
    content: status ? MOCK.listJobs.content.filter(j => j.status === status) : MOCK.listJobs.content
  })),
  listRules:           () => delay().then(() => [...MOCK.listRules]),
  listTemplates:       () => delay().then(() => [...MOCK.listTemplates]),
  listAudit:           () => delay().then(() => ({ ...MOCK.listAudit })),
  systemLogs:          (level) => delay().then(() => ({
    content: level ? MOCK.systemLogs.filter(l => l.level === level) : MOCK.systemLogs
  })),
  metrics:             () => delay(500).then(() => ({ ...MOCK.metrics })),
  queueStats:          () => delay().then(() => ({ ...MOCK.queueStats })),
  smtpConfig:          () => delay().then(() => ({ ...MOCK.smtpConfig })),
  whatsappConfig:      () => delay().then(() => ({ ...MOCK.whatsappConfig })),
  users:               () => delay().then(() => [...MOCK.users]),
  escalationConfig:    () => delay().then(() => ({ ...MOCK.escalationConfig })),
  apiKeys:             () => delay().then(() => [...MOCK.apiKeys]),

  requeueJob: (jobId) => delay().then(() => {
    const j = MOCK.listJobs.content.find(j => j.jobId === jobId);
    if (j) { j.status = 'SENT'; j.sentAt = new Date().toISOString(); j.attemptCount++; }
    return j;
  }),
  cancelJob: (jobId) => delay().then(() => {
    const j = MOCK.listJobs.content.find(j => j.jobId === jobId);
    if (j) j.status = 'CANCELLED';
    return j;
  }),

  approveRule: (ruleId) => delay().then(() => {
    const r = MOCK.listRules.find(r => r.ruleId === ruleId);
    if (r) { r.status = 'ACTIVE'; r.active = true; r.approvedBy = 'operator'; }
    return r;
  }),
  submitRuleForReview: (ruleId) => delay().then(() => {
    const r = MOCK.listRules.find(r => r.ruleId === ruleId);
    if (r) r.status = 'PENDING_REVIEW';
    return r;
  }),
  disableRule: (ruleId) => delay().then(() => {
    const r = MOCK.listRules.find(r => r.ruleId === ruleId);
    if (r) { r.status = 'DISABLED'; r.active = false; }
    return r;
  }),

  sendByRule: (payload) => delay(600).then(() => ({
    jobId: jobIdSeq++, ruleCode: payload.ruleCode, channel: 'EMAIL', status: 'SENT',
    toAddresses: [payload.recipientOverride || 'fraud-ops@zain.example.com'],
    ccAddresses: [], subject: 'Mock: rule send', attachmentStatus: 'SKIPPED',
    attemptCount: 1, maxRetryCount: 3, nextRetryAt: null, lastError: null,
    createdAt: new Date().toISOString(), sentAt: new Date().toISOString()
  })),

  updateWhatsAppConfig: (payload) => delay().then(() => {
    MOCK.whatsappConfig.businessAccountId = payload.businessAccountId ?? MOCK.whatsappConfig.businessAccountId;
    MOCK.whatsappConfig.phoneNumberId = payload.phoneNumberId ?? MOCK.whatsappConfig.phoneNumberId;
    MOCK.whatsappConfig.webhookUrl = payload.webhookUrl ?? MOCK.whatsappConfig.webhookUrl;
    if (payload.apiKey) MOCK.whatsappConfig.apiKeyConfigured = true;
    return { ...MOCK.whatsappConfig };
  }),

  sendDirect: (payload) => delay(600).then(() => ({
    jobId: jobIdSeq++, ruleCode: null, channel: 'EMAIL', status: 'SENT',
    toAddresses: payload.to, ccAddresses: payload.cc || [],
    subject: payload.subject, attachmentStatus: null,
    attemptCount: 1, maxRetryCount: 1, nextRetryAt: null, lastError: null,
    createdAt: new Date().toISOString(), sentAt: new Date().toISOString()
  })),
};
