package com.attendance.manager.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "subject_students",
        joinColumns = @JoinColumn(name = "subject_id"),
        inverseJoinColumns = @JoinColumn(name = "student_id")
    )
    private Set<User> students = new HashSet<>();

    @Column(name = "attendance_limit", nullable = false)
    private double attendanceLimit = 75.0; // Default limit is 75%

    // Constructors
    public Subject() {}

    public Subject(String name, User teacher) {
        this.name = name;
        this.teacher = teacher;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public User getTeacher() {
        return teacher;
    }

    public void setTeacher(User teacher) {
        this.teacher = teacher;
    }

    public Set<User> getStudents() {
        return students;
    }

    public void setStudents(Set<User> students) {
        this.students = students;
    }

    public double getAttendanceLimit() {
        return attendanceLimit;
    }

    public void setAttendanceLimit(double attendanceLimit) {
        this.attendanceLimit = attendanceLimit;
    }
}
