import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../context/useAuth";
import { LoaderCircle } from "lucide-react";

function AuthLoading() {
  return <main className="auth-loading"><LoaderCircle className="spin" size={24} /> Checking your session...</main>;
}

export function ProtectedRoute({ children }) {
  const { user, isLoading } = useAuth();
  const location = useLocation();
  if (isLoading) return <AuthLoading />;
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />;
  return children;
}

export function PublicOnlyRoute({ children }) {
  const { user, isLoading } = useAuth();
  if (isLoading) return <AuthLoading />;
  if (user) return <Navigate to="/dashboard" replace />;
  return children;
}
