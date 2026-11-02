import { useState } from 'react';
import BrandLogo from '../../components/BrandLogo.jsx';
import { authApi } from '../../shared/api/auth/api.js';
import './login.css';

export default function PasswordResetPage({ token }) {
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [success, setSuccess] = useState(false);

  async function submit(event) {
    event.preventDefault();
    if (password.length < 12) {
      setNotice('La contraseña debe tener al menos 12 caracteres.');
      return;
    }
    setBusy(true);
    setNotice('');
    try {
      await authApi.completePasswordReset(token, password);
      setPassword('');
      setSuccess(true);
      setNotice('Contraseña actualizada. Ya puedes iniciar sesión.');
      window.history.replaceState({}, '', window.location.pathname);
    } catch (error) {
      setNotice(error?.message || 'El enlace no es válido o ha expirado. Solicita uno nuevo.');
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth-page">
    <div className="auth-card">
      <section className="auth-panel" aria-labelledby="password-reset-title">
        <BrandLogo className="auth-brand" />
        <p className="auth-eyebrow">Seguridad de la cuenta</p>
        <h1 id="password-reset-title">Restablecer contraseña</h1>
        <form className="auth-form" onSubmit={submit}>
          <div className="auth-field">
            <label htmlFor="new-password">Nueva contraseña</label>
            <input id="new-password" type="password" autoComplete="new-password" minLength={12}
              maxLength={128} required value={password} onChange={event => setPassword(event.target.value)} />
          </div>
          {notice && <p className="auth-hint" role="status" aria-live="polite">{notice}</p>}
          {!success && <button type="submit" className="auth-submit" disabled={busy}>
            {busy ? 'Actualizando…' : 'Guardar nueva contraseña'}
          </button>}
          <button type="button" className="auth-link" onClick={() => { window.location.href = window.location.pathname; }}>
            Volver al inicio de sesión
          </button>
        </form>
      </section>
    </div>
  </main>;
}
