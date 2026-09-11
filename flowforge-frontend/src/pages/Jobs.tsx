import { useState, useEffect, useCallback, useMemo } from 'react';
import type { ChangeEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  RefreshCw,
  Plus,
  Play,
  XCircle,
  Eye,
  Search,
  CheckCircle2,
  AlertCircle,
  Clock,
  Layers,
  Calendar,
} from 'lucide-react';
import { jobService } from '../api/services';
import { ApiError } from '../types';
import type { Job, JobStatus } from '../types';
import { LoadingState, ErrorState, EmptyState } from '../components/SharedStates';

// Status badge styling helper
function getStatusBadgeStyle(status: JobStatus): { bg: string; text: string; border: string } {
  switch (status) {
    case 'CREATED':
      return { bg: '#f1f5f9', text: '#475569', border: '#cbd5e1' };
    case 'QUEUED':
      return { bg: '#fef3c7', text: '#92400e', border: '#fde68a' };
    case 'RUNNING':
      return { bg: '#ecfdf5', text: '#065f46', border: '#a7f3d0' };
    case 'RETRYING':
      return { bg: '#fdf2f8', text: '#9d174d', border: '#fbcfe8' };
    case 'COMPLETED':
      return { bg: '#f0fdf4', text: '#166534', border: '#bbf7d0' };
    case 'DEAD_LETTER':
      return { bg: '#fef2f2', text: '#991b1b', border: '#fecaca' };
    case 'CANCELLED':
      return { bg: '#f3f4f6', text: '#374151', border: '#e5e7eb' };
    default:
      return { bg: '#f3f4f6', text: '#4b5563', border: '#e5e7eb' };
  }
}

// Format date helper
function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString([], {
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  } catch {
    return dateStr;
  }
}

export default function Jobs() {
  const navigate = useNavigate();

  const [jobs, setJobs] = useState<Job[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Feedback notifications
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Search & Filter state
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [typeFilter, setTypeFilter] = useState<string>('ALL');

  // Action state
  const [actionInProgressId, setActionInProgressId] = useState<string | null>(null);
  const [jobToCancel, setJobToCancel] = useState<Job | null>(null);

  const fetchJobs = useCallback(async (silent = false) => {
    if (silent) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    setError(null);

    try {
      const data = await jobService.getJobs();
      // Sort newest first
      const sorted = [...data].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      );
      setJobs(sorted);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        setError(err.details?.message || err.message);
      } else if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to fetch jobs from backend.');
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchJobs();
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchJobs]);

  // Extract distinct job types for the filter dropdown
  const availableTypes = useMemo(() => {
    const typesSet = new Set<string>();
    jobs.forEach((j) => {
      if (j.type) typesSet.add(j.type);
    });
    return Array.from(typesSet).sort();
  }, [jobs]);

  // Filtered jobs list
  const filteredJobs = useMemo(() => {
    return jobs.filter((job) => {
      // Status filter
      if (statusFilter !== 'ALL' && job.status !== statusFilter) {
        return false;
      }
      // Type filter
      if (typeFilter !== 'ALL' && job.type !== typeFilter) {
        return false;
      }
      // Search query (name, id, payload)
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchesName = job.name.toLowerCase().includes(q);
        const matchesId = job.id.toLowerCase().includes(q);
        const matchesType = job.type.toLowerCase().includes(q);
        return matchesName || matchesId || matchesType;
      }
      return true;
    });
  }, [jobs, statusFilter, typeFilter, searchQuery]);

  // Queue Job Action
  const handleQueueJob = async (job: Job) => {
    setActionInProgressId(job.id);
    setFeedback(null);

    try {
      const updatedJob = await jobService.queueJob(job.id);
      setJobs((prev) => prev.map((j) => (j.id === job.id ? updatedJob : j)));
      setFeedback({
        type: 'success',
        message: `Job "${job.name}" has been queued for execution.`,
      });
    } catch (err: unknown) {
      let msg = 'Failed to queue job.';
      if (err instanceof ApiError) {
        msg = err.details?.message || `Failed to queue job (HTTP ${err.status})`;
      } else if (err instanceof Error) {
        msg = err.message;
      }
      setFeedback({ type: 'error', message: msg });
    } finally {
      setActionInProgressId(null);
    }
  };

  // Confirm Cancellation
  const handleConfirmCancel = async () => {
    if (!jobToCancel) return;
    const target = jobToCancel;
    setJobToCancel(null);
    setActionInProgressId(target.id);
    setFeedback(null);

    try {
      const updatedJob = await jobService.cancelJob(target.id);
      setJobs((prev) => prev.map((j) => (j.id === target.id ? updatedJob : j)));
      setFeedback({
        type: 'success',
        message: `Job "${target.name}" has been cancelled.`,
      });
    } catch (err: unknown) {
      let msg = 'Failed to cancel job.';
      if (err instanceof ApiError) {
        msg = err.details?.message || `Failed to cancel job (HTTP ${err.status})`;
      } else if (err instanceof Error) {
        msg = err.message;
      }
      setFeedback({ type: 'error', message: msg });
    } finally {
      setActionInProgressId(null);
    }
  };

  if (isLoading) {
    return <LoadingState message="Loading jobs..." />;
  }

  if (error && jobs.length === 0) {
    return (
      <ErrorState
        title="Unable to Load Jobs"
        message={error}
        onRetry={() => fetchJobs(false)}
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* Page Header Bar */}
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
            Job Management
          </h2>
          <p style={{ margin: 0, color: '#6b7280', fontSize: '0.875rem' }}>
            Monitor, queue, and cancel workflow jobs across the FlowForge cluster.
          </p>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <button
            onClick={() => fetchJobs(true)}
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

      {/* Feedback Banner */}
      {feedback && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0.875rem 1.25rem',
            borderRadius: '0.5rem',
            backgroundColor: feedback.type === 'success' ? '#f0fdf4' : '#fef2f2',
            border: `1px solid ${feedback.type === 'success' ? '#bbf7d0' : '#fca5a5'}`,
            color: feedback.type === 'success' ? '#166534' : '#991b1b',
            fontSize: '0.875rem',
            fontWeight: 500,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            {feedback.type === 'success' ? (
              <CheckCircle2 size={18} color="#16a34a" />
            ) : (
              <AlertCircle size={18} color="#dc2626" />
            )}
            <span>{feedback.message}</span>
          </div>
          <button
            onClick={() => setFeedback(null)}
            style={{
              background: 'none',
              border: 'none',
              color: 'inherit',
              cursor: 'pointer',
              fontWeight: 700,
              fontSize: '1rem',
              padding: '0 0.25rem',
            }}
          >
            &times;
          </button>
        </div>
      )}

      {/* Main Content Card */}
      {jobs.length === 0 ? (
        <EmptyState
          title="No Jobs Found"
          message="No workload jobs have been created in FlowForge yet. Register a new job to start orchestration."
          actionLabel="Create First Job"
          onAction={() => navigate('/jobs/create')}
        />
      ) : (
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
            overflow: 'hidden',
          }}
        >
          {/* Filter / Search Bar */}
          <div
            style={{
              padding: '1rem 1.5rem',
              borderBottom: '1px solid #e5e7eb',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              flexWrap: 'wrap',
              gap: '1rem',
              backgroundColor: '#fafafa',
            }}
          >
            {/* Search Input */}
            <div style={{ position: 'relative', minWidth: '240px', flex: '1 1 240px' }}>
              <Search
                size={16}
                color="#9ca3af"
                style={{ position: 'absolute', left: '0.75rem', top: '50%', transform: 'translateY(-50%)' }}
              />
              <input
                type="text"
                value={searchQuery}
                onChange={(e: ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
                placeholder="Search by job name, ID, or type..."
                style={{
                  width: '100%',
                  padding: '0.45rem 0.75rem 0.45rem 2.25rem',
                  border: '1px solid #d1d5db',
                  borderRadius: '0.375rem',
                  fontSize: '0.85rem',
                  outline: 'none',
                  boxSizing: 'border-box',
                }}
              />
            </div>

            {/* Status & Type Selectors */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280' }}>Status:</span>
                <select
                  value={statusFilter}
                  onChange={(e: ChangeEvent<HTMLSelectElement>) => setStatusFilter(e.target.value)}
                  style={{
                    padding: '0.4rem 0.6rem',
                    border: '1px solid #d1d5db',
                    borderRadius: '0.375rem',
                    fontSize: '0.8125rem',
                    color: '#374151',
                    outline: 'none',
                    backgroundColor: '#ffffff',
                  }}
                >
                  <option value="ALL">All Statuses</option>
                  <option value="CREATED">CREATED</option>
                  <option value="QUEUED">QUEUED</option>
                  <option value="RUNNING">RUNNING</option>
                  <option value="RETRYING">RETRYING</option>
                  <option value="COMPLETED">COMPLETED</option>
                  <option value="DEAD_LETTER">DEAD_LETTER</option>
                  <option value="CANCELLED">CANCELLED</option>
                </select>
              </div>

              {availableTypes.length > 0 && (
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
                  <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280' }}>Type:</span>
                  <select
                    value={typeFilter}
                    onChange={(e: ChangeEvent<HTMLSelectElement>) => setTypeFilter(e.target.value)}
                    style={{
                      padding: '0.4rem 0.6rem',
                      border: '1px solid #d1d5db',
                      borderRadius: '0.375rem',
                      fontSize: '0.8125rem',
                      color: '#374151',
                      outline: 'none',
                      backgroundColor: '#ffffff',
                    }}
                  >
                    <option value="ALL">All Types</option>
                    {availableTypes.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              {(searchQuery || statusFilter !== 'ALL' || typeFilter !== 'ALL') && (
                <button
                  onClick={() => {
                    setSearchQuery('');
                    setStatusFilter('ALL');
                    setTypeFilter('ALL');
                  }}
                  style={{
                    border: 'none',
                    background: 'none',
                    color: '#2563eb',
                    fontSize: '0.8125rem',
                    cursor: 'pointer',
                    textDecoration: 'underline',
                    padding: '0.25rem',
                  }}
                >
                  Clear
                </button>
              )}
            </div>
          </div>

          {/* Table Container */}
          {filteredJobs.length === 0 ? (
            <div style={{ padding: '3rem', textAlign: 'center', color: '#6b7280', fontSize: '0.875rem' }}>
              No jobs match the specified search query or status filter.
            </div>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.875rem' }}>
                <thead>
                  <tr style={{ backgroundColor: '#f9fafb', borderBottom: '1px solid #e5e7eb', color: '#4b5563' }}>
                    <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      Job
                    </th>
                    <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      Type
                    </th>
                    <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      Priority
                    </th>
                    <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      Status
                    </th>
                    <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      Retries
                    </th>
                    <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                      Timeline
                    </th>
                    <th style={{ padding: '0.75rem 1rem', fontWeight: 600, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.05em', textAlign: 'right' }}>
                      Actions
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {filteredJobs.map((job) => {
                    const statusBadge = getStatusBadgeStyle(job.status);
                    const isBusy = actionInProgressId === job.id;
                    const canQueue = job.status === 'CREATED';
                    const canCancel =
                      job.status === 'QUEUED' || job.status === 'RUNNING' || job.status === 'RETRYING';

                    return (
                      <tr
                        key={job.id}
                        style={{
                          borderBottom: '1px solid #f3f4f6',
                          transition: 'background-color 0.15s ease',
                        }}
                        onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = '#fafafa')}
                        onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
                      >
                        {/* Name & ID */}
                        <td style={{ padding: '0.875rem 1rem', verticalAlign: 'middle' }}>
                          <Link
                            to={`/jobs/${job.id}`}
                            style={{
                              fontWeight: 600,
                              color: '#111827',
                              textDecoration: 'none',
                              display: 'block',
                              fontSize: '0.9rem',
                            }}
                          >
                            {job.name}
                          </Link>
                          <span
                            style={{
                              fontFamily: 'monospace',
                              fontSize: '0.75rem',
                              color: '#6b7280',
                              display: 'inline-block',
                              marginTop: '0.15rem',
                            }}
                          >
                            {job.id}
                          </span>
                        </td>

                        {/* Type */}
                        <td style={{ padding: '0.875rem 1rem', verticalAlign: 'middle' }}>
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
                            {job.type}
                          </span>
                        </td>

                        {/* Priority */}
                        <td style={{ padding: '0.875rem 1rem', verticalAlign: 'middle' }}>
                          <span
                            style={{
                              fontWeight: 600,
                              color: job.priority > 0 ? '#b45309' : '#4b5563',
                              fontSize: '0.875rem',
                            }}
                          >
                            {job.priority}
                          </span>
                        </td>

                        {/* Status */}
                        <td style={{ padding: '0.875rem 1rem', verticalAlign: 'middle' }}>
                          <span
                            style={{
                              display: 'inline-block',
                              padding: '0.2rem 0.55rem',
                              backgroundColor: statusBadge.bg,
                              color: statusBadge.text,
                              border: `1px solid ${statusBadge.border}`,
                              borderRadius: '9999px',
                              fontSize: '0.75rem',
                              fontWeight: 700,
                              letterSpacing: '0.025em',
                            }}
                          >
                            {job.status}
                          </span>
                        </td>

                        {/* Retries */}
                        <td style={{ padding: '0.875rem 1rem', verticalAlign: 'middle', color: '#4b5563', fontSize: '0.8125rem' }}>
                          {job.retryCount} / {job.maxRetries}
                        </td>

                        {/* Timeline */}
                        <td style={{ padding: '0.875rem 1rem', verticalAlign: 'middle', fontSize: '0.75rem', color: '#6b7280' }}>
                          <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem' }}>
                            <Clock size={12} />
                            <span>Created: {formatDate(job.createdAt)}</span>
                          </div>
                          {job.scheduledAt && (
                            <div style={{ display: 'flex', alignItems: 'center', gap: '0.25rem', marginTop: '0.2rem', color: '#854d0e' }}>
                              <Calendar size={12} />
                              <span>Sched: {formatDate(job.scheduledAt)}</span>
                            </div>
                          )}
                        </td>

                        {/* Actions */}
                        <td style={{ padding: '0.875rem 1rem', verticalAlign: 'middle', textAlign: 'right' }}>
                          <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5rem' }}>
                            {/* Queue Button */}
                            {canQueue && (
                              <button
                                onClick={() => handleQueueJob(job)}
                                disabled={isBusy}
                                title="Queue this job for worker execution"
                                style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: '0.25rem',
                                  padding: '0.35rem 0.65rem',
                                  backgroundColor: isBusy ? '#dbeafe' : '#2563eb',
                                  color: '#ffffff',
                                  border: 'none',
                                  borderRadius: '0.375rem',
                                  fontSize: '0.75rem',
                                  fontWeight: 600,
                                  cursor: isBusy ? 'not-allowed' : 'pointer',
                                  boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
                                }}
                              >
                                <Play size={12} />
                                Queue
                              </button>
                            )}

                            {/* Cancel Button */}
                            {canCancel && (
                              <button
                                onClick={() => setJobToCancel(job)}
                                disabled={isBusy}
                                title="Cancel this running/queued job"
                                style={{
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: '0.25rem',
                                  padding: '0.35rem 0.65rem',
                                  backgroundColor: '#ffffff',
                                  color: '#dc2626',
                                  border: '1px solid #fca5a5',
                                  borderRadius: '0.375rem',
                                  fontSize: '0.75rem',
                                  fontWeight: 600,
                                  cursor: isBusy ? 'not-allowed' : 'pointer',
                                }}
                              >
                                <XCircle size={12} />
                                Cancel
                              </button>
                            )}

                            {/* View Details Link */}
                            <Link
                              to={`/jobs/${job.id}`}
                              title="View details and logs"
                              style={{
                                display: 'inline-flex',
                                alignItems: 'center',
                                padding: '0.35rem 0.5rem',
                                backgroundColor: '#f3f4f6',
                                color: '#4b5563',
                                borderRadius: '0.375rem',
                                textDecoration: 'none',
                                fontSize: '0.75rem',
                                fontWeight: 500,
                              }}
                            >
                              <Eye size={13} />
                            </Link>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Cancellation Confirmation Modal */}
      {jobToCancel && (
        <div
          style={{
            position: 'fixed',
            inset: 0,
            backgroundColor: 'rgba(0, 0, 0, 0.5)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 50,
            padding: '1rem',
          }}
        >
          <div
            style={{
              backgroundColor: '#ffffff',
              borderRadius: '0.75rem',
              maxWidth: '440px',
              width: '100%',
              padding: '1.75rem',
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1rem' }}>
              <div
                style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '50%',
                  backgroundColor: '#fee2e2',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: '#dc2626',
                  flexShrink: 0,
                }}
              >
                <XCircle size={24} />
              </div>
              <div>
                <h3 style={{ margin: 0, fontSize: '1.125rem', fontWeight: 700, color: '#111827' }}>
                  Confirm Job Cancellation
                </h3>
                <span style={{ fontSize: '0.8125rem', color: '#6b7280' }}>
                  Action cannot be undone
                </span>
              </div>
            </div>

            <p style={{ margin: '0 0 1.5rem 0', color: '#4b5563', fontSize: '0.875rem', lineHeight: '1.4' }}>
              Are you sure you want to cancel{' '}
              <strong>"{jobToCancel.name}"</strong> (
              <span style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{jobToCancel.id}</span>)?
              If currently running, its worker execution will be interrupted immediately.
            </p>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                onClick={() => setJobToCancel(null)}
                style={{
                  padding: '0.5rem 1rem',
                  backgroundColor: '#ffffff',
                  color: '#4b5563',
                  border: '1px solid #d1d5db',
                  borderRadius: '0.375rem',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: 'pointer',
                }}
              >
                Keep Job
              </button>
              <button
                onClick={handleConfirmCancel}
                style={{
                  padding: '0.5rem 1rem',
                  backgroundColor: '#dc2626',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '0.375rem',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: 'pointer',
                }}
              >
                Yes, Cancel Job
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
