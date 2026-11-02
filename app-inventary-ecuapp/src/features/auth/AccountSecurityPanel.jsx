import { useState } from 'react';
import { authApi } from '../../shared/api/auth/api.js';

export default function AccountSecurityPanel() {
  const [email, setEmail] = useState('');
  const [emailCode, setEmailCode] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [enrollment, setEnrollment] = useState(null);
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);

  async function run(action, success) {
    setBusy(true); setNotice('');
    try { await action(); setNotice(success); }
    catch (error) { setNotice(error?.message || 'No se pudo completar la operación.'); }
    finally { setBusy(false); }
  }

  return <section className="account-security-panel" aria-labelledby="account-security-title">
    <h2 id="account-security-title">Seguridad de la cuenta</h2>
    <div className="auth-field">
      <label htmlFor="recovery-email">Correo de recuperación</label>
      <input id="recovery-email" type="email" autoComplete="email" maxLength={254} value={email}
        onChange={event => setEmail(event.target.value)} placeholder="correo@ejemplo.com" />
      <button type="button" className="button" disabled={busy || !email.trim()}
        onClick={() => run(() => authApi.setRecoveryEmail(email.trim()), 'Enviamos un código de verificación a tu correo.')}>
        Enviar verificación
      </button>
    </div>
    <div className="auth-field">
      <label htmlFor="recovery-email-code">Código de verificación del correo</label>
      <input id="recovery-email-code" value={emailCode} maxLength={128} onChange={event => setEmailCode(event.target.value)}
        autoComplete="one-time-code" placeholder="Código recibido" />
      <button type="button" className="button" disabled={busy || !emailCode.trim()}
        onClick={() => run(() => authApi.verifyRecoveryEmail(emailCode.trim()), 'Correo de recuperación verificado correctamente.')}>
        Confirmar correo
      </button>
    </div>
    <div className="auth-field">
      <h3>Autenticación en dos pasos (TOTP)</h3>
      <button type="button" className="button" disabled={busy}
        onClick={() => run(async () => { const result = await authApi.beginMfaEnrollment(); setEnrollment(result); }, 'Añade la clave a tu aplicación autenticadora y confirma el código.')}>
        Configurar autenticación
      </button>
      {enrollment && <div className="auth-hint">
        <p>En tu aplicación autenticadora agrega esta clave:</p>
        <p><strong>{enrollment.secret}</strong></p>
        <p>URI: {enrollment.otpauthUri}</p>
      </div>}
      <label htmlFor="mfa-setup-code">Código de seis dígitos</label>
      <input id="mfa-setup-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6}
        value={mfaCode} onChange={event => setMfaCode(event.target.value.replace(/[^0-9]/g, '').slice(0, 6))}
        placeholder="123456" />
      <button type="button" className="button" disabled={busy || mfaCode.length !== 6}
        onClick={() => run(() => authApi.confirmMfaEnrollment(mfaCode), 'Autenticación en dos pasos activada.')}>
        Confirmar y activar MFA
      </button>
      <button type="button" className="button" disabled={busy || mfaCode.length !== 6}
        onClick={() => run(() => authApi.disableMfa(mfaCode), 'Autenticación en dos pasos desactivada.')}>
        Desactivar MFA
      </button>
    </div>
    {notice && <p role="status" aria-live="polite" className="auth-hint">{notice}</p>}
  </section>;
}
