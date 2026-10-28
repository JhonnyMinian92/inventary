package com.ecuapp.inventary.auth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
/**
 * Application-layer encryption of login credentials, on top of TLS: the password never travels or gets logged as
 * plaintext JSON by proxies, gateways or browser tooling.
 * Scheme: ECDH-ES on P-256 with a fresh server key per challenge, HKDF-SHA256 (salt = challengeId) and AES-256-GCM
 * (AAD = challengeId). A challenge is consumed on first use, so a captured request cannot be replayed, and its private
 * key only lives in memory for the challenge TTL (forward secrecy per login).
 */
@Service
public class LoginEncryptionService {
 public static final String ALGORITHM="ECDH-ES+HKDF-SHA256+A256GCM";
 private static final byte[] HKDF_INFO="inventary/login/v1".getBytes(StandardCharsets.US_ASCII);
 private static final int IV_BYTES=12,GCM_TAG_BITS=128,KEY_BYTES=32;
 private record Pending(PrivateKey privateKey,Instant expiresAt){}
 private final SecureRandom random=new SecureRandom();
 private final Cache<String,Pending> pending;
 private final ObjectMapper mapper; private final boolean required; private final Duration ttl; private final ECParameterSpec curve;
 public LoginEncryptionService(ObjectMapper mapper,@Value("${app.security.login-encryption.required:true}") boolean required,
  @Value("${app.security.login-encryption.challenge-ttl-seconds:120}") long ttlSeconds,
  @Value("${app.security.login-encryption.max-pending-challenges:20000}") long maxPending){
  this.mapper=mapper;this.required=required;this.ttl=Duration.ofSeconds(Math.max(30,Math.min(ttlSeconds,600)));
  this.pending=Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(Math.max(100,maxPending)).build();
  try{AlgorithmParameters params=AlgorithmParameters.getInstance("EC");params.init(new ECGenParameterSpec("secp256r1"));curve=params.getParameterSpec(ECParameterSpec.class);}
  catch(GeneralSecurityException ex){throw new IllegalStateException("La JVM no soporta ECDH P-256.",ex);}
 }
 public boolean required(){return required;}
 public AuthDtos.LoginChallenge issueChallenge(){
  try{
   KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(curve,random);KeyPair pair=generator.generateKeyPair();
   byte[] idBytes=new byte[18];random.nextBytes(idBytes);String id=Base64.getUrlEncoder().withoutPadding().encodeToString(idBytes);
   Instant expires=Instant.now().plus(ttl);
   pending.put(id,new Pending(pair.getPrivate(),expires));
   return new AuthDtos.LoginChallenge(id,ALGORITHM,Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),expires);
  }catch(GeneralSecurityException ex){throw new IllegalStateException("No se pudo generar el desafío de cifrado.",ex);}
 }
 /** Decrypts and consumes the challenge. Every failure is reported identically so it reveals nothing about the cause. */
 public AuthDtos.Credentials decrypt(AuthDtos.EncryptedCredentials encrypted){
  Pending challenge=pending.asMap().remove(encrypted.challengeId());
  if(challenge==null||!challenge.expiresAt().isAfter(Instant.now()))throw LoginSecurityException.invalidChallenge();
  byte[] shared=null,key=null,plain=null;
  try{
   PublicKey clientKey=KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encrypted.publicKey())));
   if(!(clientKey instanceof ECPublicKey ecKey)||!sameCurve(ecKey.getParams()))throw LoginSecurityException.invalidChallenge();
   KeyAgreement agreement=KeyAgreement.getInstance("ECDH");agreement.init(challenge.privateKey());agreement.doPhase(clientKey,true);
   shared=agreement.generateSecret();
   byte[] aad=encrypted.challengeId().getBytes(StandardCharsets.UTF_8);
   key=hkdfSha256(shared,aad,HKDF_INFO);
   byte[] iv=Base64.getDecoder().decode(encrypted.iv());
   if(iv.length!=IV_BYTES)throw LoginSecurityException.invalidChallenge();
   Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
   cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(GCM_TAG_BITS,iv));cipher.updateAAD(aad);
   plain=cipher.doFinal(Base64.getDecoder().decode(encrypted.ciphertext()));
   AuthDtos.Credentials credentials=mapper.readValue(plain,AuthDtos.Credentials.class);
   if(credentials==null)throw LoginSecurityException.invalidChallenge();
   return credentials;
  }catch(GeneralSecurityException|IllegalArgumentException|IOException ex){throw LoginSecurityException.invalidChallenge();}
  finally{wipe(shared);wipe(key);wipe(plain);}
 }
 private boolean sameCurve(ECParameterSpec params){
  return params!=null&&curve.getCurve().equals(params.getCurve())&&curve.getGenerator().equals(params.getGenerator())&&curve.getOrder().equals(params.getOrder());
 }
 /** RFC 5869 HKDF-SHA256 producing a single 32-byte block. */
 private static byte[] hkdfSha256(byte[] ikm,byte[] salt,byte[] info)throws GeneralSecurityException{
  Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(salt,"HmacSHA256"));byte[] prk=mac.doFinal(ikm);
  try{mac.init(new SecretKeySpec(prk,"HmacSHA256"));mac.update(info);mac.update((byte)1);byte[] okm=mac.doFinal();return okm.length==KEY_BYTES?okm:Arrays.copyOf(okm,KEY_BYTES);}
  finally{wipe(prk);}
 }
 private static void wipe(byte[] bytes){if(bytes!=null)Arrays.fill(bytes,(byte)0);}
}
