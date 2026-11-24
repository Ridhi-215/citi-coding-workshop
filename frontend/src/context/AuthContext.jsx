import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { setSessionExpiredHandler, tokenStore } from '../services/apiClient';
import * as authService from '../services/authService';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(() => Boolean(tokenStore.get()));
  // True when the user was signed out because their session ran out (shown on the login page).
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    setSessionExpiredHandler(() => {
      setUser((current) => {
        if (current) setSessionExpired(true);
        return null;
      });
    });
    if (!tokenStore.get()) return;
    authService.fetchMe()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setLoading(false));
  }, []);

  const startSession = useCallback((u) => {
    setSessionExpired(false);
    setUser(u);
    return u;
  }, []);

  const login = useCallback(
    async (email, password) => startSession(await authService.login(email, password)), [startSession],
  );

  const register = useCallback(async (fields) => startSession(await authService.register(fields)), [startSession]);

  const logout = useCallback(() => {
    authService.logout();
    setSessionExpired(false);
    setUser(null);
  }, []);

  const can = useCallback((permission) => Boolean(user?.permissions?.includes(permission)), [user]);

  const value = useMemo(
    () => ({ user, loading, sessionExpired, login, register, logout, can }),
    [user, loading, sessionExpired, login, register, logout, can],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
