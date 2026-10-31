
import { request } from '../http/request.js';
import { encryptCredentials } from '../../../security/loginCrypto.js';

// El desafío criptográfico de un solo uso se conserva igual que en el cliente original.
async function encryptedLogin(username, password) {
  for (let attempt = 0; ; attempt++) {
    const challenge = await request('/auth/login-challenge', { method: 'POST' });
    const encrypted = await encryptCredentials(challenge, { username, password });
    try {
      return await request('/auth/login', { method: 'POST', body: { encrypted } });
    } catch (error) {
      if (attempt === 0 && error?.code === 'LOGIN_CHALLENGE_INVALID') continue;
      throw error;
    }
  }
}
export const authApi = {
  login: encryptedLogin,
  logout: () => request('/auth/logout', { method: 'POST' }),
  me: () => request('/auth/me')
};
