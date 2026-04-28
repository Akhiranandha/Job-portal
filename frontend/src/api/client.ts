import axios, { AxiosError } from "axios";
import type { ErrorResponse } from "../types/api";

const baseURL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const api = axios.create({
  baseURL,
});

const TOKEN_STORAGE_KEY = "jobportal.token";

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setStoredToken(token: string | null) {
  if (token) {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  }
}

api.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 401 handler is wired in AuthContext so it can call logout() and use the router.
let unauthorizedHandler: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  unauthorizedHandler = handler;
}

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => {
    if (error.response?.status === 401 && unauthorizedHandler) {
      unauthorizedHandler();
    }
    return Promise.reject(error);
  }
);

export function extractErrorMessage(err: unknown, fallback = "Something went wrong"): string {
  if (axios.isAxiosError<ErrorResponse>(err) && err.response?.data?.message) {
    return err.response.data.message;
  }
  if (err instanceof Error) {
    return err.message;
  }
  return fallback;
}

export function extractFieldErrors(err: unknown): Record<string, string> | undefined {
  if (axios.isAxiosError<ErrorResponse>(err) && err.response?.data?.fieldErrors) {
    return err.response.data.fieldErrors;
  }
  return undefined;
}
