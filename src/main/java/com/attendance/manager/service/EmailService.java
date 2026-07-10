package com.attendance.manager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Async
    public void sendLowAttendanceAlert(String studentEmail, String studentName, String subjectName, double currentPercentage, double limit) {
        String subject = "Low Attendance Alert: " + subjectName;
        String text = String.format(
                "Hello %s,\n\n" +
                "This is an automated alert regarding your attendance in the subject: %s.\n" +
                "Your current attendance is %.2f%%, which is below the minimum required limit of %.2f%%.\n\n" +
                "Please make sure to attend the upcoming classes to avoid further issues.\n\n" +
                "Best regards,\nAttendance Management Team",
                studentName, subjectName, currentPercentage, limit
        );

        logger.info("Triggering low attendance email to {} (Subject: {}, Current: {}%, Limit: {}%)",
                studentEmail, subjectName, currentPercentage, limit);

        try {
            if (mailSender != null) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(studentEmail);
                message.setSubject(subject);
                message.setText(text);
                mailSender.send(message);
                logger.info("Email successfully sent via SMTP to {}", studentEmail);
            } else {
                logger.warn("JavaMailSender is not initialized. Printing message details to console.");
                printMockEmail(studentEmail, subject, text);
            }
        } catch (Exception e) {
            logger.error("Failed to send email via SMTP (Error: {}). Printing message details to console.", e.getMessage());
            printMockEmail(studentEmail, subject, text);
        }
    }

    private void printMockEmail(String to, String subject, String text) {
        System.out.println("\n==================== [MOCK EMAIL ALERT] ====================");
        System.out.println("To: " + to);
        System.out.println("Subject: " + subject);
        System.out.println("Message Body:\n" + text);
        System.out.println("============================================================\n");
    }
}
