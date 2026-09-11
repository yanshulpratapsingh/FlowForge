import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Activity,
  Clock,
  AlertCircle,
  CheckCircle,
  RefreshCw,
  Users,
  Layers,
  XCircle,
  Database,
} from 'lucide-react';
import { metricsService, analyticsService } from '../api/services';
import type {
  JobMetricsResponse,
  WorkloadAnalyticsResponse,
  JobTypeAnalyticsResponse,
  WorkerAnalyticsResponse,
  QueueAnalyticsResponse,
} from '../types';
import { LoadingState, ErrorState, EmptyState } from '../components/SharedStates';

// Helper to format execution duration
function formatDuration(ms: number): string {
  if (ms <= 0) return '0 ms';
  if (ms < 1000) {
    return `${ms} ms`;
  }
  return `${(ms / 1000).toFixed(2)} s`;
}

// Helper to format wait ages
function formatAge(seconds: number): string {
  if (seconds <= 0) return '0 sec';
  if (seconds < 60) {
    return `${seconds} sec`;
  }
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  if (m < 60) {
    return `${m}m ${s}s`;
  }
  const h = Math.floor(m / 60);
  const mins = m % 60;
  return `${h}h ${mins}m`;
}

const Dashboard: React.FC = () => {
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);

  // Stats States
  const [metrics, setMetrics] = useState<JobMetricsResponse | null>(null);
  const [workload, setWorkload] = useState<WorkloadAnalyticsResponse | null>(null);
  const [types, setTypes] = useState<JobTypeAnalyticsResponse[] | null>(null);
  const [workers, setWorkers] = useState<WorkerAnalyticsResponse | null>(null);
  const [queue, setQueue] = useState<QueueAnalyticsResponse | null>(null);

  const isFetchingRef = useRef<boolean>(false);

  const fetchData = useCallback(async (isSilent = false) => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;

    if (!isSilent) {
      setLoading(true);
    } else {
      setIsRefreshing(true);
    }

    try {
      const results = await Promise.allSettled([
        metricsService.getJobMetrics(),
        analyticsService.getWorkloadAnalytics(),
        analyticsService.getJobTypeAnalytics(),
        analyticsService.getWorkerAnalytics(),
        analyticsService.getQueueAnalytics(),
      ]);

      const [metricsRes, workloadRes, typesRes, workersRes, queueRes] = results;

      let hasSuccess = false;
      let errorMsgs: string[] = [];

      if (metricsRes.status === 'fulfilled') {
        setMetrics(metricsRes.value);
        hasSuccess = true;
      } else {
        errorMsgs.push(`Metrics API: ${metricsRes.reason?.message || 'Failed'}`);
      }

      if (workloadRes.status === 'fulfilled') {
        setWorkload(workloadRes.value);
        hasSuccess = true;
      } else {
        errorMsgs.push(`Workload API: ${workloadRes.reason?.message || 'Failed'}`);
      }

      if (typesRes.status === 'fulfilled') {
        setTypes(typesRes.value);
        hasSuccess = true;
      } else {
        errorMsgs.push(`Job Types API: ${typesRes.reason?.message || 'Failed'}`);
      }

      if (workersRes.status === 'fulfilled') {
        setWorkers(workersRes.value);
        hasSuccess = true;
      } else {
        errorMsgs.push(`Workers API: ${workersRes.reason?.message || 'Failed'}`);
      }

      if (queueRes.status === 'fulfilled') {
        setQueue(queueRes.value);
        hasSuccess = true;
      } else {
        errorMsgs.push(`Queue API: ${queueRes.reason?.message || 'Failed'}`);
      }

      if (!hasSuccess && errorMsgs.length > 0) {
        setError(errorMsgs.join(' | '));
      } else {
        setError(null);
        setLastUpdated(new Date());
      }
    } catch (err: any) {
      setError(err?.message || 'An unexpected error occurred while loading dashboard metrics.');
    } finally {
      setLoading(false);
      setIsRefreshing(false);
      isFetchingRef.current = false;
    }
  }, []);

  useEffect(() => {
    const timeout = setTimeout(() => {
      fetchData();
    }, 0);

    const interval = setInterval(() => {
      fetchData(true);
    }, 10000);

    return () => {
      clearTimeout(timeout);
      clearInterval(interval);
    };
  }, [fetchData]);

  if (loading) {
    return <LoadingState message="Loading cluster dashboard analytics..." />;
  }

  if (error && !metrics && !workload) {
    return (
      <ErrorState
        title="Dashboard Failure"
        message={error}
        onRetry={() => fetchData(false)}
      />
    );
  }

  // Safely extract status metrics with 0 fallback
  const getStatusCount = (status: string): number => {
    if (metrics?.statusCounts) {
      return (metrics.statusCounts as any)[status] || 0;
    }
    if (workload?.statusCounts) {
      return (workload.statusCounts as any)[status] || 0;
    }
    return 0;
  };

  const totalJobs = metrics?.totalJobs ?? workload?.totalJobs ?? 0;
  const isDatabaseEmpty = totalJobs === 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '2rem' }}>
      {/* Title & Refresh controls */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 'bold', margin: '0 0 0.25rem 0' }}>
            Operational Dashboard
          </h2>
          {lastUpdated && (
            <p style={{ fontSize: '0.8rem', color: '#6b7280' }}>
              Last updated: {lastUpdated.toLocaleTimeString()}{' '}
              {isRefreshing && <span style={{ marginLeft: '0.5rem', color: '#3b82f6' }}>(refreshing...)</span>}
            </p>
          )}
        </div>
        <button
          onClick={() => fetchData(true)}
          disabled={isRefreshing}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.5rem',
            padding: '0.5rem 1rem',
            backgroundColor: '#ffffff',
            color: '#374151',
            border: '1px solid #d1d5db',
            borderRadius: '0.375rem',
            cursor: isRefreshing ? 'not-allowed' : 'pointer',
            fontSize: '0.875rem',
            fontWeight: 500,
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
            transition: 'background-color 0.2s',
          }}
          onMouseOver={(e) => {
            if (!isRefreshing) e.currentTarget.style.backgroundColor = '#f9fafb';
          }}
          onMouseOut={(e) => {
            if (!isRefreshing) e.currentTarget.style.backgroundColor = '#ffffff';
          }}
        >
          <RefreshCw size={16} className={isRefreshing ? 'spin-anim' : ''} />
          Refresh
          <style>{`
            @keyframes spin-anim-kf {
              from { transform: rotate(0deg); }
              to { transform: rotate(360deg); }
            }
            .spin-anim {
              animation: spin-anim-kf 1s linear infinite;
            }
          `}</style>
        </button>
      </div>

      {isDatabaseEmpty ? (
        <EmptyState
          title="Cluster Database is Empty"
          message="No workload executions or scheduler jobs exist in the PostgreSQL cluster yet. Create or queue a job to start telemetry."
        />
      ) : (
        <>
          {/* KPI Cards Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
              gap: '1.5rem',
            }}
          >
            {/* Total Jobs */}
            <div style={kpiCardStyle('#eff6ff', '#2563eb')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Total Jobs</span>
                <Database size={20} color="#2563eb" />
              </div>
              <div style={kpiValueStyle}>{totalJobs}</div>
            </div>

            {/* Queue Depth */}
            <div style={kpiCardStyle('#fef3c7', '#d97706')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Queue Depth</span>
                <Clock size={20} color="#d97706" />
              </div>
              <div style={kpiValueStyle}>{queue?.queueDepth ?? getStatusCount('QUEUED')}</div>
            </div>

            {/* Running Jobs */}
            <div style={kpiCardStyle('#ecfdf5', '#059669')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Running Jobs</span>
                <Activity size={20} color="#059669" />
              </div>
              <div style={kpiValueStyle}>{queue?.runningCount ?? getStatusCount('RUNNING')}</div>
            </div>

            {/* Retrying Jobs */}
            <div style={kpiCardStyle('#fdf2f8', '#db2777')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Retrying Jobs</span>
                <AlertCircle size={20} color="#db2777" />
              </div>
              <div style={kpiValueStyle}>{queue?.retryingCount ?? getStatusCount('RETRYING')}</div>
            </div>

            {/* Completed Jobs */}
            <div style={kpiCardStyle('#f0fdf4', '#16a34a')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Completed</span>
                <CheckCircle size={20} color="#16a34a" />
              </div>
              <div style={kpiValueStyle}>{getStatusCount('COMPLETED')}</div>
            </div>

            {/* Dead Letter Jobs */}
            <div style={kpiCardStyle('#fef2f2', '#dc2626')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Dead Letter</span>
                <XCircle size={20} color="#dc2626" />
              </div>
              <div style={kpiValueStyle}>{getStatusCount('DEAD_LETTER')}</div>
            </div>

            {/* Cancelled Jobs */}
            <div style={kpiCardStyle('#f3f4f6', '#4b5563')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Cancelled</span>
                <Layers size={20} color="#4b5563" />
              </div>
              <div style={kpiValueStyle}>{getStatusCount('CANCELLED')}</div>
            </div>

            {/* Active Workers */}
            <div style={kpiCardStyle('#faf5ff', '#7c3aed')}>
              <div style={kpiHeaderStyle}>
                <span style={kpiLabelStyle}>Active Workers</span>
                <Users size={20} color="#7c3aed" />
              </div>
              <div style={kpiValueStyle}>{workers?.activeWorkerCount ?? metrics?.activeWorkers ?? 0}</div>
            </div>
          </div>

          {/* Performance & Health split grids */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(400px, 1fr))',
              gap: '2rem',
            }}
          >
            {/* Performance Stats */}
            <div style={sectionCardStyle}>
              <h3 style={sectionTitleStyle}>Execution Performance</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Avg Execution Duration</span>
                  <span style={statValStyle}>
                    {formatDuration(metrics?.averageExecutionDurationMs ?? 0)}
                  </span>
                </div>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Max Execution Duration</span>
                  <span style={statValStyle}>
                    {formatDuration(metrics?.maxExecutionDurationMs ?? 0)}
                  </span>
                </div>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Recent Executions (60m)</span>
                  <span style={statValStyle}>{metrics?.recentExecutions ?? 0}</span>
                </div>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Recent Failures (60m)</span>
                  <span style={statValStyle}>{metrics?.recentFailures ?? 0}</span>
                </div>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Recent Cancellations (60m)</span>
                  <span style={statValStyle}>{metrics?.recentCancellations ?? 0}</span>
                </div>
              </div>
            </div>

            {/* Workload Health */}
            <div style={sectionCardStyle}>
              <h3 style={sectionTitleStyle}>Workload Health Rates</h3>
              {workload ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
                  {/* Success Rate */}
                  <div>
                    <div style={rateHeaderStyle}>
                      <span>Success Rate</span>
                      <strong>{workload.successRate.toFixed(2)}%</strong>
                    </div>
                    <div style={progressBarBgStyle}>
                      <div style={progressBarFillStyle(workload.successRate, '#10b981')} />
                    </div>
                  </div>

                  {/* Failure Rate */}
                  <div>
                    <div style={rateHeaderStyle}>
                      <span>Failure / DLQ Rate</span>
                      <strong>{workload.failureRate.toFixed(2)}%</strong>
                    </div>
                    <div style={progressBarBgStyle}>
                      <div style={progressBarFillStyle(workload.failureRate, '#ef4444')} />
                    </div>
                  </div>

                  {/* Retry Rate */}
                  <div>
                    <div style={rateHeaderStyle}>
                      <span>Retry Rate</span>
                      <strong>{workload.retryRate.toFixed(2)}%</strong>
                    </div>
                    <div style={progressBarBgStyle}>
                      <div style={progressBarFillStyle(workload.retryRate, '#f59e0b')} />
                    </div>
                  </div>
                </div>
              ) : (
                <p style={{ color: '#6b7280' }}>Analytics data unavailable.</p>
              )}
            </div>
          </div>

          {/* Queue & Workers split grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '1.5rem',
            }}
          >
            {/* Queue Monitor */}
            <div style={sectionCardStyle}>
              <h3 style={sectionTitleStyle}>Queue Metrics</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Queue Wait Age (Oldest)</span>
                  <span style={statValStyle}>
                    {formatAge(queue?.oldestQueuedJobAgeSeconds ?? 0)}
                  </span>
                </div>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Retry Wait Age (Oldest)</span>
                  <span style={statValStyle}>
                    {formatAge(queue?.oldestRetryingJobAgeSeconds ?? 0)}
                  </span>
                </div>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Currently Queued</span>
                  <span style={statValStyle}>{queue?.queueDepth ?? 0}</span>
                </div>
                <div style={statRowStyle}>
                  <span style={statLabelStyle}>Currently Running</span>
                  <span style={statValStyle}>{queue?.runningCount ?? 0}</span>
                </div>
              </div>
            </div>

            {/* Workers Monitor */}
            <div style={sectionCardStyle}>
              <h3 style={sectionTitleStyle}>Active Workers Pool</h3>
              {workers && workers.activeWorkers.length > 0 ? (
                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: '1rem',
                    maxHeight: '260px',
                    overflowY: 'auto',
                  }}
                >
                  {workers.activeWorkers.map((w) => (
                    <div
                      key={w.workerId}
                      style={{
                        padding: '1rem',
                        border: '1px solid #e5e7eb',
                        borderRadius: '0.375rem',
                        backgroundColor: '#f9fafb',
                      }}
                    >
                      <div
                        style={{
                          display: 'flex',
                          justifyContent: 'space-between',
                          marginBottom: '0.5rem',
                        }}
                      >
                        <span style={{ fontWeight: 'bold', fontSize: '0.9rem' }}>{w.workerId}</span>
                        <span
                          style={{
                            backgroundColor: '#dbeafe',
                            color: '#1e40af',
                            padding: '0.125rem 0.5rem',
                            borderRadius: '0.25rem',
                            fontSize: '0.75rem',
                            fontWeight: 'bold',
                          }}
                        >
                          Running: {w.runningJobsCount}
                        </span>
                      </div>
                      <div style={{ fontSize: '0.8rem', color: '#6b7280' }}>
                        Job IDs: {w.runningJobIds.join(', ')}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <EmptyState
                  title="No Active Workers"
                  message="There are currently no worker execution threads running workloads in this cluster."
                />
              )}
            </div>
          </div>

          {/* Job Type Overview Table */}
          <div style={sectionCardStyle}>
            <h3 style={sectionTitleStyle}>Job Type Diagnostics</h3>
            {types && types.length > 0 ? (
              <div style={{ overflowX: 'auto' }}>
                <table
                  style={{
                    width: '100%',
                    borderCollapse: 'collapse',
                    textAlign: 'left',
                    fontSize: '0.875rem',
                  }}
                >
                  <thead>
                    <tr style={{ borderBottom: '2px solid #e5e7eb', color: '#374151' }}>
                      <th style={thStyle}>Job Type</th>
                      <th style={thStyle}>Total Jobs</th>
                      <th style={thStyle}>Completed</th>
                      <th style={thStyle}>Failed</th>
                      <th style={thStyle}>Retrying</th>
                      <th style={thStyle}>Cancelled</th>
                      <th style={thStyle}>Avg Duration</th>
                      <th style={thStyle}>Max Duration</th>
                    </tr>
                  </thead>
                  <tbody>
                    {types.map((t) => (
                      <tr key={t.type} style={{ borderBottom: '1px solid #e5e7eb' }}>
                        <td style={tdBoldStyle}>{t.type}</td>
                        <td style={tdStyle}>{t.totalJobs}</td>
                        <td style={tdStyle}>{t.completedCount}</td>
                        <td style={tdStyle}>{t.failedCount}</td>
                        <td style={tdStyle}>{t.retryingCount}</td>
                        <td style={tdStyle}>{t.cancelledCount}</td>
                        <td style={tdStyle}>{formatDuration(t.averageDurationMs)}</td>
                        <td style={tdStyle}>{formatDuration(t.maxDurationMs)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState title="No Types Available" message="No type classification analytics exist yet." />
            )}
          </div>
        </>
      )}
    </div>
  );
};

// Styling Object Maps
const kpiCardStyle = (bgColor: string, borderColor: string): React.CSSProperties => ({
  backgroundColor: bgColor,
  borderLeft: `4px solid ${borderColor}`,
  borderRadius: '0.5rem',
  padding: '1.25rem',
  boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
  display: 'flex',
  flexDirection: 'column',
  justifyContent: 'space-between',
  minHeight: '110px',
});

const kpiHeaderStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  alignItems: 'center',
  marginBottom: '0.5rem',
};

const kpiLabelStyle: React.CSSProperties = {
  color: '#6b7280',
  fontSize: '0.85rem',
  fontWeight: 500,
};

const kpiValueStyle: React.CSSProperties = {
  fontSize: '1.75rem',
  fontWeight: 'bold',
  color: '#111827',
};

const sectionCardStyle: React.CSSProperties = {
  backgroundColor: '#ffffff',
  borderRadius: '0.5rem',
  padding: '1.5rem',
  boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
};

const sectionTitleStyle: React.CSSProperties = {
  fontSize: '1.1rem',
  fontWeight: 'bold',
  margin: '0 0 1.25rem 0',
  color: '#1f2937',
  borderBottom: '1px solid #f3f4f6',
  paddingBottom: '0.5rem',
};

const statRowStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  fontSize: '0.9rem',
  paddingBottom: '0.5rem',
  borderBottom: '1px dashed #f3f4f6',
};

const statLabelStyle: React.CSSProperties = {
  color: '#4b5563',
};

const statValStyle: React.CSSProperties = {
  fontWeight: 'bold',
  color: '#111827',
};

const rateHeaderStyle: React.CSSProperties = {
  display: 'flex',
  justifyContent: 'space-between',
  fontSize: '0.875rem',
  marginBottom: '0.25rem',
  color: '#4b5563',
};

const progressBarBgStyle: React.CSSProperties = {
  width: '100%',
  height: '8px',
  backgroundColor: '#e5e7eb',
  borderRadius: '9999px',
  overflow: 'hidden',
};

const progressBarFillStyle = (rate: number, color: string): React.CSSProperties => ({
  width: `${Math.min(100, Math.max(0, rate))}%`,
  height: '100%',
  backgroundColor: color,
  borderRadius: '9999px',
});

const thStyle: React.CSSProperties = {
  padding: '0.75rem 1rem',
  fontWeight: 600,
  color: '#4b5563',
};

const tdStyle: React.CSSProperties = {
  padding: '0.75rem 1rem',
  color: '#4b5563',
};

const tdBoldStyle: React.CSSProperties = {
  padding: '0.75rem 1rem',
  fontWeight: 'bold',
  color: '#111827',
};

export default Dashboard;
