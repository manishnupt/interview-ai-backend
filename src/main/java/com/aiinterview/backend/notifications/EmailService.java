package com.aiinterview.backend.notifications;

import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public void sendInviteEmail(String toEmail, String name, String inviteLink) {
        // TODO: implement with SendGrid
        System.out.println("[Email] Invite sent to " + toEmail + ": " + inviteLink);
    }

    public void sendApplicationConfirmation(String toEmail, String name, String jobTitle) {
        // TODO: implement with SendGrid
        System.out.println("[Email] Application confirmation sent to " + toEmail
            + " for job: " + jobTitle);
    }

    public void sendInterviewCompleteToHR(String toEmail, String candidateName, int score) {
        // TODO: implement with SendGrid
        System.out.println("[Email] Interview complete notification sent to " + toEmail
            + " | Candidate: " + candidateName + " | Score: " + score + "/10");
    }
}
