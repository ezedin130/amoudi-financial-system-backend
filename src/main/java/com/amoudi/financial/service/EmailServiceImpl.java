package com.amoudi.financial.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Implementation of EmailService sending password recovery verification emails
 * to the centralized shared company inbox.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final String sharedInbox;
    private final String fromAddress;
    private final String subjectPrefix;
    private final String mailPassword;

    @Autowired
    public EmailServiceImpl(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${app.mail.shared-inbox:amoudibusinessgroup@gmail.com}") String sharedInbox,
            @Value("${app.mail.from:amoudibusinessgroup@gmail.com}") String fromAddress,
            @Value("${app.mail.subject-prefix:[Amoudi Financial] }") String subjectPrefix,
            @Value("${spring.mail.password:}") String mailPassword
    ) {
        this.mailSender = mailSender;
        this.sharedInbox = sharedInbox;
        this.fromAddress = fromAddress;
        this.subjectPrefix = subjectPrefix;
        this.mailPassword = mailPassword;
    }

    @Override
    public void sendPasswordRecoveryOtp(String username, String otp, int validityMinutes) {
        log.info("Preparing password recovery OTP email for user '{}' -> shared company inbox: {}",
                username, sharedInbox);

        if (mailSender == null) {
            log.warn("JavaMailSender bean is not configured. Email to {} could not be dispatched.", sharedInbox);
            log.info("[LOCAL DEV RECOVERY OTP] Generated code for user '{}': [{}] (shared inbox: {})",
                    username, otp, sharedInbox);
            return;
        }

        if (mailPassword == null || mailPassword.isBlank()) {
            log.info("No SMTP password (SPRING_MAIL_PASSWORD) configured for {}. Skipping remote SMTP dispatch.", sharedInbox);
            log.info("[LOCAL DEV RECOVERY OTP] Generated code for user '{}': [{}] (shared inbox: {})",
                    username, otp, sharedInbox);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress);
            helper.setTo(sharedInbox);
            helper.setSubject(subjectPrefix + "Password Reset Verification Code");

            String htmlBody = buildHtmlBody(username, otp, validityMinutes);
            String textBody = buildTextBody(username, otp, validityMinutes);

            helper.setText(textBody, htmlBody);

            mailSender.send(message);
            log.info("Password recovery OTP email successfully dispatched to {}", sharedInbox);

        } catch (Exception ex) {
            log.error("Failed to send password recovery email to {}: {}. Account recovery state remains active.",
                    sharedInbox, ex.getMessage());
            log.warn("[LOCAL DEV RECOVERY OTP] Generated code for user '{}': [{}] (shared inbox: {})",
                    username, otp, sharedInbox);
        }
    }

    private String buildHtmlBody(String username, String otp, int validityMinutes) {
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'></head>" +
                "<body style='font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica, Arial, sans-serif; background-color: #f8fafc; padding: 24px; color: #1e293b; margin: 0;'>" +
                "<div style='max-width: 540px; margin: 0 auto; background: #ffffff; border-radius: 8px; border: 1px solid #e2e8f0; overflow: hidden;'>" +
                "  <div style='background-color: #0F2E22; padding: 24px; text-align: center; border-bottom: 3px solid #CBA326;'>" +
                "    <h1 style='color: #ffffff; font-size: 20px; margin: 0; letter-spacing: 2px; text-transform: uppercase;'>Amoudi Travel Service</h1>" +
                "    <p style='color: #CBA326; font-size: 13px; margin: 4px 0 0 0;'>Finance Management System</p>" +
                "  </div>" +
                "  <div style='padding: 28px 24px;'>" +
                "    <h2 style='font-size: 18px; margin-top: 0; color: #0F2E22;'>Password Reset Verification Code</h2>" +
                "    <p style='font-size: 14px; line-height: 1.5; color: #475569;'>" +
                "      A password reset request was initiated for system user account: <strong>" + escapeHtml(username) + "</strong>." +
                "    </p>" +
                "    <p style='font-size: 14px; line-height: 1.5; color: #475569;'>Use the following 6-digit verification code to complete the password recovery:</p>" +
                "    <div style='margin: 24px 0; text-align: center;'>" +
                "      <div style='display: inline-block; font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #0F2E22; background-color: #f1f5f9; padding: 14px 28px; border-radius: 8px; border: 1px solid #cbd5e1; font-family: Courier, monospace;'>" +
                otp +
                "      </div>" +
                "    </div>" +
                "    <p style='font-size: 13px; color: #64748b; line-height: 1.5;'>" +
                "      This code is valid for <strong>" + validityMinutes + " minutes</strong> from issuance and can only be used once." +
                "    </p>" +
                "    <div style='margin-top: 24px; padding: 12px; background-color: #fef2f2; border-left: 4px solid #ef4444; border-radius: 4px;'>" +
                "      <p style='margin: 0; font-size: 12px; color: #991b1b;'>" +
                "        <strong>Security Notice:</strong> If you or an authorized staff member did not initiate this request, please disregard this email and review user security." +
                "      </p>" +
                "    </div>" +
                "  </div>" +
                "  <div style='background-color: #f8fafc; padding: 16px 24px; text-align: center; border-top: 1px solid #e2e8f0; font-size: 11px; color: #94a3b8;'>" +
                "    Amoudi Travel Service · Internal Finance System · Automated Message" +
                "  </div>" +
                "</div>" +
                "</body></html>";
    }

    private String buildTextBody(String username, String otp, int validityMinutes) {
        return "AMOUDI TRAVEL SERVICE - FINANCE MANAGEMENT SYSTEM\n" +
                "Password Reset Verification Code\n\n" +
                "A password reset request was initiated for system user account: " + username + "\n\n" +
                "Verification Code: " + otp + "\n\n" +
                "This code is valid for " + validityMinutes + " minutes from issuance and can only be used once.\n\n" +
                "If you or an authorized staff member did not initiate this request, please disregard this email.\n";
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}
