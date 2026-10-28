import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createDecipheriv, createPublicKey, diffieHellman, generateKeyPairSync, hkdfSync, webcrypto } from 'node:crypto';
import { encryptCredentials, LOGIN_ENCRYPTION_ALGORITHM, LoginCryptoUnavailableError } from './loginCrypto.js';

// Reproduce el lado del servidor con node:crypto (implementación independiente de WebCrypto).
function serverChallenge(challengeId = 'Q2hhbGxlbmdlLTAxMjM0NTY3') {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'P-256' });
  const spki = publicKey.export({ type: 'spki', format: 'der' }).toString('base64');
  return { privateKey, challenge: { challengeId, algorithm: LOGIN_ENCRYPTION_ALGORITHM, publicKey: spki, expiresAt: new Date().toISOString() } };
}

function serverDecrypt(privateKey, encrypted, aad = encrypted.challengeId) {
  const clientKey = createPublicKey({ key: Buffer.from(encrypted.publicKey, 'base64'), format: 'der', type: 'spki' });
  const shared = diffieHellman({ privateKey, publicKey: clientKey });
  const key = Buffer.from(hkdfSync('sha256', shared, Buffer.from(encrypted.challengeId), Buffer.from('inventary/login/v1'), 32));
  const data = Buffer.from(encrypted.ciphertext, 'base64');
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(encrypted.iv, 'base64'));
  decipher.setAAD(Buffer.from(aad));
  decipher.setAuthTag(data.subarray(-16));
  return JSON.parse(Buffer.concat([decipher.update(data.subarray(0, -16)), decipher.final()]).toString('utf8'));
}

test('el servidor descifra exactamente las credenciales enviadas', async () => {
  const { privateKey, challenge } = serverChallenge();
  const credentials = { username: 'admin', password: 'Clave-Segura-ñ-2026-€!' };
  const encrypted = await encryptCredentials(challenge, credentials, webcrypto);
  assert.equal(encrypted.challengeId, challenge.challengeId);
  assert.equal(Buffer.from(encrypted.iv, 'base64').length, 12);
  assert.equal(Buffer.from(encrypted.publicKey, 'base64').length, 91);
  assert.ok(!Buffer.from(encrypted.ciphertext, 'base64').toString('latin1').includes('Clave'));
  assert.deepEqual(serverDecrypt(privateKey, encrypted), credentials);
});

test('cada cifrado usa una clave efímera y un IV distintos', async () => {
  const { challenge } = serverChallenge();
  const first = await encryptCredentials(challenge, { username: 'a', password: 'b' }, webcrypto);
  const second = await encryptCredentials(challenge, { username: 'a', password: 'b' }, webcrypto);
  assert.notEqual(first.publicKey, second.publicKey);
  assert.notEqual(first.iv, second.iv);
  assert.notEqual(first.ciphertext, second.ciphertext);
});

test('el texto cifrado queda ligado al desafío (AAD)', async () => {
  const { privateKey, challenge } = serverChallenge();
  const encrypted = await encryptCredentials(challenge, { username: 'admin', password: 'x' }, webcrypto);
  assert.throws(() => serverDecrypt(privateKey, encrypted, 'otro-desafio'));
});

test('rechaza esquemas desconocidos y orígenes sin WebCrypto', async () => {
  const { challenge } = serverChallenge();
  await assert.rejects(encryptCredentials({ ...challenge, algorithm: 'RSA1_5' }, { username: 'a', password: 'b' }, webcrypto));
  await assert.rejects(encryptCredentials(challenge, { username: 'a', password: 'b' }, { getRandomValues: () => {} }), LoginCryptoUnavailableError);
});
