package com.ecuapp.inventary.auth;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Sends account security messages off the request thread to avoid email-existence timing leaks. */
@Service
public class AuthEmailSender {
    private static final Logger log=LoggerFactory.getLogger(AuthEmailSender.class);
    private final JavaMailSender sender;
    private final MeterRegistry metrics;
    private final String from;

    public AuthEmailSender(JavaMailSender sender,MeterRegistry metrics,
            @Value("${app.security.mail-from:no-reply@inventary.local}") String from) {
        this.sender=sender; this.metrics=metrics; this.from=from;
    }

    @Async
    public void sendAsync(String recipient,String subject,String body) {
        try {
            SimpleMailMessage message=new SimpleMailMessage();
            message.setFrom(from); message.setTo(recipient); message.setSubject(subject); message.setText(body);
            sender.send(message);
            metrics.counter("inventary.auth.email","outcome","sent").increment();
        } catch(RuntimeException failure) {
            metrics.counter("inventary.auth.email","outcome","failed").increment();
            log.error("Authentication email delivery failed; recipient omitted from log",failure);
        }
    }
}
