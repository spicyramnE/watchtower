"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { setToken } from "./api";
import type { Role } from "./types";

interface AuthState {
  token: string | null;
  username: string | null;
  role: Role | null;
}

interface AuthContextValue extends AuthState {
  isAuthenticated: boolean;
  login: (token: string, role: Role, username: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

const STORAGE_KEYS = {
  token: "watchtower_token",
  role: "watchtower_role",
  username: "watchtower_username",
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ token: null, username: null, role: null });
  const [hydrated, setHydrated] = useState(false);

  // Read persisted auth on first client render only - avoids a
  // server/client hydration mismatch since localStorage doesn't exist
  // during server rendering.
  useEffect(() => {
    const token = localStorage.getItem(STORAGE_KEYS.token);
    const role = localStorage.getItem(STORAGE_KEYS.role) as Role | null;
    const username = localStorage.getItem(STORAGE_KEYS.username);
    if (token && role && username) {
      // One-time hydration from localStorage after mount, to avoid a
      // server/client mismatch (localStorage doesn't exist during SSR) -
      // React's recommended pattern for this exact case, even though it's
      // a synchronous setState inside the effect.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setState({ token, role, username });
    }
    setHydrated(true);
  }, []);

  function login(token: string, role: Role, username: string) {
    localStorage.setItem(STORAGE_KEYS.token, token);
    localStorage.setItem(STORAGE_KEYS.role, role);
    localStorage.setItem(STORAGE_KEYS.username, username);
    setToken(token);
    setState({ token, role, username });
  }

  function logout() {
    localStorage.removeItem(STORAGE_KEYS.token);
    localStorage.removeItem(STORAGE_KEYS.role);
    localStorage.removeItem(STORAGE_KEYS.username);
    setToken(null);
    setState({ token: null, username: null, role: null });
  }

  if (!hydrated) {
    return null;
  }

  return (
    <AuthContext.Provider value={{ ...state, isAuthenticated: !!state.token, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
