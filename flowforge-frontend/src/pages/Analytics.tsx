import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  RefreshCw,
  TrendingUp,
  CheckCircle2,
  AlertTriangle,
  Clock,
  Cpu,
  Layers,
  Play,
  RotateCcw,
  Activity,
  Timer,
  Plus,
} from 'lucide-react';
import { analyticsService } from '../api/services';
import { ApiError } from '../types';
import type {
  WorkloadAnalyticsResponse,
  JobTypeAnalyticsResponse,
  WorkerAnalyticsResponse,
  QueueAnalyticsResponse,
  JobStatus,
} from '../types';
import { LoadingState, ErrorState, EmptyState } from '../components/SharedStates';

// Status color mapping helper
function getStatusColor(status: JobStatus): string {
  switch (status) {
    case 'COMPLETED':
      return '#16a34a';
    case 'RUNNING':
      return '#059669';
    case 'QUEUED':
      return '#d97706';
    case 'CREATED':
      return '#64748b';
    case 'RETRYING':
      return '#db2777';
    case 'DEAD_LETTER':
      return '#dc2626';
    case 'CANCELLED':
      return '#4b5563';
    default:
      return '#9ca3af';
  }
}

export default function Analytics() {
  const navigate = useNavigate();

  const [workload, setWorkload] = useState<WorkloadAnalyticsResponse | null>(null);
  const [types, setTypes] = useState<JobTypeAnalyticsResponse[]>([]);
  const [workers, setWorkers] = useState<WorkerAnalyticsResponse | null>(null);
  const [queue, setQueue] = useState<QueueAnalyticsResponse | null>(null);

  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fetchData = useCallback(async (silent = false) => {
    if (silent) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);

    try {
      const [workloadRes, typesRes, workersRes, queueRes] = await Promise.all([
        analyticsService.getWorkloadAnalytics(),
        analyticsService.getJobTypeAnalytics(),
        analyticsService.getWorkerAnalytics(),
        analyticsService.getQueueAnalytics(),
      ]);

      setWorkload(workloadRes);
      setTypes(typesRes);
      setWorkers(workersRes);
      setQueue(queueRes);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.details?.message || err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load system analytics from backend.');
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchData();
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchData]);

  if (isLoading) {
    return <LoadingState message="Aggregating cluster analytics..." />;
  }

  if (error && !workload) {
    return (
      <ErrorState
        title="Unable to Load Analytics"
        message={error}
        onRetry={() => fetchData(false)}
      />
    );
  }

  if (!workload || workload.totalJobs === 0) {
    return (
      <div style={{ maxWidth: '800px', margin: '0 auto' }}>
        <EmptyState
          title="No Analytics Available"
          message="No jobs have been processed yet. Create and queue execution jobs to generate system telemetry and throughput analytics."
          actionLabel="Create First Job"
          onAction={() => navigate('/jobs/create')}
        />
      </div>
    );
  }

  const allStatuses: JobStatus[] = [
    'COMPLETED',
    'RUNNING',
    'QUEUED',
    'CREATED',
    'RETRYING',
    'DEAD_LETTER',
    'CANCELLED',
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.75rem' }}>
      {/* Header Bar */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '1rem',
        }}
      >
        <div>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#111827', margin: '0 0 0.25rem 0' }}>
            System Analytics
          </h2>
          <p style={{ margin: 0, color: '#6b7280', fontSize: '0.875rem' }}>
            Cluster-wide workload performance, job type distribution, and queue health.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <button
            onClick={() => fetchData(true)}
            disabled={isRefreshing}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.5rem 0.875rem',
              backgroundColor: '#ffffff',
              color: '#374151',
              border: '1px solid #d1d5db',
              borderRadius: '0.375rem',
              cursor: isRefreshing ? 'not-allowed' : 'pointer',
              fontSize: '0.875rem',
              fontWeight: 500,
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
            }}
          >
            <RefreshCw size={15} className={isRefreshing ? 'spin-anim' : ''} />
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

          <Link
            to="/jobs/create"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.5rem 1rem',
              backgroundColor: '#2563eb',
              color: '#ffffff',
              borderRadius: '0.375rem',
              textDecoration: 'none',
              fontSize: '0.875rem',
              fontWeight: 600,
              boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
            }}
          >
            <Plus size={16} />
            Create Job
          </Link>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
          gap: '1rem',
        }}
      >
        {/* Total Jobs */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.25rem',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Total Jobs</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#f3f4f6', borderRadius: '0.375rem', color: '#4b5563' }}>
              <Layers size={16} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#111827', marginTop: '0.5rem' }}>
            {workload.totalJobs}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#9ca3af' }}>Registered cluster workloads</span>
        </div>

        {/* Success Rate */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.25rem',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Success Rate</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#ecfdf5', borderRadius: '0.375rem', color: '#10b981' }}>
              <CheckCircle2 size={16} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#065f46', marginTop: '0.5rem' }}>
            {workload.successRate.toFixed(1)}%
          </div>
          <span style={{ fontSize: '0.75rem', color: '#059669' }}>Completed workloads</span>
        </div>

        {/* Failure Rate */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.25rem',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Failure Rate</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#fef2f2', borderRadius: '0.375rem', color: '#dc2626' }}>
              <AlertTriangle size={16} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: workload.failureRate > 0 ? '#991b1b' : '#111827', marginTop: '0.5rem' }}>
            {workload.failureRate.toFixed(1)}%
          </div>
          <span style={{ fontSize: '0.75rem', color: workload.failureRate > 0 ? '#dc2626' : '#9ca3af' }}>Dead-lettered jobs</span>
        </div>

        {/* Queue Depth */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.25rem',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Queue Depth</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#fef3c7', borderRadius: '0.375rem', color: '#d97706' }}>
              <Clock size={16} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#92400e', marginTop: '0.5rem' }}>
            {queue?.queueDepth ?? 0}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#b45309' }}>Waiting in queue</span>
        </div>

        {/* Running Jobs */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.25rem',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Running Jobs</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#eff6ff', borderRadius: '0.375rem', color: '#2563eb' }}>
              <Play size={16} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#1e40af', marginTop: '0.5rem' }}>
            {queue?.runningCount ?? 0}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#3b82f6' }}>Active worker threads</span>
        </div>

        {/* Active Workers */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.25rem',
            boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Active Workers</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#f3e8ff', borderRadius: '0.375rem', color: '#7e22ce' }}>
              <Cpu size={16} />
            </div>
          </div>
          <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#581c87', marginTop: '0.5rem' }}>
            {workers?.activeWorkerCount ?? 0}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#7e22ce' }}>Nodes holding leases</span>
        </div>
      </div>

      {/* Middle Section: Status Distribution & Queue Latency */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: '1.5rem',
        }}
      >
        {/* Status Breakdown Card */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.5rem',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.25rem' }}>
            <TrendingUp size={18} color="#374151" />
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#111827', margin: 0 }}>
              Job Status Distribution
            </h3>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
            {allStatuses.map((st) => {
              const count = workload.statusCounts[st] || 0;
              const pct = workload.totalJobs > 0 ? (count / workload.totalJobs) * 100 : 0;
              const color = getStatusColor(st);

              return (
                <div key={st}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem', fontSize: '0.8125rem' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                      <span
                        style={{
                          width: '8px',
                          height: '8px',
                          borderRadius: '50%',
                          backgroundColor: color,
                          display: 'inline-block',
                        }}
                      />
                      <span style={{ fontWeight: 600, color: '#374151' }}>{st}</span>
                    </div>
                    <span style={{ color: '#6b7280' }}>
                      <strong>{count}</strong> ({pct.toFixed(1)}%)
                    </span>
                  </div>

                  {/* Progress Bar */}
                  <div
                    style={{
                      height: '6px',
                      backgroundColor: '#f3f4f6',
                      borderRadius: '9999px',
                      overflow: 'hidden',
                    }}
                  >
                    <div
                      style={{
                        height: '100%',
                        width: `${pct}%`,
                        backgroundColor: color,
                        borderRadius: '9999px',
                        transition: 'width 0.3s ease',
                      }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        </div>

        {/* Queue & Latency Diagnostics */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            padding: '1.5rem',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1.25rem' }}>
            <Activity size={18} color="#374151" />
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#111827', margin: 0 }}>
              Queue & Latency Telemetry
            </h3>
          </div>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
              gap: '1rem',
              marginBottom: '1.5rem',
            }}
          >
            <div style={{ padding: '1rem', backgroundColor: '#f9fafb', borderRadius: '0.5rem', border: '1px solid #e5e7eb' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: '#6b7280', fontSize: '0.75rem', fontWeight: 600 }}>
                <Timer size={14} />
                Oldest Queued
              </div>
              <div style={{ fontSize: '1.4rem', fontWeight: 700, color: '#111827', marginTop: '0.4rem' }}>
                {queue?.oldestQueuedJobAgeSeconds ?? 0}s
              </div>
              <span style={{ fontSize: '0.7rem', color: '#9ca3af' }}>Max queuing latency</span>
            </div>

            <div style={{ padding: '1rem', backgroundColor: '#f9fafb', borderRadius: '0.5rem', border: '1px solid #e5e7eb' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', color: '#6b7280', fontSize: '0.75rem', fontWeight: 600 }}>
                <RotateCcw size={14} />
                Oldest Retrying
              </div>
              <div style={{ fontSize: '1.4rem', fontWeight: 700, color: '#111827', marginTop: '0.4rem' }}>
                {queue?.oldestRetryingJobAgeSeconds ?? 0}s
              </div>
              <span style={{ fontSize: '0.7rem', color: '#9ca3af' }}>Time in backoff delay</span>
            </div>
          </div>

          <div style={{ borderTop: '1px solid #f3f4f6', paddingTop: '1.25rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <span style={{ fontSize: '0.85rem', color: '#374151', fontWeight: 500 }}>Jobs in Retry Loop</span>
              <span style={{ fontSize: '0.85rem', fontWeight: 700, color: (queue?.retryingCount ?? 0) > 0 ? '#db2777' : '#111827' }}>
                {queue?.retryingCount ?? 0}
              </span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
              <span style={{ fontSize: '0.85rem', color: '#374151', fontWeight: 500 }}>Cluster Retry Rate</span>
              <span style={{ fontSize: '0.85rem', fontWeight: 700, color: '#111827' }}>
                {workload.retryRate.toFixed(1)}%
              </span>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.85rem', color: '#374151', fontWeight: 500 }}>Execution Engine</span>
              <span
                style={{
                  fontSize: '0.75rem',
                  fontWeight: 600,
                  backgroundColor: '#ecfdf5',
                  color: '#065f46',
                  padding: '0.15rem 0.45rem',
                  borderRadius: '0.25rem',
                }}
              >
                PostgreSQL Advisory Locked
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Section 3: Job Type Performance Breakdown */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          border: '1px solid #e5e7eb',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          overflow: 'hidden',
        }}
      >
        <div style={{ padding: '1.25rem 1.5rem', borderBottom: '1px solid #e5e7eb', backgroundColor: '#fafafa' }}>
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#111827', margin: 0 }}>
            Job Type Performance & Concurrency
          </h3>
          <p style={{ margin: '0.25rem 0 0 0', color: '#6b7280', fontSize: '0.8125rem' }}>
            Breakdown of duration benchmarks, completion rates, and recent 60-minute window activity by job type.
          </p>
        </div>

        {types.length === 0 ? (
          <div style={{ padding: '2.5rem', textAlign: 'center', color: '#6b7280', fontSize: '0.875rem' }}>
            No specific type breakdowns registered yet.
          </div>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.875rem' }}>
              <thead>
                <tr style={{ backgroundColor: '#f9fafb', borderBottom: '1px solid #e5e7eb', color: '#4b5563' }}>
                  <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                    Type
                  </th>
                  <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                    Total Jobs
                  </th>
                  <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                    Completed
                  </th>
                  <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                    Failed (DLQ)
                  </th>
                  <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                    Avg / Max Duration
                  </th>
                  <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase' }}>
                    Recent 60m Activity
                  </th>
                </tr>
              </thead>
              <tbody>
                {types.map((t) => (
                  <tr
                    key={t.type}
                    style={{ borderBottom: '1px solid #f3f4f6' }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#fafafa')}
                    onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                  >
                    <td style={{ padding: '0.875rem 1rem', fontWeight: 600, color: '#111827' }}>
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '0.3rem',
                          padding: '0.2rem 0.5rem',
                          backgroundColor: '#eff6ff',
                          color: '#1d4ed8',
                          border: '1px solid #bfdbfe',
                          borderRadius: '0.25rem',
                          fontSize: '0.75rem',
                          fontWeight: 700,
                        }}
                      >
                        <Layers size={11} />
                        {t.type}
                      </span>
                    </td>
                    <td style={{ padding: '0.875rem 1rem', fontWeight: 600, color: '#111827' }}>
                      {t.totalJobs}
                    </td>
                    <td style={{ padding: '0.875rem 1rem', color: '#166534', fontWeight: 600 }}>
                      {t.completedCount}
                    </td>
                    <td style={{ padding: '0.875rem 1rem', color: t.failedCount > 0 ? '#dc2626' : '#6b7280', fontWeight: 600 }}>
                      {t.failedCount}
                    </td>
                    <td style={{ padding: '0.875rem 1rem', color: '#4b5563', fontSize: '0.8125rem' }}>
                      {t.averageDurationMs} ms <span style={{ color: '#9ca3af' }}>/ {t.maxDurationMs} ms</span>
                    </td>
                    <td style={{ padding: '0.875rem 1rem', fontSize: '0.8125rem', color: '#4b5563' }}>
                      <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
                        <span>
                          Exec: <strong>{t.recentExecutions}</strong>
                        </span>
                        <span style={{ color: t.recentFailures > 0 ? '#dc2626' : '#6b7280' }}>
                          Fail: <strong>{t.recentFailures}</strong>
                        </span>
                        <span style={{ color: '#6b7280' }}>
                          Cancel: <strong>{t.recentCancellations}</strong>
                        </span>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Section 4: Worker Utilization */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          border: '1px solid #e5e7eb',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          padding: '1.5rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <Cpu size={18} color="#374151" />
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#111827', margin: 0 }}>
            Active Worker Lease Allocation
          </h3>
        </div>

        {(!workers || workers.activeWorkers.length === 0) ? (
          <div
            style={{
              padding: '1.5rem',
              backgroundColor: '#f9fafb',
              borderRadius: '0.5rem',
              border: '1px dashed #d1d5db',
              color: '#6b7280',
              fontSize: '0.875rem',
              textAlign: 'center',
            }}
          >
            No worker nodes are actively holding leases on running jobs at this moment. The worker pool will acquire transaction leases when queued jobs become eligible for dispatch.
          </div>
        ) : (
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '1rem',
            }}
          >
            {workers.activeWorkers.map((w) => (
              <div
                key={w.workerId}
                style={{
                  padding: '1.25rem',
                  borderRadius: '0.5rem',
                  border: '1px solid #e5e7eb',
                  backgroundColor: '#f9fafb',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
                  <span style={{ fontFamily: 'monospace', fontSize: '0.8125rem', fontWeight: 600, color: '#1e40af' }}>
                    {w.workerId}
                  </span>
                  <span
                    style={{
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      backgroundColor: '#dbeafe',
                      color: '#1e40af',
                      padding: '0.15rem 0.45rem',
                      borderRadius: '0.25rem',
                    }}
                  >
                    {w.runningJobsCount} active job{w.runningJobsCount === 1 ? '' : 's'}
                  </span>
                </div>

                <div style={{ marginTop: '0.5rem' }}>
                  <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>Running Workload IDs:</span>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem', marginTop: '0.25rem' }}>
                    {w.runningJobIds.map((jobId) => (
                      <Link
                        key={jobId}
                        to={`/jobs/${jobId}`}
                        style={{
                          fontFamily: 'monospace',
                          fontSize: '0.75rem',
                          color: '#2563eb',
                          textDecoration: 'none',
                        }}
                      >
                        {jobId}
                      </Link>
                    ))}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
