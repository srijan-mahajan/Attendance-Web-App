package com.attendance.manager.repository;

import com.attendance.manager.model.Subject;
import com.attendance.manager.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByTeacher(User teacher);
}
