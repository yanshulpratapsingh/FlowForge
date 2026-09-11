import { ApiError } from '../types';
import type { ApiErrorDetail } from '../types';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';
const API_KEY = import.meta.env.VITE_API_KEY || '';

interface RequestOptions extends RequestInit {
  params?: Record<string, string>;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { params, headers, ...restOptions } = options;

  let url = `${BASE_URL}${path}`;
  if (params) {
    const searchParams = new URLSearchParams(params);
    url += `?${searchParams.toString()}`;
  }

  const defaultHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (API_KEY) {
    defaultHeaders['X-API-KEY'] = API_KEY;
  }

  let response: Response;
  try {
    response = await fetch(url, {
      headers: {
        ...defaultHeaders,
        ...headers,
      },
      ...restOptions,
    });
  } catch {
    const details: ApiErrorDetail = {
      timestamp: new Date().toISOString(),
      status: 0,
      error: 'NetworkError',
      message: 'Cannot connect to FlowForge backend. Check that the backend is running on port 8081 and CORS is configured.',
    };
    throw new ApiError(0, details);
  }

  if (!response.ok) {
    let details: ApiErrorDetail;
    try {
      details = await response.json();
    } catch {
      details = {
        timestamp: new Date().toISOString(),
        status: response.status,
        error: response.statusText,
        message: `HTTP error ${response.status}`,
      };
    }

    if (response.status === 401) {
      details.message = 'Authentication failed. Please verify your VITE_API_KEY.';
    }

    throw new ApiError(response.status, details);
  }

  // Handle empty or 204 No Content responses
  if (response.status === 204) {
    return {} as T;
  }

  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    return response.json() as Promise<T>;
  }

  return response.text() as unknown as Promise<T>;
}
