import { Eye, EyeOff, UserPlus } from "lucide-react";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/useAuth";

function messageFromError(error, fallback) {
  return error.response?.data?.message || fallback;
}

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: "", email: "", password: "", confirmation: "" });
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    if (form.password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    if (form.password !== form.confirmation) {
      setError("Passwords do not match.");
      return;
    }
    setIsSubmitting(true);
    try {
      await register({ name: form.name, email: form.email, password: form.password });
      navigate("/login", { replace: true, state: { message: "Account created. Sign in to continue." } });
    } catch (requestError) {
      setError(messageFromError(requestError, "Unable to create your account."));
    } finally {
      setIsSubmitting(false);
    }
  }

  return <main className="auth-page"><section className="auth-card"><div className="auth-heading"><p className="section-kicker"><span className="status-dot" /> Secure workspace</p><h1>Create your account</h1><p>Set up your workspace account to start reviewing pull requests.</p></div>{error && <div className="auth-error" role="alert">{error}</div>}<form className="auth-form" onSubmit={handleSubmit} noValidate><label htmlFor="register-name">Name<input id="register-name" type="text" autoComplete="name" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} required /></label><label htmlFor="register-email">Email<input id="register-email" type="email" autoComplete="email" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} required /></label><label htmlFor="register-password">Password<div className="password-wrap"><input id="register-password" type={showPassword ? "text" : "password"} autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} required minLength={8} /><button type="button" className="icon-button" onClick={() => setShowPassword(!showPassword)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div></label><label htmlFor="register-confirmation">Confirm password<input id="register-confirmation" type={showPassword ? "text" : "password"} autoComplete="new-password" value={form.confirmation} onChange={(event) => setForm({ ...form, confirmation: event.target.value })} required /></label><button className="auth-submit" type="submit" disabled={isSubmitting}><UserPlus size={17} />{isSubmitting ? "Creating account..." : "Create account"}</button></form><p className="auth-switch">Already have an account? <Link to="/login">Sign in</Link></p></section></main>;
}
