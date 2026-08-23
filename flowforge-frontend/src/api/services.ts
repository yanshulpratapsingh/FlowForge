import { request } from './client';
import type {
  Job,
  CreateJobRequest,
  JobMetricsResponse,
  WorkloadAnalyticsResponse,
  JobTypeAnalyticsResponse,
  WorkerAnalyticsResponse,
  QueueAnalyticsResponse,
} from '../types';

export const jobService = {
  createJob(req: CreateJobRequest): Promise<Job> {
    return request<Job>('/api/jobs', {
      method: 'POST',
      body: JSON.stringify(req),
    });
  },

  getJobs(): Promise<Job[]> {
    return request<Job[]>('/api/jobs', {
      method: 'GET',
    });
  },

  getJob(id: string): Promise<Job> {
    return request<Job>(`/api/jobs/${id}`, {
      method: 'GET',
    });
  },

  queueJob(id: string): Promise<Job> {
    return request<Job>(`/api/jobs/${id}/queue`, {
      method: 'PUT',
    });
  },

  cancelJob(id: string): Promise<Job> {
    return request<Job>(`/api/jobs/${id}/cancel`, {
      method: 'POST',
    });
  },
};

export const metricsService = {
  getJobMetrics(): Promise<JobMetricsResponse> {
    return request<JobMetricsResponse>('/api/metrics/jobs', {
      method: 'GET',
    });
  },
};

export const analyticsService = {
  getWorkloadAnalytics(): Promise<WorkloadAnalyticsResponse> {
    return request<WorkloadAnalyticsResponse>('/api/analytics/workload', {
      method: 'GET',
    });
  },

  getJobTypeAnalytics(): Promise<JobTypeAnalyticsResponse[]> {
    return request<JobTypeAnalyticsResponse[]>('/api/analytics/workload/types', {
      method: 'GET',
    });
  },

  getWorkerAnalytics(): Promise<WorkerAnalyticsResponse> {
    return request<WorkerAnalyticsResponse>('/api/analytics/workers', {
      method: 'GET',
    });
  },

  getQueueAnalytics(): Promise<QueueAnalyticsResponse> {
    return request<QueueAnalyticsResponse>('/api/analytics/queue', {
      method: 'GET',
    });
  },
};
