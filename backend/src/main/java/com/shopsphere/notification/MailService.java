package com.shopsphere.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email sender with a documented console fallback: when no SMTP host is
 * configured (dev), messages are logged instead of silently "succeeding".
 */
@Service
public class MailService {

    public enum Intent { VERIFY_EMAIL, RESET_PASSWORD, OTHER }

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;   // may be a no-op instance when spring.mail.host=""
    private final boolean mailConfigured;
    private final String from;

    public MailService(JavaMailSender mailSender,
                       org.springframework.core.env.Environment env) {
        this.mailSender = mailSender;
        this.from = env.getProperty("shopsphere.app.mail.from", "noreply@shopsphere.local");
        this.mailConfigured = env.getProperty("spring.mail.host", "") != null
                                && !env.getProperty("spring.mail.host", "").isBlank();
    }

    public void send(String to, String subject, String body, Intent intent) {
        if (!mailConfigured) {
            log.info("[MAIL-CONSOLE-FALLBACK] intent={} to={} subject={}\n{}", intent, to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            // Never fail a customer flow because SMTP hiccupped — log for traceability.
            log.error("SMTP send failed for {} (intent={}): {}", to, intent, e.getMessage());
        }
    }
}
