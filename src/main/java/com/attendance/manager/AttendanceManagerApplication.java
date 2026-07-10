package com.attendance.manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AttendanceManagerApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttendanceManagerApplication.class, args);
    }
}
