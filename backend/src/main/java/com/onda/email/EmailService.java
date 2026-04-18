package com.onda.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendAuthCode(String toEmail, String code, String magicLinkToken) {
        String magicLinkUrl = "http://localhost:5173/auth/verify?token=" + magicLinkToken;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("Your Onda login code");
        message.setText(
            "Your Onda login code: " + code + "\n\n" +
            "Or click here to sign in:\n" + magicLinkUrl + "\n\n" +
            "Expires in 10 minutes."
        );

        try {
            mailSender.send(message);
            log.info("Auth email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send auth email to {}: {}", toEmail, e.getMessage());
            // Don't throw — auth token is already saved. User can request again.
        }
    }
}
