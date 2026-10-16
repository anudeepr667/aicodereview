import { Eye, EyeOff, LogIn } from "lucide-react";
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";

function messageFromError(error, fallback) {
  if (!error.response) {
    return "Could not reach the login server. Check that the backend is running and try again.";
  }

  const responseData = error.response.data;
  if (typeof responseData === "string" && responseData.trim()) {
    return responseData;
  }

  if (responseData && typeof responseData === "object") {
    const message = responseData.message || responseData.error;
    if (typeof message === "string" && message.trim()) {
      return message;
    }
  }

  if (error.response.status === 401) {
    return "Invalid email or password. If you haven’t created an account yet, register first.";
  }
  if (error.response.status === 403) {
    return "The server rejected the login request (403). Refresh the page and try again.";
  }

  return `${fallback} (HTTP ${error.response.status}).`;
}

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [form, setForm] = useState({ email: "", password: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const successMessage = location.state?.message;

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setIsSubmitting(true);
    try {
      await login(form);
      const destination = location.state?.from?.pathname || "/dashboard";
      navigate(destination, { replace: true });
    } catch (requestError) {
      setError(messageFromError(requestError, "Unable to sign in with those credentials."));
    } finally {
      setIsSubmitting(false);
    }
  }

  return <main className="auth-page"><section className="auth-card"><div className="auth-heading"><p className="section-kicker"><span className="status-dot" /> Secure workspace</p><h1>Welcome back</h1><p>Sign in to review pull requests and access your saved analyses.</p></div>{successMessage && <div className="auth-success" role="status">{successMessage}</div>}{error && <div className="auth-error" role="alert">{error}</div>}<form className="auth-form" onSubmit={handleSubmit} noValidate><label htmlFor="login-email">Email<input id="login-email" type="email" autoComplete="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required /></label><label htmlFor="login-password">Password<div className="password-wrap"><input id="login-password" type={showPassword ? "text" : "password"} autoComplete="current-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} required /><button type="button" className="icon-button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div></label><button className="auth-submit" type="submit" disabled={isSubmitting}><LogIn size={17} />{isSubmitting ? "Signing in..." : "Sign in"}</button></form><p className="auth-switch">New to the workspace? <Link to="/register">Create an account</Link></p></section></main>;
}
