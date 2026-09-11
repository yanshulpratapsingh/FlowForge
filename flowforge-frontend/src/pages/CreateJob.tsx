import { useState } from 'react';
import type { FormEvent, ChangeEvent } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowLeft,
  CheckCircle2,
  PlusCircle,
  Eye,
  AlertCircle,
  Calendar,
  Layers,
  Send,
} from 'lucide-react';
import { jobService } from '../api/services';
import { ApiError } from '../types';
import type { CreateJobRequest, Job } from '../types';

interface FormData {
  name: string;
  type: string;
  priority: string;
  maxRetries: string;
  payload: string;
  scheduledAt: string;
}

const INITIAL_FORM_DATA: FormData = {
  name: '',
  type: 'SIMULATED',
  priority: '0',
  maxRetries: '3',
  payload: '',
  scheduledAt: '',
};

export default function CreateJob() {
  const [formData, setFormData] = useState<FormData>(INITIAL_FORM_DATA);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [createdJob, setCreatedJob] = useState<Job | null>(null);

  const handleInputChange = (
    e: ChangeEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>
  ) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));

    // Clear field-level error on edit
    if (fieldErrors[name]) {
      setFieldErrors((prev) => {
        const updated = { ...prev };
        delete updated[name];
        return updated;
      });
    }
  };

  const validate = (): boolean => {
    const errors: Record<string, string> = {};

    // Name validation
    const trimmedName = formData.name.trim();
    if (!trimmedName) {
      errors.name = 'Job name is required';
    } else if (trimmedName.length > 255) {
      errors.name = 'Job name cannot exceed 255 characters';
    }

    // Priority validation
    if (formData.priority.trim() === '') {
      errors.priority = 'Priority is required';
    } else {
      const priorityNum = Number(formData.priority);
      if (isNaN(priorityNum) || !Number.isInteger(priorityNum)) {
        errors.priority = 'Priority must be an integer';
      } else if (priorityNum < 0) {
        errors.priority = 'Priority must be 0 or positive';
      }
    }

    // Max Retries validation
    if (formData.maxRetries.trim() === '') {
      errors.maxRetries = 'Max retries is required';
    } else {
      const retriesNum = Number(formData.maxRetries);
      if (isNaN(retriesNum) || !Number.isInteger(retriesNum)) {
        errors.maxRetries = 'Max retries must be an integer';
      } else if (retriesNum < 0) {
        errors.maxRetries = 'Max retries must be 0 or positive';
      }
    }

    // Type validation
    if (formData.type.trim().length > 100) {
      errors.type = 'Job type cannot exceed 100 characters';
    }

    // Payload validation
    if (formData.payload.length > 10000) {
      errors.payload = 'Payload cannot exceed 10,000 characters';
    }

    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setGlobalError(null);

    if (!validate()) {
      return;
    }

    setIsSubmitting(true);

    try {
      const requestPayload: CreateJobRequest = {
        name: formData.name.trim(),
        priority: parseInt(formData.priority, 10),
        maxRetries: parseInt(formData.maxRetries, 10),
      };

      if (formData.type.trim()) {
        requestPayload.type = formData.type.trim().toUpperCase();
      }

      if (formData.payload.trim()) {
        requestPayload.payload = formData.payload.trim();
      }

      if (formData.scheduledAt.trim()) {
        requestPayload.scheduledAt = formData.scheduledAt.trim();
      }

      const result = await jobService.createJob(requestPayload);
      setCreatedJob(result);
    } catch (err: unknown) {
      if (err instanceof ApiError) {
        if (err.details?.validationErrors) {
          setFieldErrors(err.details.validationErrors);
        }
        setGlobalError(err.details?.message || err.message);
      } else if (err instanceof Error) {
        setGlobalError(err.message);
      } else {
        setGlobalError('An unexpected error occurred while creating the job.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleResetForm = () => {
    setFormData(INITIAL_FORM_DATA);
    setFieldErrors({});
    setGlobalError(null);
    setCreatedJob(null);
  };

  if (createdJob) {
    return (
      <div style={{ maxWidth: '720px', margin: '0 auto' }}>
        {/* Success Card */}
        <div
          style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.75rem',
            border: '1px solid #e5e7eb',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
            padding: '2.5rem',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '1rem',
              marginBottom: '1.5rem',
            }}
          >
            <div
              style={{
                width: '48px',
                height: '48px',
                borderRadius: '50%',
                backgroundColor: '#ecfdf5',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: '#10b981',
                flexShrink: 0,
              }}
            >
              <CheckCircle2 size={28} />
            </div>
            <div>
              <h2
                style={{
                  fontSize: '1.5rem',
                  fontWeight: 700,
                  color: '#111827',
                  margin: 0,
                }}
              >
                Job Created Successfully
              </h2>
              <p style={{ margin: '0.25rem 0 0 0', color: '#6b7280', fontSize: '0.875rem' }}>
                The job has been registered in the FlowForge database in{' '}
                <span
                  style={{
                    backgroundColor: '#e5e7eb',
                    color: '#374151',
                    padding: '0.15rem 0.4rem',
                    borderRadius: '0.25rem',
                    fontWeight: 600,
                    fontSize: '0.75rem',
                  }}
                >
                  {createdJob.status}
                </span>{' '}
                state.
              </p>
            </div>
          </div>

          {/* Job Details Summary Box */}
          <div
            style={{
              backgroundColor: '#f9fafb',
              borderRadius: '0.5rem',
              border: '1px solid #e5e7eb',
              padding: '1.5rem',
              marginBottom: '2rem',
            }}
          >
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
                gap: '1.25rem',
              }}
            >
              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Job ID
                </div>
                <div
                  style={{
                    fontFamily: 'monospace',
                    fontSize: '0.85rem',
                    fontWeight: 600,
                    color: '#1f2937',
                    marginTop: '0.25rem',
                    wordBreak: 'break-all',
                  }}
                >
                  {createdJob.id}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Name
                </div>
                <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#111827', marginTop: '0.25rem' }}>
                  {createdJob.name}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Type
                </div>
                <div style={{ marginTop: '0.25rem' }}>
                  <span
                    style={{
                      backgroundColor: '#dbeafe',
                      color: '#1e40af',
                      padding: '0.2rem 0.5rem',
                      borderRadius: '0.25rem',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                    }}
                  >
                    {createdJob.type}
                  </span>
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Priority
                </div>
                <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#111827', marginTop: '0.25rem' }}>
                  {createdJob.priority}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Max Retries
                </div>
                <div style={{ fontSize: '0.95rem', fontWeight: 600, color: '#111827', marginTop: '0.25rem' }}>
                  {createdJob.maxRetries}
                </div>
              </div>

              <div>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                  Scheduled At
                </div>
                <div style={{ fontSize: '0.85rem', color: '#4b5563', marginTop: '0.25rem' }}>
                  {createdJob.scheduledAt ? new Date(createdJob.scheduledAt).toLocaleString() : 'Immediate (Upon Queue)'}
                </div>
              </div>
            </div>

            {createdJob.payload && (
              <div style={{ marginTop: '1.25rem', paddingTop: '1.25rem', borderTop: '1px solid #e5e7eb' }}>
                <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#6b7280', textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.4rem' }}>
                  Payload
                </div>
                <pre
                  style={{
                    backgroundColor: '#ffffff',
                    border: '1px solid #e5e7eb',
                    borderRadius: '0.375rem',
                    padding: '0.75rem',
                    fontSize: '0.8rem',
                    color: '#374151',
                    overflowX: 'auto',
                    margin: 0,
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all',
                    maxHeight: '160px',
                  }}
                >
                  {createdJob.payload}
                </pre>
              </div>
            )}
          </div>

          {/* Action Buttons */}
          <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap' }}>
            <Link
              to={`/jobs/${createdJob.id}`}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                padding: '0.625rem 1.25rem',
                backgroundColor: '#2563eb',
                color: '#ffffff',
                borderRadius: '0.375rem',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.875rem',
                boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
              }}
            >
              <Eye size={16} />
              View Job Details
            </Link>

            <button
              onClick={handleResetForm}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                padding: '0.625rem 1.25rem',
                backgroundColor: '#ffffff',
                color: '#374151',
                border: '1px solid #d1d5db',
                borderRadius: '0.375rem',
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: 'pointer',
                boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
              }}
            >
              <PlusCircle size={16} />
              Create Another Job
            </button>

            <Link
              to="/jobs"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                padding: '0.625rem 1.25rem',
                backgroundColor: '#f3f4f6',
                color: '#4b5563',
                borderRadius: '0.375rem',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.875rem',
              }}
            >
              Back to Jobs List
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: '720px', margin: '0 auto' }}>
      {/* Back Link */}
      <div style={{ marginBottom: '1.25rem' }}>
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
      </div>

      {/* Main Card */}
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '0.75rem',
          border: '1px solid #e5e7eb',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
          padding: '2rem 2.5rem',
        }}
      >
        <div style={{ marginBottom: '1.75rem', borderBottom: '1px solid #f3f4f6', paddingBottom: '1rem' }}>
          <h2 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#111827', margin: '0 0 0.35rem 0' }}>
            Create New Job
          </h2>
          <p style={{ color: '#6b7280', fontSize: '0.875rem', margin: '0 0 1rem 0' }}>
            Configure and register a new execution workload with priority, retry policy, and optional payload.
          </p>
        </div>

        {/* Global Error Banner */}
        {globalError && (
          <div
            style={{
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.75rem',
              padding: '1rem',
              backgroundColor: '#fef2f2',
              border: '1px solid #fca5a5',
              borderRadius: '0.5rem',
              marginBottom: '1.5rem',
            }}
          >
            <AlertCircle size={20} color="#dc2626" style={{ flexShrink: 0, marginTop: '0.1rem' }} />
            <div>
              <div style={{ fontWeight: 600, color: '#991b1b', fontSize: '0.875rem' }}>
                Failed to create job
              </div>
              <div style={{ color: '#7f1d1d', fontSize: '0.8125rem', marginTop: '0.25rem' }}>
                {globalError}
              </div>
            </div>
          </div>
        )}

        <form onSubmit={handleSubmit} noValidate>
          {/* Job Name */}
          <div style={{ marginBottom: '1.25rem' }}>
            <label
              htmlFor="name"
              style={{
                display: 'block',
                fontSize: '0.875rem',
                fontWeight: 600,
                color: '#374151',
                marginBottom: '0.4rem',
              }}
            >
              Job Name <span style={{ color: '#ef4444' }}>*</span>
            </label>
            <input
              id="name"
              name="name"
              type="text"
              value={formData.name}
              onChange={handleInputChange}
              placeholder="e.g. Daily Data Ingestion"
              disabled={isSubmitting}
              maxLength={255}
              style={{
                width: '100%',
                padding: '0.625rem 0.875rem',
                border: `1px solid ${fieldErrors.name ? '#ef4444' : '#d1d5db'}`,
                borderRadius: '0.375rem',
                fontSize: '0.875rem',
                color: '#111827',
                outline: 'none',
                backgroundColor: isSubmitting ? '#f9fafb' : '#ffffff',
                boxSizing: 'border-box',
              }}
            />
            {fieldErrors.name ? (
              <p style={{ color: '#dc2626', fontSize: '0.75rem', marginTop: '0.35rem', margin: '0.35rem 0 0 0' }}>
                {fieldErrors.name}
              </p>
            ) : (
              <p style={{ color: '#9ca3af', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                A descriptive title for this job (max 255 chars).
              </p>
            )}
          </div>

          {/* Job Type & Scheduling Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: '1.25rem',
              marginBottom: '1.25rem',
            }}
          >
            {/* Job Type */}
            <div>
              <label
                htmlFor="type"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: '#374151',
                  marginBottom: '0.4rem',
                }}
              >
                <Layers size={15} color="#6b7280" />
                Job Type
              </label>
              <input
                id="type"
                name="type"
                type="text"
                value={formData.type}
                onChange={handleInputChange}
                list="job-type-suggestions"
                placeholder="SIMULATED"
                disabled={isSubmitting}
                maxLength={100}
                style={{
                  width: '100%',
                  padding: '0.625rem 0.875rem',
                  border: `1px solid ${fieldErrors.type ? '#ef4444' : '#d1d5db'}`,
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  color: '#111827',
                  outline: 'none',
                  backgroundColor: isSubmitting ? '#f9fafb' : '#ffffff',
                  boxSizing: 'border-box',
                }}
              />
              <datalist id="job-type-suggestions">
                <option value="SIMULATED" />
                <option value="DATA_SYNC" />
                <option value="EMAIL_NOTIFICATION" />
                <option value="REPORT_GENERATION" />
                <option value="CLEANUP" />
              </datalist>
              {fieldErrors.type ? (
                <p style={{ color: '#dc2626', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  {fieldErrors.type}
                </p>
              ) : (
                <p style={{ color: '#9ca3af', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  Used for concurrency and rate limits (defaults to SIMULATED).
                </p>
              )}
            </div>

            {/* Scheduled At */}
            <div>
              <label
                htmlFor="scheduledAt"
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.35rem',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: '#374151',
                  marginBottom: '0.4rem',
                }}
              >
                <Calendar size={15} color="#6b7280" />
                Scheduled Execution (Optional)
              </label>
              <input
                id="scheduledAt"
                name="scheduledAt"
                type="datetime-local"
                value={formData.scheduledAt}
                onChange={handleInputChange}
                disabled={isSubmitting}
                style={{
                  width: '100%',
                  padding: '0.625rem 0.875rem',
                  border: `1px solid ${fieldErrors.scheduledAt ? '#ef4444' : '#d1d5db'}`,
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  color: '#111827',
                  outline: 'none',
                  backgroundColor: isSubmitting ? '#f9fafb' : '#ffffff',
                  boxSizing: 'border-box',
                }}
              />
              {fieldErrors.scheduledAt ? (
                <p style={{ color: '#dc2626', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  {fieldErrors.scheduledAt}
                </p>
              ) : (
                <p style={{ color: '#9ca3af', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  Leave blank for immediate eligibility once queued.
                </p>
              )}
            </div>
          </div>

          {/* Priority & Max Retries Grid */}
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
              gap: '1.25rem',
              marginBottom: '1.25rem',
            }}
          >
            {/* Priority */}
            <div>
              <label
                htmlFor="priority"
                style={{
                  display: 'block',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: '#374151',
                  marginBottom: '0.4rem',
                }}
              >
                Priority <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <input
                id="priority"
                name="priority"
                type="number"
                min="0"
                step="1"
                value={formData.priority}
                onChange={handleInputChange}
                disabled={isSubmitting}
                style={{
                  width: '100%',
                  padding: '0.625rem 0.875rem',
                  border: `1px solid ${fieldErrors.priority ? '#ef4444' : '#d1d5db'}`,
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  color: '#111827',
                  outline: 'none',
                  backgroundColor: isSubmitting ? '#f9fafb' : '#ffffff',
                  boxSizing: 'border-box',
                }}
              />
              {fieldErrors.priority ? (
                <p style={{ color: '#dc2626', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  {fieldErrors.priority}
                </p>
              ) : (
                <p style={{ color: '#9ca3af', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  Higher priority jobs are dispatched first (0 = default).
                </p>
              )}
            </div>

            {/* Max Retries */}
            <div>
              <label
                htmlFor="maxRetries"
                style={{
                  display: 'block',
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: '#374151',
                  marginBottom: '0.4rem',
                }}
              >
                Max Retries <span style={{ color: '#ef4444' }}>*</span>
              </label>
              <input
                id="maxRetries"
                name="maxRetries"
                type="number"
                min="0"
                step="1"
                value={formData.maxRetries}
                onChange={handleInputChange}
                disabled={isSubmitting}
                style={{
                  width: '100%',
                  padding: '0.625rem 0.875rem',
                  border: `1px solid ${fieldErrors.maxRetries ? '#ef4444' : '#d1d5db'}`,
                  borderRadius: '0.375rem',
                  fontSize: '0.875rem',
                  color: '#111827',
                  outline: 'none',
                  backgroundColor: isSubmitting ? '#f9fafb' : '#ffffff',
                  boxSizing: 'border-box',
                }}
              />
              {fieldErrors.maxRetries ? (
                <p style={{ color: '#dc2626', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  {fieldErrors.maxRetries}
                </p>
              ) : (
                <p style={{ color: '#9ca3af', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                  Attempts with exponential backoff before Dead-Letter Queue.
                </p>
              )}
            </div>
          </div>

          {/* Payload */}
          <div style={{ marginBottom: '1.75rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
              <label
                htmlFor="payload"
                style={{
                  fontSize: '0.875rem',
                  fontWeight: 600,
                  color: '#374151',
                }}
              >
                Job Payload (Optional)
              </label>
              <span style={{ fontSize: '0.75rem', color: formData.payload.length > 10000 ? '#ef4444' : '#9ca3af' }}>
                {formData.payload.length} / 10,000 chars
              </span>
            </div>
            <textarea
              id="payload"
              name="payload"
              rows={4}
              value={formData.payload}
              onChange={handleInputChange}
              placeholder='e.g. {"targetUrl": "https://api.example.com", "syncMode": "full"}'
              disabled={isSubmitting}
              maxLength={10000}
              style={{
                width: '100%',
                padding: '0.625rem 0.875rem',
                border: `1px solid ${fieldErrors.payload ? '#ef4444' : '#d1d5db'}`,
                borderRadius: '0.375rem',
                fontSize: '0.875rem',
                fontFamily: 'monospace',
                color: '#111827',
                outline: 'none',
                backgroundColor: isSubmitting ? '#f9fafb' : '#ffffff',
                boxSizing: 'border-box',
                resize: 'vertical',
              }}
            />
            {fieldErrors.payload ? (
              <p style={{ color: '#dc2626', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                {fieldErrors.payload}
              </p>
            ) : (
              <p style={{ color: '#9ca3af', fontSize: '0.75rem', margin: '0.35rem 0 0 0' }}>
                Arbitrary string or JSON payload to be passed to worker during execution.
              </p>
            )}
          </div>

          {/* Form Actions */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: '1rem',
              paddingTop: '1.25rem',
              borderTop: '1px solid #f3f4f6',
            }}
          >
            <Link
              to="/jobs"
              style={{
                padding: '0.625rem 1.25rem',
                backgroundColor: '#ffffff',
                color: '#4b5563',
                border: '1px solid #d1d5db',
                borderRadius: '0.375rem',
                textDecoration: 'none',
                fontWeight: 600,
                fontSize: '0.875rem',
              }}
            >
              Cancel
            </Link>

            <button
              type="submit"
              disabled={isSubmitting}
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '0.5rem',
                padding: '0.625rem 1.5rem',
                backgroundColor: isSubmitting ? '#93c5fd' : '#2563eb',
                color: '#ffffff',
                border: 'none',
                borderRadius: '0.375rem',
                fontWeight: 600,
                fontSize: '0.875rem',
                cursor: isSubmitting ? 'not-allowed' : 'pointer',
                boxShadow: '0 1px 2px rgba(0, 0, 0, 0.05)',
                transition: 'background-color 0.15s ease',
              }}
            >
              {isSubmitting ? (
                <>
                  <div
                    style={{
                      width: '14px',
                      height: '14px',
                      border: '2px solid rgba(255, 255, 255, 0.4)',
                      borderTop: '2px solid #ffffff',
                      borderRadius: '50%',
                      animation: 'spin 1s linear infinite',
                    }}
                  />
                  Creating...
                  <style>{`
                    @keyframes spin {
                      0% { transform: rotate(0deg); }
                      100% { transform: rotate(360deg); }
                    }
                  `}</style>
                </>
              ) : (
                <>
                  <Send size={15} />
                  Create Job
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
