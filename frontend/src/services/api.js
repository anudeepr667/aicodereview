import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: "XSRF-TOKEN-IGNORED",
  xsrfHeaderName: "X-XSRF-TOKEN",
});

api.interceptors.response.use(undefined, (error) => {
  if (error.response?.status === 401 && !error.config?.url?.includes("/api/auth/")) {
    window.dispatchEvent(new Event("auth:expired"));
  }
  return Promise.reject(error);
});

export async function getCsrfToken() {
  const response = await api.get("/api/auth/csrf");
  api.defaults.headers.common["X-XSRF-TOKEN"] = response.data.token;
  return response.data;
}

export async function register(data) {
  const response = await api.post("/api/auth/register", data);
  return response.data;
}

export async function login(data) {
  const response = await api.post("/api/auth/login", data);
  return response.data;
}

export async function logout() {
  const response = await api.post("/api/auth/logout");
  return response.data;
}

export async function getCurrentUser() {
  const response = await api.get("/api/auth/me");
  return response.data;
}

export async function createReview(data) {
  const response = await api.post("/review", data);
  return response.data;
}

export async function getReviews() {
  const response = await api.get("/reviews");
  return response.data;
}
