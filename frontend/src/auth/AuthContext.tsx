import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useNavigate } from "react-router-dom";
import {
  api,
  getStoredToken,
  setStoredToken,
  setUnauthorizedHandler,
} from "../api/client";
import type { LoginResponse, Role } from "../types/api";

interface AuthState {
  email: string;
  role: Role;
  token: string;
}

interface AuthContextValue {
  auth: AuthState | null;
  login: (email: string, password: string) => Promise<LoginResponse>;
  logout: () => void;
  isAuthenticated: boolean;
}

const STORAGE_AUTH_KEY = "jobportal.auth";

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

function loadFromStorage(): AuthState | null {
  const token = getStoredToken();
  const raw = localStorage.getItem(STORAGE_AUTH_KEY);
  if (!token || !raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Omit<AuthState, "token">;
    return { ...parsed, token };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(() => loadFromStorage());
  const navigate = useNavigate();

  const logout = useMemo(
    () => () => {
      setStoredToken(null);
      localStorage.removeItem(STORAGE_AUTH_KEY);
      setAuth(null);
      navigate("/login");
    },
    [navigate]
  );

  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  const login = async (email: string, password: string): Promise<LoginResponse> => {
    const response = await api.post<LoginResponse>("/auth/login", { email, password });
    const { token, role, email: respEmail } = response.data;
    setStoredToken(token);
    const newAuth: AuthState = { email: respEmail, role, token };
    localStorage.setItem(STORAGE_AUTH_KEY, JSON.stringify({ email: respEmail, role }));
    setAuth(newAuth);
    return response.data;
  };

  const value: AuthContextValue = {
    auth,
    login,
    logout,
    isAuthenticated: auth !== null,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}
