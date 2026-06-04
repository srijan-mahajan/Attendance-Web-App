package com.attendance.manager.repository;

import com.attendance.manager.model.Attendance;
import com.attendance.manager.model.Subject;
import com.attendance.manager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    List<Attendance> findBySubject(Subject subject);
    List<Attendance> findByStudent(User student);
    List<Attendance> findBySubjectAndStudent(Subject subject, User student);
    Optional<Attendance> findBySubjectAndStudentAndDate(Subject subject, User student, LocalDate date);
    
    long countBySubjectAndStudent(Subject subject, User student);
    long countBySubjectAndStudentAndStatus(Subject subject, User student, com.attendance.manager.model.AttendanceStatus status);
}
