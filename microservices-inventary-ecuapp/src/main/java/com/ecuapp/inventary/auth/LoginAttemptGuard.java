package com.ecuapp.inventary.auth;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * Per-account brute-force protection that complements the per-address rate limit (which distributed attacks evade).
 * Keys are normalized usernames whether or not the account exists, so a lockout never reveals which users are real.
 * State is per instance and in memory, consistent with the single-instance deployment of this monolith.
 */
@Component
public class LoginAttemptGuard {
 private record Attempts(int failures,Instant firstFailureAt,Instant lockedUntil){}
 private final Cache<String,Attempts> attempts; private final int maxFailures; private final Duration window;
 public LoginAttemptGuard(@Value("${app.security.login-lockout.max-failures:5}") int maxFailures,
  @Value("${app.security.login-lockout.minutes:15}") long minutes){
  this.maxFailures=maxFailures;this.window=Duration.ofMinutes(Math.max(1,Math.min(minutes,1440)));
  this.attempts=Caffeine.newBuilder().expireAfterWrite(window).maximumSize(100_000).build();
 }
 public void checkNotLocked(String username){
  if(maxFailures<=0)return;
  Attempts current=attempts.getIfPresent(username);
  if(current==null||current.lockedUntil()==null)return;
  long seconds=Duration.between(Instant.now(),current.lockedUntil()).toSeconds();
  if(seconds>0)throw LoginSecurityException.locked(seconds);
 }
 /** Returns true when this failure locked the account. */
 public boolean recordFailure(String username){
  if(maxFailures<=0)return false;
  Instant now=Instant.now();AtomicBoolean locked=new AtomicBoolean(false);
  attempts.asMap().compute(username,(ignored,current)->{
   boolean expired=current==null||(current.lockedUntil()!=null?!current.lockedUntil().isAfter(now):!current.firstFailureAt().plus(window).isAfter(now));
   int failures=expired?1:current.failures()+1;Instant first=expired?now:current.firstFailureAt();
   if(failures>=maxFailures){locked.set(true);return new Attempts(failures,first,now.plus(window));}
   return new Attempts(failures,first,null);
  });
  return locked.get();
 }
 public void recordSuccess(String username){attempts.invalidate(username);}
}
