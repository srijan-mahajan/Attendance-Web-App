package com.attendance.manager.service;

import com.attendance.manager.model.Attendance;
import com.attendance.manager.model.AttendanceStatus;
import com.attendance.manager.model.Subject;
import com.attendance.manager.model.User;
import com.attendance.manager.repository.AttendanceRepository;
import com.attendance.manager.repository.SubjectRepository;
import com.attendance.manager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AttendanceService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void markAttendance(Long subjectId, LocalDate date, Map<Long, AttendanceStatus> studentStatusMap) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        for (Map.Entry<Long, AttendanceStatus> entry : studentStatusMap.entrySet()) {
            Long studentId = entry.getKey();
            AttendanceStatus status = entry.getValue();

            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new IllegalArgumentException("Student not found"));

            // Check if attendance already exists for this day
            Optional<Attendance> existingOpt = attendanceRepository.findBySubjectAndStudentAndDate(subject, student, date);
            Attendance attendance;
            if (existingOpt.isPresent()) {
                attendance = existingOpt.get();
                attendance.setStatus(status);
            } else {
                attendance = new Attendance(student, subject, date, status);
            }
            attendanceRepository.save(attendance);

            // Re-calculate attendance percentage for the student
            long totalClasses = attendanceRepository.countBySubjectAndStudent(subject, student);
            long presentClasses = attendanceRepository.countBySubjectAndStudentAndStatus(subject, student, AttendanceStatus.PRESENT);

            double percentage = 100.0;
            if (totalClasses > 0) {
                percentage = (double) presentClasses / totalClasses * 100.0;
            }

            // Check if below limit & send mail (only if classes have been conducted)
            if (percentage < subject.getAttendanceLimit() && totalClasses > 0) {
                emailService.sendLowAttendanceAlert(
                        student.getEmail(),
                        student.getUsername(),
                        subject.getName(),
                        percentage,
                        subject.getAttendanceLimit()
                );
            }

            // WebSocket notification
            sendWebSocketUpdate(student.getId(), subject.getName(), percentage, status.name());
        }
    }

    private void sendWebSocketUpdate(Long studentId, String subjectName, double percentage, String status) {
        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("studentId", studentId);
        messagePayload.put("subjectName", subjectName);
        messagePayload.put("percentage", percentage);
        messagePayload.put("status", status);
        messagePayload.put("timestamp", LocalDate.now().toString());

        try {
            messagingTemplate.convertAndSend("/topic/attendance/" + studentId, messagePayload);
        } catch (Exception e) {
            // Log WebSocket connection issues but do not fail marking attendance
            System.err.println("Could not send WebSocket message: " + e.getMessage());
        }
    }
}
