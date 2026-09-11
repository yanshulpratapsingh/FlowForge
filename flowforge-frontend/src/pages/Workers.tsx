import { useState, useEffect, useCallback, useMemo } from 'react';
import { Link } from 'react-router-dom';
import {
  Cpu,
  RefreshCw,
  Clock,
  ExternalLink,
  ShieldCheck,
  Activity,
  Play,
  Plus,
} from 'lucide-react';
import { analyticsService, jobService } from '../api/services';
import { ApiError } from '../types';
import type { WorkerAnalyticsResponse, QueueAnalyticsResponse, Job } from '../types';
import { LoadingState, ErrorState } from '../components/SharedStates';

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString([], {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

export default function Workers() {

  const [workerData, setWorkerData] = useState<WorkerAnalyticsResponse | null>(null);
  const [queueData, setQueueData] = useState<QueueAnalyticsResponse | null>(null);
  const [runningJobsMap, setRunningJobsMap] = useState<Map<string, Job>>(new Map());

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
      const [workersRes, queueRes, allJobsRes] = await Promise.all([
        analyticsService.getWorkerAnalytics(),
        analyticsService.getQueueAnalytics(),
        jobService.getJobs().catch(() => [] as Job[]),
      ]);

      setWorkerData(workersRes);
      setQueueData(queueRes);

      // Build map of running jobs for quick lookup by ID
      const map = new Map<string, Job>();
      allJobsRes.forEach((job) => {
        map.set(job.id, job);
      });
      setRunningJobsMap(map);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.details?.message || err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to fetch worker analytics from the backend.');
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

  // Compute total active leases across workers
  const totalActiveLeases = useMemo(() => {
    if (!workerData) return 0;
    return workerData.activeWorkers.reduce((acc, w) => acc + w.runningJobsCount, 0);
  }, [workerData]);

  if (isLoading) {
    return <LoadingState message="Scanning cluster worker nodes..." />;
  }

  if (error && !workerData) {
    return (
      <ErrorState
        title="Unable to Load Worker Fleet"
        message={error}
        onRetry={() => fetchData(false)}
      />
    );
  }

  const activeWorkerCount = workerData?.activeWorkerCount ?? 0;

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
            Worker Node Fleet
          </h2>
          <p style={{ margin: 0, color: '#6b7280', fontSize: '0.875rem' }}>
            Monitor active worker thread allocations, running workload leases, and pool utilization.
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
            Refresh Fleet
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
            Create Workload
          </Link>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
          gap: '1.25rem',
        }}
      >
        {/* Active Worker Count */}
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
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Active Nodes</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#f3e8ff', borderRadius: '0.375rem', color: '#7e22ce' }}>
              <Cpu size={18} />
            </div>
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 700, color: activeWorkerCount > 0 ? '#581c87' : '#111827', marginTop: '0.5rem' }}>
            {activeWorkerCount}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>Nodes holding job leases</span>
        </div>

        {/* Active Leases */}
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
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Running Leases</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#ecfdf5', borderRadius: '0.375rem', color: '#10b981' }}>
              <Play size={18} />
            </div>
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 700, color: totalActiveLeases > 0 ? '#065f46' : '#111827', marginTop: '0.5rem' }}>
            {totalActiveLeases}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>Tasks currently in execution</span>
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
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Pending Queue</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#fef3c7', borderRadius: '0.375rem', color: '#d97706' }}>
              <Clock size={18} />
            </div>
          </div>
          <div style={{ fontSize: '1.85rem', fontWeight: 700, color: (queueData?.queueDepth ?? 0) > 0 ? '#92400e' : '#111827', marginTop: '0.5rem' }}>
            {queueData?.queueDepth ?? 0}
          </div>
          <span style={{ fontSize: '0.75rem', color: '#6b7280' }}>Waiting for worker allocation</span>
        </div>

        {/* Pool Fleet Status */}
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
            <span style={{ fontSize: '0.8125rem', fontWeight: 600, color: '#6b7280' }}>Pool Status</span>
            <div style={{ padding: '0.35rem', backgroundColor: '#eff6ff', borderRadius: '0.375rem', color: '#2563eb' }}>
              <Activity size={18} />
            </div>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', marginTop: '0.75rem' }}>
            <span
              style={{
                width: '10px',
                height: '10px',
                borderRadius: '50%',
                backgroundColor: activeWorkerCount > 0 ? '#10b981' : '#64748b',
                display: 'inline-block',
              }}
            />
            <span style={{ fontSize: '1rem', fontWeight: 700, color: '#111827' }}>
              {activeWorkerCount > 0 ? 'Active Execution' : 'Standby / Idle'}
            </span>
          </div>
          <span style={{ fontSize: '0.75rem', color: '#6b7280', display: 'block', marginTop: '0.2rem' }}>
            {activeWorkerCount > 0 ? 'Workers processing queue' : 'Awaiting queued jobs'}
          </span>
        </div>
      </div>

      {/* Main Content Area */}
      {activeWorkerCount === 0 ? (
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
            padding: '3rem 2rem',
            textAlign: 'center',
          }}
        >
          <div
            style={{
              width: '56px',
              height: '56px',
              borderRadius: '50%',
              backgroundColor: '#f3f4f6',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 1.25rem auto',
              color: '#6b7280',
            }}
          >
            <Cpu size={28} />
          </div>
          <h3 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#111827', margin: '0 0 0.5rem 0' }}>
            No Active Worker Leases
          </h3>
          <p style={{ color: '#6b7280', fontSize: '0.875rem', maxWidth: '520px', margin: '0 auto 1.5rem auto', lineHeight: '1.5' }}>
            The Spring Boot worker pool daemon is healthy and polling. Workers claim short-term transaction leases dynamically when executable jobs enter the queue.
          </p>

          <div style={{ display: 'inline-flex', gap: '0.75rem' }}>
            <Link
              to="/jobs"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                padding: '0.5rem 1rem',
                backgroundColor: '#ffffff',
                color: '#374151',
                border: '1px solid #d1d5db',
                borderRadius: '0.375rem',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.875rem',
              }}
            >
              View Jobs Queue
            </Link>
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
                fontWeight: 600,
                fontSize: '0.875rem',
              }}
            >
              <Plus size={15} />
              Create New Job
            </Link>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          <h3 style={{ fontSize: '1.15rem', fontWeight: 700, color: '#111827', margin: 0 }}>
            Active Worker Nodes ({activeWorkerCount})
          </h3>

          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))',
              gap: '1.25rem',
            }}
          >
            {workerData?.activeWorkers.map((worker) => (
              <div
                key={worker.workerId}
                style={{
                  backgroundColor: '#ffffff',
                  borderRadius: '0.75rem',
                  border: '1px solid #e5e7eb',
                  boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
                  padding: '1.5rem',
                }}
              >
                {/* Worker Node Card Header */}
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '1.25rem' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
                    <div
                      style={{
                        padding: '0.5rem',
                        backgroundColor: '#eff6ff',
                        borderRadius: '0.375rem',
                        color: '#2563eb',
                      }}
                    >
                      <Cpu size={20} />
                    </div>
                    <div>
                      <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase' }}>
                        Worker Node
                      </div>
                      <div style={{ fontFamily: 'monospace', fontSize: '0.85rem', fontWeight: 700, color: '#111827' }}>
                        {worker.workerId}
                      </div>
                    </div>
                  </div>

                  <span
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '0.3rem',
                      padding: '0.2rem 0.6rem',
                      backgroundColor: '#ecfdf5',
                      color: '#065f46',
                      border: '1px solid #a7f3d0',
                      borderRadius: '9999px',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                    }}
                  >
                    <span
                      style={{
                        width: '6px',
                        height: '6px',
                        borderRadius: '50%',
                        backgroundColor: '#10b981',
                        display: 'inline-block',
                      }}
                    />
                    {worker.runningJobsCount} Lease{worker.runningJobsCount === 1 ? '' : 's'}
                  </span>
                </div>

                {/* Assigned Running Jobs */}
                <div>
                  <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', marginBottom: '0.5rem' }}>
                    Active Workload Execution
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
                    {worker.runningJobIds.map((jobId) => {
                      const enrichedJob = runningJobsMap.get(jobId);

                      return (
                        <div
                          key={jobId}
                          style={{
                            padding: '0.75rem 1rem',
                            backgroundColor: '#f9fafb',
                            borderRadius: '0.5rem',
                            border: '1px solid #e5e7eb',
                          }}
                        >
                          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <Link
                              to={`/jobs/${jobId}`}
                              style={{
                                fontWeight: 600,
                                fontSize: '0.875rem',
                                color: '#2563eb',
                                textDecoration: 'none',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '0.3rem',
                              }}
                            >
                              {enrichedJob ? enrichedJob.name : jobId}
                              <ExternalLink size={12} />
                            </Link>

                            {enrichedJob && (
                              <span
                                style={{
                                  fontSize: '0.7rem',
                                  fontWeight: 700,
                                  backgroundColor: '#eff6ff',
                                  color: '#1d4ed8',
                                  padding: '0.1rem 0.4rem',
                                  borderRadius: '0.25rem',
                                }}
                              >
                                {enrichedJob.type}
                              </span>
                            )}
                          </div>

                          <div style={{ display: 'flex', gap: '1rem', marginTop: '0.4rem', fontSize: '0.75rem', color: '#6b7280' }}>
                            <span style={{ fontFamily: 'monospace' }}>ID: {jobId.slice(0, 8)}...</span>
                            {enrichedJob?.startedAt && (
                              <span>Started: {formatDate(enrichedJob.startedAt)}</span>
                            )}
                            {enrichedJob?.leaseUntil && (
                              <span style={{ color: '#b45309' }}>Lease: {formatDate(enrichedJob.leaseUntil)}</span>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Worker Pool Architecture Reference */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          border: '1px solid #e5e7eb',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          padding: '1.5rem 2rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.75rem' }}>
          <ShieldCheck size={18} color="#2563eb" />
          <h3 style={{ fontSize: '1.05rem', fontWeight: 700, color: '#111827', margin: 0 }}>
            Worker Architecture & Lease Safety
          </h3>
        </div>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
            gap: '1.25rem',
            marginTop: '1rem',
            fontSize: '0.85rem',
            color: '#4b5563',
            lineHeight: '1.5',
          }}
        >
          <div style={{ padding: '1rem', backgroundColor: '#f9fafb', borderRadius: '0.5rem', border: '1px solid #f3f4f6' }}>
            <div style={{ fontWeight: 600, color: '#111827', marginBottom: '0.25rem' }}>Advisory Locking</div>
            Type-level rate and concurrency limits are guaranteed via PostgreSQL transaction-scoped advisory locks (<code>pg_advisory_xact_lock</code>) to prevent race conditions.
          </div>

          <div style={{ padding: '1rem', backgroundColor: '#f9fafb', borderRadius: '0.5rem', border: '1px solid #f3f4f6' }}>
            <div style={{ fontWeight: 600, color: '#111827', marginBottom: '0.25rem' }}>Lease Durations</div>
            Worker threads acquire time-bound leases (30s default). If a worker process crashes, its heartbeat expires and the scheduler re-claims the job.
          </div>

          <div style={{ padding: '1rem', backgroundColor: '#f9fafb', borderRadius: '0.5rem', border: '1px solid #f3f4f6' }}>
            <div style={{ fontWeight: 600, color: '#111827', marginBottom: '0.25rem' }}>Starvation Prevention</div>
            Low-priority jobs that remain queued beyond the starvation threshold (300s) receive priority boosting to prevent queue head-of-line blocking.
          </div>
        </div>
      </div>
    </div>
  );
}
