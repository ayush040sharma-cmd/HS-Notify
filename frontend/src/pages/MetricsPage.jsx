import React, { useEffect, useState } from 'react';
import { api } from '../api/client.js';
import { BarChart3, TrendingUp, Clock } from 'lucide-react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  LineChart, Line, Legend
} from 'recharts';

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: 'var(--card)', border: '1px solid var(--border)', borderRadius: 8, padding: '10px 14px', fontSize: 12 }}>
      <div className="fw-600 mb-4">{label}</div>
      {payload.map(p => (
        <div key={p.name} style={{ color: p.color, marginTop: 2 }}>{p.name}: {p.value}</div>
      ))}
    </div>
  );
};

export default function MetricsPage() {
  const [data, setData] = useState(null);
  const [period, setPeriod] = useState('hourly');

  useEffect(() => {
    if (api.metrics) {
      api.metrics().then(setData);
    }
  }, []);

  const chartData = data?.[period] || [];

  return (
    <div>
      <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
        <button className={`btn btn-sm ${period === 'hourly' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setPeriod('hourly')}>24 Hours</button>
        <button className={`btn btn-sm ${period === 'daily' ? 'btn-primary' : 'btn-secondary'}`} onClick={() => setPeriod('daily')}>7 Days</button>
      </div>

      {!data ? (
        <div className="card"><div className="skeleton" style={{ height: 220 }} /></div>
      ) : (
        <>
          {/* Sent vs Failed bar chart */}
          <div className="card mb-16">
            <div className="section-header mb-16">
              <div className="section-title"><BarChart3 size={14} /> Delivery Volume</div>
            </div>
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={chartData} barGap={2}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                <XAxis dataKey={period === 'hourly' ? 'hour' : 'day'} tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 11 }} axisLine={false} tickLine={false} />
                <Tooltip content={<CustomTooltip />} />
                <Legend wrapperStyle={{ fontSize: 12, color: 'var(--text-secondary)' }} />
                <Bar dataKey="sent" name="Sent" fill="var(--green)" radius={[3,3,0,0]} maxBarSize={28} />
                <Bar dataKey="failed" name="Failed" fill="var(--red)" radius={[3,3,0,0]} maxBarSize={28} />
              </BarChart>
            </ResponsiveContainer>
          </div>

          {/* Success rate line chart */}
          <div className="grid grid-2" style={{ gap: 16 }}>
            <div className="card">
              <div className="section-header mb-12">
                <div className="section-title"><TrendingUp size={14} /> Success Rate (%)</div>
              </div>
              <ResponsiveContainer width="100%" height={160}>
                <LineChart data={chartData.slice(-7).map((d, i) => ({ ...d, successRate: data.successRate?.[i] ?? 95 }))}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                  <XAxis dataKey={period === 'hourly' ? 'hour' : 'day'} tick={{ fill: 'var(--text-muted)', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <YAxis domain={[70, 100]} tick={{ fill: 'var(--text-muted)', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip content={<CustomTooltip />} />
                  <Line dataKey="successRate" name="Success %" stroke="var(--green)" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>

            <div className="card">
              <div className="section-header mb-12">
                <div className="section-title"><Clock size={14} /> Avg Response Time (ms)</div>
              </div>
              <ResponsiveContainer width="100%" height={160}>
                <LineChart data={chartData.slice(-7).map((d, i) => ({ ...d, avgMs: data.avgResponseMs?.[i] ?? 300 }))}>
                  <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                  <XAxis dataKey={period === 'hourly' ? 'hour' : 'day'} tick={{ fill: 'var(--text-muted)', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fill: 'var(--text-muted)', fontSize: 10 }} axisLine={false} tickLine={false} />
                  <Tooltip content={<CustomTooltip />} />
                  <Line dataKey="avgMs" name="Avg ms" stroke="var(--blue)" strokeWidth={2} dot={false} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
