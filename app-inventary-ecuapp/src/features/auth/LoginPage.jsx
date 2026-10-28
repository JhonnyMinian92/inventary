import { useEffect, useId, useRef, useState } from 'react';
import { ChevronRight, Eye, EyeOff, LockKeyhole, User } from 'lucide-react';
import { toast } from '../../ui/feedback.js';
import BrandLogo from '../../components/BrandLogo.jsx';
import LoginIllustration from './LoginIllustration.jsx';
import ecuappLogo from '../../assets/ecuapp-logo.png';
import './login.css';

function loginErrorMessage(error) {
  const code = error?.code || error?.details?.code;
  if (code === 'INVALID_CREDENTIALS') return 'Usuario o contraseña incorrectos.';
  if (code === 'LOGIN_TEMPORARILY_LOCKED') return error.message;
  if (code === 'RATE_LIMIT_EXCEEDED') {
    const seconds = Number(error.retryAfter) || 60;
    return `Demasiados intentos desde este equipo. Espera ${seconds} segundos e inténtalo de nuevo.`;
  }
  if (code === 'VALIDATION_FAILED') return 'Revisa el usuario y la contraseña: el formato no es válido.';
  return error?.message || 'No se pudo iniciar sesión.';
}

export default function LoginPage({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);
  const [showRecovery, setShowRecovery] = useState(false);
  const [busy, setBusy] = useState(false);
  const usernameRef = useRef(null);
  const passwordRef = useRef(null);
  const ids = useId();

  useEffect(() => { usernameRef.current?.focus(); }, []);

  async function submit(event) {
    event.preventDefault();
    if (busy) return;
    const user = username.trim();
    if (!user || !password) {
      toast.error('Escribe tu usuario y tu contraseña.');
      (user ? passwordRef : usernameRef).current?.focus();
      return;
    }
    setBusy(true);
    try {
      await onLogin({ username: user, password, remember });
    } catch (loginError) {
      // La contraseña no se conserva en memoria tras un intento fallido.
      setPassword('');
      setShowPassword(false);
      toast.error(loginErrorMessage(loginError));
      setBusy(false);
      passwordRef.current?.focus();
    }
  }

  function trackCapsLock(event) {
    if (typeof event.getModifierState === 'function') setCapsLock(event.getModifierState('CapsLock'));
  }

  return <main className="auth-page">
    <div className="auth-card">
      <section className="auth-panel" aria-labelledby={`${ids}-title`}>
        <BrandLogo className="auth-brand" />
        <p className="auth-eyebrow">Bienvenido de nuevo</p>
        <h1 id={`${ids}-title`}>Toma el control absoluto de tu inventario.</h1>


        <form className="auth-form" onSubmit={submit} noValidate aria-busy={busy}>
          <div className="auth-field">
            <label htmlFor={`${ids}-user`}>Usuario</label>
            <div className="auth-input">
              <User size={20} aria-hidden="true" />
              <input ref={usernameRef} id={`${ids}-user`} name="username" type="text" autoComplete="username" autoCapitalize="none" autoCorrect="off"
                spellCheck={false} maxLength={80} required value={username} disabled={busy} placeholder="Tu usuario"
                onChange={event => setUsername(event.target.value)} />
            </div>
          </div>

          <div className="auth-field">
            <div className="auth-label-row">
              <label htmlFor={`${ids}-password`}>Contraseña</label>
              <button type="button" className="auth-link" aria-expanded={showRecovery} aria-controls={`${ids}-recovery`} onClick={() => setShowRecovery(value => !value)}>Olvidé mi contraseña</button>
            </div>
            <div className="auth-input">
              <LockKeyhole size={20} aria-hidden="true" />
              <input ref={passwordRef} id={`${ids}-password`} name="password" type={showPassword ? 'text' : 'password'} autoComplete="current-password"
                maxLength={128} required value={password} disabled={busy} placeholder="Tu contraseña" aria-describedby={capsLock ? `${ids}-caps` : undefined}
                onChange={event => setPassword(event.target.value)} onKeyDown={trackCapsLock} onKeyUp={trackCapsLock} onBlur={() => setCapsLock(false)} />
              <button type="button" className="auth-reveal" aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'} aria-pressed={showPassword}
                aria-controls={`${ids}-password`} disabled={busy} onClick={() => setShowPassword(value => !value)}>
                {showPassword ? <EyeOff size={20} aria-hidden="true" /> : <Eye size={20} aria-hidden="true" />}
              </button>
            </div>
            {capsLock && <p id={`${ids}-caps`} className="auth-hint warning">Bloq Mayús activado</p>}
            {showRecovery && <p id={`${ids}-recovery`} className="auth-hint">Solicítala a un administrador.</p>}
          </div>

          <label className="auth-check">
            <input type="checkbox" checked={remember} disabled={busy} onChange={event => setRemember(event.target.checked)} />
            <span>Mantener sesión iniciada</span>
          </label>

          <button type="submit" className="auth-submit" disabled={busy}>
            {busy ? <><span className="auth-spinner" aria-hidden="true" />Verificando…</> : <>Iniciar sesión<ChevronRight size={20} aria-hidden="true" /></>}
          </button>
        </form>

        <footer className="auth-legal">
          <img src={ecuappLogo} alt="EcuApp" width="24" height="24" />
          <p>© {new Date().getFullYear()} EcuApp. Todos los derechos reservados.</p>
        </footer>
      </section>

      <aside className="auth-showcase" aria-label="Presentación de Inventary">
        <LoginIllustration />
        <div className="auth-showcase-copy">
          <h2>Visualiza, gestiona y domina tu stock</h2>
          <ul className="auth-tags">
            <li>Tiempo real</li>
            <li>Control de stock</li>
            <li>Múltiples almacenes</li>
          </ul>
        </div>
      </aside>
    </div>
  </main>;
}
