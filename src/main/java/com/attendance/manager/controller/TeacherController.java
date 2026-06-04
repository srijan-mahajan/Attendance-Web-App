package com.attendance.manager.controller;

import com.attendance.manager.model.*;
import com.attendance.manager.repository.*;
import com.attendance.manager.service.AttendanceService;
import com.attendance.manager.service.PdfReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private AttendanceService attendanceService;

    @Autowired
    private PdfReportService pdfReportService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User getLoggedTeacher(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Teacher user not found"));
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<Subject>> getMySubjects(@AuthenticationPrincipal UserDetails userDetails) {
        User teacher = getLoggedTeacher(userDetails);
        return ResponseEntity.ok(subjectRepository.findByTeacher(teacher));
    }

    @PutMapping("/subjects/{subjectId}/limit")
    public ResponseEntity<?> setAttendanceLimit(
            @PathVariable Long subjectId,
            @RequestParam double limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = getLoggedTeacher(userDetails);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        if (!subject.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body("You do not teach this subject");
        }

        subject.setAttendanceLimit(limit);
        subjectRepository.save(subject);
        return ResponseEntity.ok("Attendance limit updated successfully to " + limit + "%");
    }

    @PostMapping("/students")
    public ResponseEntity<?> addStudent(
            @RequestBody AdminController.CreateUserRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username is already taken");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email is already taken");
        }

        User student = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getEmail(),
                Role.ROLE_STUDENT,
                request.getRollNumber(),
                request.getBranch(),
                request.getSemester()
        );
        userRepository.save(student);
        return ResponseEntity.ok(student);
    }

    @GetMapping("/students")
    public ResponseEntity<List<User>> getStudents() {
        return ResponseEntity.ok(userRepository.findByRole(Role.ROLE_STUDENT));
    }

    @PostMapping("/subjects/{subjectId}/enroll")
    public ResponseEntity<?> enrollStudent(
            @PathVariable Long subjectId,
            @RequestParam Long studentId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = getLoggedTeacher(userDetails);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        if (!subject.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body("You do not teach this subject");
        }

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (student.getRole() != Role.ROLE_STUDENT) {
            return ResponseEntity.badRequest().body("User is not a student");
        }

        subject.getStudents().add(student);
        subjectRepository.save(subject);
        return ResponseEntity.ok("Student enrolled in subject successfully");
    }

    @PostMapping("/subjects/{subjectId}/attendance")
    public ResponseEntity<?> markAttendance(
            @PathVariable Long subjectId,
            @RequestBody MarkAttendanceRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = getLoggedTeacher(userDetails);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        if (!subject.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body("You do not teach this subject");
        }

        LocalDate attendanceDate = request.getDate() != null ? request.getDate() : LocalDate.now();

        Map<Long, AttendanceStatus> statusMap = new HashMap<>();
        for (Map.Entry<String, String> entry : request.getAttendance().entrySet()) {
            try {
                Long studentId = Long.parseLong(entry.getKey());
                AttendanceStatus status = AttendanceStatus.valueOf(entry.getValue().toUpperCase());
                statusMap.put(studentId, status);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Invalid student ID or status");
            }
        }

        attendanceService.markAttendance(subjectId, attendanceDate, statusMap);
        return ResponseEntity.ok("Attendance marked successfully");
    }

    @GetMapping("/subjects/{subjectId}/report")
    public ResponseEntity<InputStreamResource> downloadReport(
            @PathVariable Long subjectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = getLoggedTeacher(userDetails);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        if (!subject.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body(null);
        }

        ByteArrayInputStream bis = pdfReportService.generateAttendanceReport(subject);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=attendance_report_" + subject.getName().replaceAll("\\s+", "_") + ".pdf");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(bis));
    }

    @GetMapping("/subjects/{subjectId}/statistics")
    public ResponseEntity<?> getSubjectStatistics(
            @PathVariable Long subjectId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User teacher = getLoggedTeacher(userDetails);
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        if (!subject.getTeacher().getId().equals(teacher.getId())) {
            return ResponseEntity.status(403).body("You do not teach this subject");
        }

        List<Map<String, Object>> studentStatsList = new ArrayList<>();
        for (User student : subject.getStudents()) {
            long totalClasses = attendanceRepository.countBySubjectAndStudent(subject, student);
            long presentClasses = attendanceRepository.countBySubjectAndStudentAndStatus(subject, student, AttendanceStatus.PRESENT);

            double percentage = 100.0;
            if (totalClasses > 0) {
                percentage = (double) presentClasses / totalClasses * 100.0;
            }

            Map<String, Object> stat = new HashMap<>();
            stat.put("studentId", student.getId());
            stat.put("username", student.getUsername());
            stat.put("email", student.getEmail());
            stat.put("rollNumber", student.getRollNumber());
            stat.put("branch", student.getBranch());
            stat.put("semester", student.getSemester());
            stat.put("totalClasses", totalClasses);
            stat.put("presentClasses", presentClasses);
            stat.put("attendancePercentage", percentage);
            stat.put("belowLimit", percentage < subject.getAttendanceLimit() && totalClasses > 0);
            studentStatsList.add(stat);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("subjectId", subject.getId());
        response.put("subjectName", subject.getName());
        response.put("attendanceLimit", subject.getAttendanceLimit());
        response.put("students", studentStatsList);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/mentor/overall-statistics")
    public ResponseEntity<?> getOverallStatistics() {
        List<User> students = userRepository.findByRole(Role.ROLE_STUDENT);
        List<Map<String, Object>> overallStats = new ArrayList<>();

        for (User student : students) {
            long totalClasses = 0;
            long presentClasses = 0;
            List<Map<String, Object>> subjectBreakdown = new ArrayList<>();

            List<Subject> allSubjects = subjectRepository.findAll();
            for (Subject subject : allSubjects) {
                if (subject.getStudents().stream().anyMatch(s -> s.getId().equals(student.getId()))) {
                    long subTotal = attendanceRepository.countBySubjectAndStudent(subject, student);
                    long subPresent = attendanceRepository.countBySubjectAndStudentAndStatus(subject, student, AttendanceStatus.PRESENT);
                    double subPercentage = 100.0;
                    if (subTotal > 0) {
                        subPercentage = (double) subPresent / subTotal * 100.0;
                    }

                    Map<String, Object> subMap = new HashMap<>();
                    subMap.put("subjectName", subject.getName());
                    subMap.put("totalClasses", subTotal);
                    subMap.put("presentClasses", subPresent);
                    subMap.put("percentage", subPercentage);
                    subMap.put("limit", subject.getAttendanceLimit());
                    subMap.put("belowLimit", subPercentage < subject.getAttendanceLimit() && subTotal > 0);

                    subjectBreakdown.add(subMap);

                    totalClasses += subTotal;
                    presentClasses += subPresent;
                }
            }

            double overallPercentage = 100.0;
            if (totalClasses > 0) {
                overallPercentage = (double) presentClasses / totalClasses * 100.0;
            }

            Map<String, Object> studentStat = new HashMap<>();
            studentStat.put("studentId", student.getId());
            studentStat.put("username", student.getUsername());
            studentStat.put("email", student.getEmail());
            studentStat.put("rollNumber", student.getRollNumber());
            studentStat.put("branch", student.getBranch());
            studentStat.put("semester", student.getSemester());
            studentStat.put("totalClasses", totalClasses);
            studentStat.put("presentClasses", presentClasses);
            studentStat.put("overallPercentage", overallPercentage);
            studentStat.put("subjectBreakdown", subjectBreakdown);
            
            boolean anyWarning = subjectBreakdown.stream().anyMatch(sb -> (boolean) sb.get("belowLimit"));
            studentStat.put("hasWarning", anyWarning);

            overallStats.add(studentStat);
        }

        return ResponseEntity.ok(overallStats);
    }

    @GetMapping("/mentor/overall-report")
    public ResponseEntity<InputStreamResource> downloadOverallReport() {
        List<User> students = userRepository.findByRole(Role.ROLE_STUDENT);
        List<Subject> allSubjects = subjectRepository.findAll();
        ByteArrayInputStream bis = pdfReportService.generateOverallAttendanceReport(students, allSubjects);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Disposition", "attachment; filename=overall_mentor_attendance_report.pdf");

        return ResponseEntity
                .ok()
                .headers(headers)
                .contentType(MediaType.APPLICATION_PDF)
                .body(new InputStreamResource(bis));
    }

    public static class MarkAttendanceRequest {
        private LocalDate date;
        private Map<String, String> attendance; // Key: studentId, Value: PRESENT / ABSENT

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public Map<String, String> getAttendance() { return attendance; }
        public void setAttendance(Map<String, String> attendance) { this.attendance = attendance; }
    }
}
