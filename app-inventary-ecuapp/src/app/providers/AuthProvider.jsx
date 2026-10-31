import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, getToken, getTokenExpiry, setToken } from '../../api.js';
import { toast } from '../../ui/feedback.js';
import { hasPermission } from './permissions.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setTokenState] = useState(() => getToken());
  const [user, setUser] = useState(null);
  const [apiStatus, setApiStatus] = useState(() => getToken() ? 'checking' : 'disconnected');
  const [authError, setAuthError] = useState('');

  const expireSession = useCallback((message = 'La sesión expiró o ya no es válida. Inicia sesión nuevamente.') => {
    setToken('');
    setTokenState('');
    setUser(null);
    setApiStatus('error');
    setAuthError('');
    toast.info(message);
  }, []);

  const login = useCallback(async ({ username, password, remember }) => {
    const result = await api.login(username, password);
    const accessToken = result?.accessToken;
    if (!accessToken) throw new Error('La respuesta de inicio de sesión no contiene accessToken.');
    setToken(accessToken, { persist: remember, expiresAt: result.expiresAt });
    setUser(null);
    setAuthError('');
    setTokenState(accessToken);
    setApiStatus('checking');
    return result;
  }, []);

  const logout = useCallback(async () => {
    let error = null;
    try { await api.logout(); } catch (cause) { error = cause; }
    finally {
      setToken('');
      setTokenState('');
      setUser(null);
      setApiStatus('disconnected');
      setAuthError('');
    }
    return error;
  }, []);

  useEffect(() => {
    if (!token) {
      setUser(null);
      setApiStatus('disconnected');
      return undefined;
    }
    let active = true;
    setApiStatus('checking');
    setAuthError('');
    api.me().then(profile => {
      if (!active) return;
      setUser(profile);
      setApiStatus('connected');
    }).catch(error => {
      if (!active) return;
      if (error?.status === 401) expireSession();
      else {
        setApiStatus('error');
        setAuthError(error?.message || 'No se pudo verificar la sesión con el servidor.');
      }
    });
    return () => { active = false; };
  }, [token, expireSession]);

  useEffect(() => {
    if (!token) return undefined;
    const expiresAt = getTokenExpiry();
    if (!expiresAt) return undefined;
    const timer = window.setTimeout(
      () => expireSession('Tu sesión expiró por seguridad. Inicia sesión nuevamente.'),
      Math.max(0, expiresAt - Date.now())
    );
    return () => window.clearTimeout(timer);
  }, [token, expireSession]);

  // Fail closed while the authenticated profile is unknown or lacks the requested permission.
  const can = useCallback(permission => hasPermission(user, permission), [user]);
  const value = useMemo(() => ({ token, user, apiStatus, authError, can, login, logout, expireSession }),
    [token, user, apiStatus, authError, can, login, logout, expireSession]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth debe usarse dentro de AuthProvider.');
  return context;
}
