package com.ecuapp.inventary.auth;
import org.springframework.http.HttpStatus;
/** Login rejections that carry their own HTTP status, stable error code and optional Retry-After. */
public class LoginSecurityException extends RuntimeException {
 private final HttpStatus status; private final String code; private final String title; private final long retryAfterSeconds;
 private LoginSecurityException(HttpStatus status,String code,String title,String message,long retryAfterSeconds){
  super(message);this.status=status;this.code=code;this.title=title;this.retryAfterSeconds=retryAfterSeconds;
 }
 public static LoginSecurityException invalidChallenge(){
  return new LoginSecurityException(HttpStatus.BAD_REQUEST,"LOGIN_CHALLENGE_INVALID","Cifrado de credenciales inválido",
   "El desafío de cifrado no es válido, ya fue usado o expiró. Solicita uno nuevo e intenta otra vez.",0);
 }
 public static LoginSecurityException encryptionRequired(){
  return new LoginSecurityException(HttpStatus.BAD_REQUEST,"LOGIN_ENCRYPTION_REQUIRED","Cifrado de credenciales obligatorio",
   "Este servidor solo acepta credenciales cifradas. Solicita un desafío en POST /api/v1/auth/login-challenge.",0);
 }
 public static LoginSecurityException mixedPayload(){
  return new LoginSecurityException(HttpStatus.BAD_REQUEST,"LOGIN_PAYLOAD_INVALID","Solicitud de inicio de sesión inválida",
   "Envía las credenciales cifradas o en texto plano, no ambas.",0);
 }
 public static LoginSecurityException locked(long retryAfterSeconds){
  long minutes=Math.max(1,(retryAfterSeconds+59)/60);
  return new LoginSecurityException(HttpStatus.TOO_MANY_REQUESTS,"LOGIN_TEMPORARILY_LOCKED","Cuenta bloqueada temporalmente",
   "Demasiados intentos fallidos para esta cuenta. Intenta nuevamente en "+minutes+(minutes==1?" minuto.":" minutos."),retryAfterSeconds);
 }
 public HttpStatus status(){return status;} public String code(){return code;} public String title(){return title;}
 public long retryAfterSeconds(){return retryAfterSeconds;}
}
