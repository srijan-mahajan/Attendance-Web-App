package com.attendance.manager.controller;

import com.attendance.manager.model.AttendanceStatus;
import com.attendance.manager.model.Subject;
import com.attendance.manager.model.User;
import com.attendance.manager.repository.AttendanceRepository;
import com.attendance.manager.repository.SubjectRepository;
import com.attendance.manager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

    @GetMapping("/attendance")
    public ResponseEntity<?> getMyAttendance(@AuthenticationPrincipal UserDetails userDetails) {
        User student = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        List<Subject> allSubjects = subjectRepository.findAll();
        List<Map<String, Object>> resultList = new ArrayList<>();

        for (Subject subject : allSubjects) {
            if (subject.getStudents().stream().anyMatch(s -> s.getId().equals(student.getId()))) {
                long totalClasses = attendanceRepository.countBySubjectAndStudent(subject, student);
                long presentClasses = attendanceRepository.countBySubjectAndStudentAndStatus(subject, student, AttendanceStatus.PRESENT);

                double percentage = 100.0;
                if (totalClasses > 0) {
                    percentage = (double) presentClasses / totalClasses * 100.0;
                }

                Map<String, Object> item = new HashMap<>();
                item.put("subjectId", subject.getId());
                item.put("subjectName", subject.getName());
                item.put("teacherName", subject.getTeacher().getUsername());
                item.put("totalClasses", totalClasses);
                item.put("presentClasses", presentClasses);
                item.put("attendancePercentage", percentage);
                item.put("attendanceLimit", subject.getAttendanceLimit());
                item.put("belowLimit", percentage < subject.getAttendanceLimit() && totalClasses > 0);

                resultList.add(item);
            }
        }

        return ResponseEntity.ok(resultList);
    }
}
