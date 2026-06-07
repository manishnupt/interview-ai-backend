package com.aiinterview.backend.notifications;

import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Value("${app.sendgrid.api-key}")
    private String apiKey;

    @Value("${app.sendgrid.from-email}")
    private String fromEmail;

    @Value("${app.sendgrid.from-name}")
    private String fromName;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private void send(String toEmail, String toName,
                      String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[Email STUB] No API key — " + subject
                + " → " + toEmail);
            return;
        }
        try {
            Email from = new Email(fromEmail, fromName);
            Email to = new Email(toEmail, toName);
            Content content = new Content("text/html", htmlBody);
            Mail mail = new Mail(from, subject, to, content);

            SendGrid sg = new SendGrid(apiKey);
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());
            Response response = sg.api(request);

            System.out.println("[Email] Sent '" + subject
                + "' to " + toEmail
                + " | Status: " + response.getStatusCode());
        } catch (Exception e) {
            System.out.println("[Email] Failed to send '"
                + subject + "' to " + toEmail
                + ": " + e.getMessage());
        }
    }

    public void sendInviteEmail(String toEmail, String name, String inviteLink) {
        String subject = "You've been invited to join AI Interview Platform";
        String html = """
            <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
              <h2>You're invited!</h2>
              <p>Hi %s,</p>
              <p>You've been invited to join the AI Interview Platform.
                 Click the button below to set your password and get started.</p>
              <a href="%s"
                 style="display:inline-block;background:#6366f1;
                        color:white;padding:12px 24px;
                        border-radius:8px;text-decoration:none;
                        font-weight:600;margin-top:12px">
                Accept invitation
              </a>
              <p style="color:#64748b;font-size:13px;margin-top:16px">
                This link expires in 48 hours. If you didn't expect this,
                you can ignore this email.
              </p>
            </div>
            """.formatted(name, inviteLink);
        send(toEmail, name, subject, html);
    }

    public void sendApplicationConfirmation(
            String toEmail, String candidateName, String jobTitle) {
        String subject = "We received your application — " + jobTitle;
        String html = """
            <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
              <h2>Application received ✓</h2>
              <p>Hi %s,</p>
              <p>Thank you for applying for <strong>%s</strong>.
                 We'll review your application and reach out if you're
                 shortlisted for the next round.</p>
              <p>You may receive a call from our AI screening interviewer
                 if your profile matches our requirements.</p>
              <p style="color:#64748b;font-size:13px">
                 This is an automated message — please do not reply.</p>
            </div>
            """.formatted(candidateName, jobTitle);
        send(toEmail, candidateName, subject, html);
    }

    public void sendRejectionEmail(
            String toEmail, String candidateName) {
        String subject = "Update on your application";
        String html = """
            <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
              <h2>Application update</h2>
              <p>Hi %s,</p>
              <p>Thank you for your interest. After reviewing your profile,
                 we've decided to move forward with other candidates whose
                 experience more closely matches our current requirements.</p>
              <p>We appreciate the time you took to apply and wish you
                 the best in your search.</p>
            </div>
            """.formatted(candidateName);
        send(toEmail, candidateName, subject, html);
    }

    public void sendShortlistEmail(
            String toEmail, String candidateName) {
        String subject = "You've been shortlisted — expect a call";
        String html = """
            <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
              <h2>Great news — you're shortlisted! 🎉</h2>
              <p>Hi %s,</p>
              <p>Your application has been reviewed and you've been
                 shortlisted for the next round.</p>
              <p><strong>You will receive a phone call from our AI
                 interviewer within the next 24 hours.</strong>
                 Please keep your phone handy.</p>
              <p>The call will last approximately 10 minutes and will
                 cover your technical background and experience.</p>
            </div>
            """.formatted(candidateName);
        send(toEmail, candidateName, subject, html);
    }

    public void sendInterviewCompleteToHR(
            String hrEmail, String candidateName, int score) {
        String subject = "Interview complete — " + candidateName
            + " scored " + score + "/10";
        String html = """
            <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
              <h2>Interview report ready</h2>
              <p>The AI interview for <strong>%s</strong> is complete.</p>
              <p style="font-size:32px;font-weight:bold;color:%s">
                 %d/10
              </p>
              <p>Log in to the platform to view the full report,
                 transcript, strengths and weaknesses.</p>
              <a href="%s/dashboard"
                 style="display:inline-block;background:#6366f1;
                        color:white;padding:12px 24px;
                        border-radius:8px;text-decoration:none;
                        font-weight:600;margin-top:12px">
                View report
              </a>
            </div>
            """.formatted(
                candidateName,
                score >= 7 ? "#22c55e" : score >= 5 ? "#f97316" : "#ef4444",
                score,
                frontendUrl
            );
        send(hrEmail, "HR Team", subject, html);
    }

    public void sendMagicLink(
            String toEmail, String candidateName,
            String magicLinkUrl) {
        String subject = "Check your application status";
        String html = """
            <div style="font-family:sans-serif;max-width:560px;margin:0 auto">
              <h2>Your application status</h2>
              <p>Hi %s,</p>
              <p>Click the button below to check the current status
                 of your application. This link is valid for 48 hours.</p>
              <a href="%s"
                 style="display:inline-block;background:#6366f1;
                        color:white;padding:12px 24px;
                        border-radius:8px;text-decoration:none;
                        font-weight:600;margin-top:12px">
                View my application status
              </a>
              <p style="color:#64748b;font-size:13px;margin-top:16px">
                If you didn't request this, you can ignore this email.
              </p>
            </div>
            """.formatted(candidateName, magicLinkUrl);
        send(toEmail, candidateName, subject, html);
    }
}
