import { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  RefreshCw,
  Play,
  XCircle,
  Clock,
  Calendar,
  Layers,
  AlertCircle,
  CheckCircle2,
  Cpu,
  Copy,
  Check,
  FileCode,
} from 'lucide-react';
import { jobService } from '../api/services';
import { ApiError } from '../types';
import type { Job, JobStatus } from '../types';
import { LoadingState, ErrorState } from '../components/SharedStates';

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

// Date formatter helper
function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—';
  try {
    const d = new Date(dateStr);
    return d.toLocaleString([], {
      year: 'numeric',
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

export default function JobDetails() {
  const { id } = useParams<{ id: string }>();

  const [job, setJob] = useState<Job | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // Action feedback message
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  // Cancellation modal & action progress
  const [isActionLoading, setIsActionLoading] = useState<boolean>(false);
  const [showCancelModal, setShowCancelModal] = useState<boolean>(false);
  const [copiedId, setCopiedId] = useState<boolean>(false);

  const fetchJob = useCallback(
    async (silent = false) => {
      if (!id) return;
      if (silent) {
        setIsRefreshing(true);
      } else {
        setIsLoading(true);
      }
      setError(null);

      try {
        const data = await jobService.getJob(id);
        setJob(data);
      } catch (err: unknown) {
        if (err instanceof ApiError) {
          if (err.status === 404) {
            setError(`Job with ID "${id}" was not found.`);
          } else {
            setError(err.details?.message || err.message);
          }
        } else if (err instanceof Error) {
          setError(err.message);
        } else {
          setError('Failed to load job details from the server.');
        }
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [id]
  );

  useEffect(() => {
    const timer = setTimeout(() => {
      fetchJob();
    }, 0);
    return () => clearTimeout(timer);
  }, [fetchJob]);

  // Copy Job ID to clipboard
  const handleCopyId = () => {
    if (!job) return;
    navigator.clipboard.writeText(job.id);
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 2000);
  };

  // Queue Job Action
  const handleQueueJob = async () => {
    if (!job) return;
    setIsActionLoading(true);
    setFeedback(null);

    try {
      const updated = await jobService.queueJob(job.id);
      setJob(updated);
      setFeedback({
        type: 'success',
        message: `Job "${job.name}" has been transitioned to QUEUED state.`,
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
      setIsActionLoading(false);
    }
  };

  // Cancel Job Action
  const handleConfirmCancel = async () => {
    if (!job) return;
    setShowCancelModal(false);
    setIsActionLoading(true);
    setFeedback(null);

    try {
      const updated = await jobService.cancelJob(job.id);
      setJob(updated);
      setFeedback({
        type: 'success',
        message: `Job "${job.name}" has been cancelled.`,
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
      setIsActionLoading(false);
    }
  };

  if (isLoading) {
    return <LoadingState message="Loading job diagnostics..." />;
  }

  if (error || !job) {
    return (
      <div style={{ maxWidth: '800px', margin: '0 auto' }}>
        <div style={{ marginBottom: '1.25rem' }}>
          <Link
            to="/jobs"
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              color: '#2563eb',
              fontSize: '0.875rem',
              textDecoration: 'none',
              fontWeight: 500,
            }}
          >
            <ArrowLeft size={16} />
            Back to Jobs
          </Link>
        </div>
        <ErrorState
          title="Job Not Available"
          message={error || `Job with ID "${id}" could not be found.`}
          onRetry={() => fetchJob(false)}
        />
      </div>
    );
  }

  const statusStyle = getStatusBadgeStyle(job.status);
  const canQueue = job.status === 'CREATED';
  const canCancel =
    job.status === 'QUEUED' || job.status === 'RUNNING' || job.status === 'RETRYING';

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto', display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
      {/* Top Navigation Row */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <Link
          to="/jobs"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.4rem',
            color: '#4b5563',
            fontSize: '0.875rem',
            textDecoration: 'none',
            fontWeight: 500,
          }}
        >
          <ArrowLeft size={16} />
          Back to Jobs
        </Link>

        {/* Action Buttons */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <button
            onClick={() => fetchJob(true)}
            disabled={isRefreshing || isActionLoading}
            style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '0.4rem',
              padding: '0.5rem 0.875rem',
              backgroundColor: '#ffffff',
              color: '#374151',
              border: '1px solid #d1d5db',
              borderRadius: '0.375rem',
              cursor: isRefreshing || isActionLoading ? 'not-allowed' : 'pointer',
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

          {canQueue && (
            <button
              onClick={handleQueueJob}
              disabled={isActionLoading}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                padding: '0.5rem 1rem',
                backgroundColor: isActionLoading ? '#93c5fd' : '#2563eb',
                color: '#ffffff',
                border: 'none',
                borderRadius: '0.375rem',
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: isActionLoading ? 'not-allowed' : 'pointer',
                boxShadow: '0 1px 2px rgba(0,0,0,0.05)',
              }}
            >
              <Play size={15} />
              Queue Job
            </button>
          )}

          {canCancel && (
            <button
              onClick={() => setShowCancelModal(true)}
              disabled={isActionLoading}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.4rem',
                padding: '0.5rem 1rem',
                backgroundColor: '#ffffff',
                color: '#dc2626',
                border: '1px solid #fca5a5',
                borderRadius: '0.375rem',
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: isActionLoading ? 'not-allowed' : 'pointer',
              }}
            >
              <XCircle size={15} />
              Cancel Job
            </button>
          )}
        </div>
      </div>

      {/* Action Feedback Banner */}
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

      {/* Error Message Alert (e.g. from failed execution) */}
      {job.lastErrorMessage && (
        <div
          style={{
            padding: '1.25rem',
            borderRadius: '0.5rem',
            backgroundColor: '#fef2f2',
            border: '1px solid #fecaca',
            color: '#991b1b',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.4rem' }}>
            <AlertCircle size={18} color="#dc2626" />
            <h4 style={{ margin: 0, fontSize: '0.95rem', fontWeight: 700 }}>Execution Failure Recorded</h4>
          </div>
          <p
            style={{
              margin: '0 0 0.5rem 0',
              fontSize: '0.875rem',
              fontFamily: 'monospace',
              backgroundColor: '#ffffff',
              padding: '0.5rem 0.75rem',
              borderRadius: '0.375rem',
              border: '1px solid #fee2e2',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
            }}
          >
            {job.lastErrorMessage}
          </p>
          {job.lastFailedAt && (
            <span style={{ fontSize: '0.75rem', color: '#b91c1c' }}>
              Last failure occurred at: {formatDate(job.lastFailedAt)}
            </span>
          )}
        </div>
      )}

      {/* Main Details Card Header */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          border: '1px solid #e5e7eb',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          padding: '1.75rem 2rem',
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '1rem' }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', flexWrap: 'wrap' }}>
              <h2 style={{ fontSize: '1.6rem', fontWeight: 700, color: '#111827', margin: 0 }}>
                {job.name}
              </h2>
              <span
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  padding: '0.2rem 0.6rem',
                  backgroundColor: '#eff6ff',
                  color: '#1d4ed8',
                  border: '1px solid #bfdbfe',
                  borderRadius: '0.25rem',
                  fontSize: '0.75rem',
                  fontWeight: 700,
                }}
              >
                <Layers size={12} />
                {job.type}
              </span>
              <span
                style={{
                  display: 'inline-block',
                  padding: '0.25rem 0.75rem',
                  backgroundColor: statusStyle.bg,
                  color: statusStyle.text,
                  border: `1px solid ${statusStyle.border}`,
                  borderRadius: '9999px',
                  fontSize: '0.75rem',
                  fontWeight: 700,
                  letterSpacing: '0.025em',
                }}
              >
                {job.status}
              </span>
            </div>

            {/* Job ID Row */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.6rem' }}>
              <span style={{ fontSize: '0.8rem', color: '#6b7280', fontWeight: 500 }}>ID:</span>
              <span style={{ fontFamily: 'monospace', fontSize: '0.85rem', color: '#1f2937', fontWeight: 600 }}>
                {job.id}
              </span>
              <button
                onClick={handleCopyId}
                title="Copy Job ID"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '0.25rem',
                  background: 'none',
                  border: 'none',
                  color: copiedId ? '#16a34a' : '#6b7280',
                  cursor: 'pointer',
                  padding: '0.15rem 0.35rem',
                  borderRadius: '0.25rem',
                  fontSize: '0.75rem',
                }}
              >
                {copiedId ? <Check size={14} /> : <Copy size={14} />}
                {copiedId ? 'Copied' : ''}
              </button>
            </div>
          </div>
        </div>

        {/* Configuration Grid */}
        <div
          style={{
            marginTop: '1.75rem',
            paddingTop: '1.5rem',
            borderTop: '1px solid #f3f4f6',
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
            gap: '1.25rem',
          }}
        >
          <div>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Priority
            </span>
            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: job.priority > 0 ? '#b45309' : '#111827', marginTop: '0.2rem' }}>
              {job.priority}
            </div>
          </div>

          <div>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Retry Attempts
            </span>
            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#111827', marginTop: '0.2rem' }}>
              {job.retryCount} <span style={{ fontSize: '0.875rem', fontWeight: 400, color: '#6b7280' }}>/ {job.maxRetries} max</span>
            </div>
          </div>

          <div>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Worker Node
            </span>
            <div style={{ fontSize: '0.875rem', fontWeight: 600, color: job.workerId ? '#1e40af' : '#6b7280', marginTop: '0.2rem' }}>
              {job.workerId ? (
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: '0.3rem' }}>
                  <Cpu size={14} />
                  {job.workerId}
                </span>
              ) : (
                'Unassigned'
              )}
            </div>
          </div>

          <div>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Scheduled Dispatch
            </span>
            <div style={{ fontSize: '0.875rem', fontWeight: 500, color: job.scheduledAt ? '#854d0e' : '#6b7280', marginTop: '0.2rem' }}>
              {job.scheduledAt ? formatDate(job.scheduledAt) : 'Immediate'}
            </div>
          </div>
        </div>
      </div>

      {/* Execution Timeline Card */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          border: '1px solid #e5e7eb',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          padding: '1.5rem 2rem',
        }}
      >
        <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#111827', margin: '0 0 1.25rem 0' }}>
          Lifecycle & Execution Timeline
        </h3>

        <div
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '1.25rem',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.6rem' }}>
            <Calendar size={18} color="#6b7280" style={{ marginTop: '0.15rem' }} />
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase' }}>
                Created At
              </div>
              <div style={{ fontSize: '0.875rem', fontWeight: 500, color: '#111827', marginTop: '0.15rem' }}>
                {formatDate(job.createdAt)}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.6rem' }}>
            <Clock size={18} color="#6b7280" style={{ marginTop: '0.15rem' }} />
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase' }}>
                Started At
              </div>
              <div style={{ fontSize: '0.875rem', fontWeight: 500, color: job.startedAt ? '#065f46' : '#6b7280', marginTop: '0.15rem' }}>
                {formatDate(job.startedAt)}
              </div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.6rem' }}>
            <Clock size={18} color="#6b7280" style={{ marginTop: '0.15rem' }} />
            <div>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase' }}>
                Last Updated
              </div>
              <div style={{ fontSize: '0.875rem', fontWeight: 500, color: '#111827', marginTop: '0.15rem' }}>
                {formatDate(job.updatedAt)}
              </div>
            </div>
          </div>

          {job.leaseUntil && (
            <div style={{ display: 'flex', alignItems: 'flex-start', gap: '0.6rem' }}>
              <Clock size={18} color="#d97706" style={{ marginTop: '0.15rem' }} />
              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase' }}>
                  Worker Lease Until
                </div>
                <div style={{ fontSize: '0.875rem', fontWeight: 500, color: '#b45309', marginTop: '0.15rem' }}>
                  {formatDate(job.leaseUntil)}
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Payload Card */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          border: '1px solid #e5e7eb',
          boxShadow: '0 1px 3px rgba(0,0,0,0.05)',
          padding: '1.5rem 2rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem' }}>
          <FileCode size={18} color="#374151" />
          <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: '#111827', margin: 0 }}>
            Job Payload Data
          </h3>
        </div>

        {job.payload ? (
          <pre
            style={{
              backgroundColor: '#f9fafb',
              border: '1px solid #e5e7eb',
              borderRadius: '0.5rem',
              padding: '1rem',
              fontFamily: 'monospace',
              fontSize: '0.85rem',
              color: '#374151',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
              margin: 0,
              maxHeight: '300px',
              overflowY: 'auto',
            }}
          >
            {job.payload}
          </pre>
        ) : (
          <p style={{ color: '#9ca3af', fontSize: '0.875rem', margin: 0, fontStyle: 'italic' }}>
            No payload attached to this job.
          </p>
        )}
      </div>

      {/* Cancellation Confirmation Modal */}
      {showCancelModal && (
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
              boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
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
                  Cancel This Job?
                </h3>
                <span style={{ fontSize: '0.8125rem', color: '#6b7280' }}>
                  This action is irreversible
                </span>
              </div>
            </div>

            <p style={{ margin: '0 0 1.5rem 0', color: '#4b5563', fontSize: '0.875rem', lineHeight: '1.4' }}>
              Are you sure you want to cancel <strong>"{job.name}"</strong>? If this job is currently being executed by an active worker, its thread execution will be interrupted immediately.
            </p>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                onClick={() => setShowCancelModal(false)}
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
