package ch.junto.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);
  private final JavaMailSender mailSender;
  private final String appBaseUrl;

  public EmailService(JavaMailSender mailSender, @Value("${app.base-url}") String appBaseUrl) {
    this.mailSender = mailSender;
    this.appBaseUrl = appBaseUrl;
  }

  public void sendAuthCode(String toEmail, String code, String magicLinkToken) {
    String magicLinkUrl = appBaseUrl + "/auth/verify?token=" + magicLinkToken;

    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(toEmail);
    message.setSubject("Your Junto login code");
    message.setText(
        "Your Junto login code: "
            + code
            + "\n\n"
            + "Or click here to sign in:\n"
            + magicLinkUrl
            + "\n\n"
            + "Expires in 10 minutes.");

    // Delivery failures propagate through AuthCodeService's transaction so
    // the undelivered token is rolled back before API behavior is chosen.
    mailSender.send(message);
    log.info("Auth email sent to {}", toEmail);
  }
}
