package com.aiinterview.backend.notifications;

import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public void sendInviteEmail(String toEmail, String name, String inviteLink) {
        // TODO: implement with SendGrid
        System.out.println("[Email] Invite sent to " + toEmail + ": " + inviteLink);
    }
}
