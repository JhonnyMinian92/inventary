// Cifrado de credenciales en la capa de aplicación, además de TLS: la contraseña nunca viaja ni queda registrada
// como JSON en texto plano. Contrato idéntico a LoginEncryptionService del backend:
// ECDH P-256 (clave efímera del navegador + clave de un solo uso del servidor) -> HKDF-SHA256 (salt = challengeId)
// -> AES-256-GCM (IV aleatorio de 12 bytes, AAD = challengeId).

export const LOGIN_ENCRYPTION_ALGORITHM = 'ECDH-ES+HKDF-SHA256+A256GCM';
const HKDF_INFO = 'inventary/login/v1';
const encoder = new TextEncoder();

export class LoginCryptoUnavailableError extends Error {
  constructor() {
    super('El navegador no permite cifrar las credenciales en este origen. Abre Inventary con HTTPS o desde localhost.');
    this.name = 'LoginCryptoUnavailableError';
  }
}

function toBase64(buffer) {
  let binary = '';
  for (const byte of new Uint8Array(buffer)) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function fromBase64(value) {
  return Uint8Array.from(atob(value), char => char.charCodeAt(0));
}

export async function encryptCredentials(challenge, { username, password }, cryptoApi = globalThis.crypto) {
  const subtle = cryptoApi?.subtle;
  if (!subtle || typeof cryptoApi.getRandomValues !== 'function') throw new LoginCryptoUnavailableError();
  if (challenge?.algorithm !== LOGIN_ENCRYPTION_ALGORITHM || !challenge.challengeId || !challenge.publicKey) {
    throw new Error('El servidor respondió con un esquema de cifrado no compatible. Actualiza la página e inténtalo de nuevo.');
  }
  const curve = { name: 'ECDH', namedCurve: 'P-256' };
  const context = encoder.encode(challenge.challengeId);
  const serverKey = await subtle.importKey('spki', fromBase64(challenge.publicKey), curve, false, []);
  // La clave privada efímera no es exportable; la pública siempre lo es.
  const ephemeral = await subtle.generateKey(curve, false, ['deriveBits']);
  const sharedSecret = await subtle.deriveBits({ name: 'ECDH', public: serverKey }, ephemeral.privateKey, 256);
  const keyMaterial = await subtle.importKey('raw', sharedSecret, 'HKDF', false, ['deriveKey']);
  const aesKey = await subtle.deriveKey(
    { name: 'HKDF', hash: 'SHA-256', salt: context, info: encoder.encode(HKDF_INFO) },
    keyMaterial, { name: 'AES-GCM', length: 256 }, false, ['encrypt']
  );
  const iv = cryptoApi.getRandomValues(new Uint8Array(12));
  const plaintext = encoder.encode(JSON.stringify({ username, password }));
  try {
    const ciphertext = await subtle.encrypt({ name: 'AES-GCM', iv, additionalData: context, tagLength: 128 }, aesKey, plaintext);
    const publicKey = await subtle.exportKey('spki', ephemeral.publicKey);
    return { challengeId: challenge.challengeId, publicKey: toBase64(publicKey), iv: toBase64(iv), ciphertext: toBase64(ciphertext) };
  } finally {
    plaintext.fill(0);
  }
}
