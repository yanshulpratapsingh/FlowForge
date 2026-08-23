export type JobStatus =
  | 'CREATED'
  | 'QUEUED'
  | 'RUNNING'
  | 'RETRYING'
  | 'COMPLETED'
  | 'DEAD_LETTER'
  | 'CANCELLED';

export interface Job {
  id: string;
  name: string;
  payload: string | null;
  priority: number;
  status: JobStatus;
  retryCount: number;
  maxRetries: number;
  createdAt: string;
  updatedAt: string;
  scheduledAt: string | null;
  workerId: string | null;
  leaseUntil: string | null;
  startedAt: string | null;
  lastErrorMessage: string | null;
  lastFailedAt: string | null;
  type: string;
}

export interface CreateJobRequest {
  name: string;
  payload?: string;
  priority: number;
  maxRetries: number;
  type?: string;
  scheduledAt?: string | null;
}

export interface JobMetricsResponse {
  totalJobs: number;
  statusCounts: Record<JobStatus, number>;
  runningJobs: number;
  queueDepth: number;
  retryingJobs: number;
  completedJobs: number;
  deadLetterJobs: number;
  cancelledJobs: number;
  averageExecutionDurationMs: number;
  maxExecutionDurationMs: number;
  activeWorkers: number;
  recentExecutions: number;
  recentFailures: number;
  recentCancellations: number;
}

export interface WorkloadAnalyticsResponse {
  totalJobs: number;
  successRate: number;
  failureRate: number;
  retryRate: number;
  statusCounts: Record<JobStatus, number>;
}

export interface JobTypeAnalyticsResponse {
  type: string;
  totalJobs: number;
  statusCounts: Record<JobStatus, number>;
  completedCount: number;
  failedCount: number;
  retryingCount: number;
  cancelledCount: number;
  averageDurationMs: number;
  maxDurationMs: number;
  recentExecutions: number;
  recentFailures: number;
  recentCancellations: number;
}

export interface WorkerStats {
  workerId: string;
  runningJobsCount: number;
  runningJobIds: string[];
}

export interface WorkerAnalyticsResponse {
  activeWorkerCount: number;
  activeWorkers: WorkerStats[];
}

export interface QueueAnalyticsResponse {
  queueDepth: number;
  runningCount: number;
  retryingCount: number;
  oldestQueuedJobAgeSeconds: number;
  oldestRetryingJobAgeSeconds: number;
}

export interface ApiErrorDetail {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  validationErrors?: Record<string, string> | null;
}

export class ApiError extends Error {
  public status: number;
  public details: ApiErrorDetail;

  constructor(status: number, details: ApiErrorDetail) {
    super(details.message || 'API Request Failed');
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}
