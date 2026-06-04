package com.attendance.manager.controller;

import com.attendance.manager.model.Role;
import com.attendance.manager.model.Subject;
import com.attendance.manager.model.User;
import com.attendance.manager.repository.SubjectRepository;
import com.attendance.manager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @GetMapping("/teachers")
    public ResponseEntity<List<User>> getTeachers() {
        return ResponseEntity.ok(userRepository.findByRole(Role.ROLE_TEACHER));
    }

    @GetMapping("/students")
    public ResponseEntity<List<User>> getStudents() {
        return ResponseEntity.ok(userRepository.findByRole(Role.ROLE_STUDENT));
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody CreateUserRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Username is already taken");
        }
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email is already taken");
        }

        Role userRole;
        try {
            String roleStr = request.getRole().toUpperCase();
            if (!roleStr.startsWith("ROLE_")) {
                roleStr = "ROLE_" + roleStr;
            }
            userRole = Role.valueOf(roleStr);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid role. Must be ROLE_ADMIN, ROLE_TEACHER, or ROLE_STUDENT");
        }

        User newUser = new User(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getEmail(),
                userRole,
                request.getRollNumber(),
                request.getBranch(),
                request.getSemester()
        );
        userRepository.save(newUser);
        return ResponseEntity.ok(newUser);
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<Subject>> getAllSubjects() {
        return ResponseEntity.ok(subjectRepository.findAll());
    }

    @PostMapping("/subjects")
    public ResponseEntity<?> createSubject(@RequestBody CreateSubjectRequest request) {
        User teacher = userRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        if (teacher.getRole() != Role.ROLE_TEACHER) {
            return ResponseEntity.badRequest().body("Assigned user is not a teacher");
        }

        Subject subject = new Subject(request.getName(), teacher);
        if (request.getAttendanceLimit() > 0) {
            subject.setAttendanceLimit(request.getAttendanceLimit());
        }
        subjectRepository.save(subject);
        return ResponseEntity.ok(subject);
    }

    @PostMapping("/subjects/{subjectId}/enroll")
    public ResponseEntity<?> enrollStudent(@PathVariable Long subjectId, @RequestParam Long studentId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        if (student.getRole() != Role.ROLE_STUDENT) {
            return ResponseEntity.badRequest().body("User to enroll must be a student");
        }

        subject.getStudents().add(student);
        subjectRepository.save(subject);
        return ResponseEntity.ok("Student enrolled in subject successfully");
    }

    public static class CreateUserRequest {
        private String username;
        private String password;
        private String email;
        private String role; // ROLE_ADMIN, ROLE_TEACHER, ROLE_STUDENT
        private String rollNumber;
        private String branch;
        private Integer semester;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getRollNumber() { return rollNumber; }
        public void setRollNumber(String rollNumber) { this.rollNumber = rollNumber; }
        public String getBranch() { return branch; }
        public void setBranch(String branch) { this.branch = branch; }
        public Integer getSemester() { return semester; }
        public void setSemester(Integer semester) { this.semester = semester; }
    }

    public static class CreateSubjectRequest {
        private String name;
        private Long teacherId;
        private double attendanceLimit = 75.0;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getTeacherId() { return teacherId; }
        public void setTeacherId(Long teacherId) { this.teacherId = teacherId; }
        public double getAttendanceLimit() { return attendanceLimit; }
        public void setAttendanceLimit(double attendanceLimit) { this.attendanceLimit = attendanceLimit; }
    }
}
