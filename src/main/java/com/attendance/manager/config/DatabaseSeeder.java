package com.attendance.manager.config;

import com.attendance.manager.model.Role;
import com.attendance.manager.model.Subject;
import com.attendance.manager.model.User;
import com.attendance.manager.repository.SubjectRepository;
import com.attendance.manager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements ApplicationRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (userRepository.count() == 0) {
            System.out.println("No users found in database. Seeding default Admin, Teacher, and Student...");

            User admin = new User(
                    "admin",
                    passwordEncoder.encode("admin123"),
                    "admin@attendance.com",
                    Role.ROLE_ADMIN
            );
            userRepository.save(admin);

            User teacher = new User(
                    "teacher",
                    passwordEncoder.encode("teacher123"),
                    "teacher@attendance.com",
                    Role.ROLE_TEACHER
            );
            userRepository.save(teacher);

            User student = new User(
                    "student",
                    passwordEncoder.encode("student123"),
                    "student@attendance.com",
                    Role.ROLE_STUDENT,
                    "R-001",
                    "Computer Science",
                    4
            );
            userRepository.save(student);

            // Seed a default subject for demonstration
            Subject subject = new Subject("Calculus I", teacher);
            subject.setAttendanceLimit(75.0);
            subject.getStudents().add(student);
            subjectRepository.save(subject);

            System.out.println("Database seeded successfully.");
            System.out.println("Default Credentials:");
            System.out.println("- Admin: admin / admin123");
            System.out.println("- Teacher: teacher / teacher123");
            System.out.println("- Student: student / student123");
        }
    }
}
