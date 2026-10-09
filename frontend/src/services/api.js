import axios from "axios";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

export async function createReview(data) {
  const response = await api.post("/review", data);
  return response.data;
}

export async function getReviews() {
  const response = await api.get("/reviews");
  return response.data;
}
