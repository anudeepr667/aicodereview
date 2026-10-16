import { useEffect, useState } from "react";
import { getCsrfToken, getCurrentUser, login as loginRequest, logout as logoutRequest, register as registerRequest } from "../services/api";
import { AuthContext } from "./authContext";

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    function handleExpiredSession() {
      setUser(null);
    }

    window.addEventListener("auth:expired", handleExpiredSession);
    async function restoreSession() {
      try {
        await getCsrfToken();
        setUser(await getCurrentUser());
      } catch {
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    }
    restoreSession();
    return () => window.removeEventListener("auth:expired", handleExpiredSession);
  }, []);

  async function login(credentials) {
    await getCsrfToken();
    const authenticatedUser = await loginRequest(credentials);
    setUser(authenticatedUser);
    return authenticatedUser;
  }

  async function register(credentials) {
    await getCsrfToken();
    return registerRequest(credentials);
  }

  async function logout() {
    try {
      await logoutRequest();
    } finally {
      setUser(null);
    }
  }

  return <AuthContext.Provider value={{ user, isLoading, login, register, logout }}>{children}</AuthContext.Provider>;
}
