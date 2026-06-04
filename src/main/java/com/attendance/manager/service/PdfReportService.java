package com.attendance.manager.service;

import com.attendance.manager.model.Attendance;
import com.attendance.manager.model.AttendanceStatus;
import com.attendance.manager.model.Subject;
import com.attendance.manager.model.User;
import com.attendance.manager.repository.AttendanceRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class PdfReportService {

    @Autowired
    private AttendanceRepository attendanceRepository;

    public ByteArrayInputStream generateAttendanceReport(Subject subject) {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Document Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
            Paragraph title = new Paragraph("Attendance Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Metadata / Information Block
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Paragraph info = new Paragraph();
            info.add(new Chunk("Subject: ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            info.add(new Chunk(subject.getName() + "\n", metaFont));
            info.add(new Chunk("Teacher: ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            info.add(new Chunk(subject.getTeacher().getUsername() + " (" + subject.getTeacher().getEmail() + ")\n", metaFont));
            info.add(new Chunk("Attendance Limit: ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            info.add(new Chunk(String.format("%.1f%%", subject.getAttendanceLimit()) + "\n", metaFont));
            info.add(new Chunk("Date Generated: ", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            info.add(new Chunk(LocalDate.now().toString() + "\n", metaFont));
            info.setSpacingAfter(20);
            document.add(info);

            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 3f, 2.5f, 1.5f, 4f, 2f, 2f, 2.5f, 2.5f});

            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Color headerBg = new Color(52, 152, 219);

            String[] headers = {"Roll No", "Student Name", "Branch", "Sem", "Email", "Total Classes", "Present Count", "Percentage", "Status"};
            for (String header : headers) {
                PdfPCell headerCell = new PdfPCell(new Phrase(header, headFont));
                headerCell.setBackgroundColor(headerBg);
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                headerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                headerCell.setPadding(6);
                table.addCell(headerCell);
            }

            Set<User> students = subject.getStudents();
            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font warningFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(192, 57, 43)); // Red
            Font okFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(39, 174, 96)); // Green

            for (User student : students) {
                long totalClasses = attendanceRepository.countBySubjectAndStudent(subject, student);
                long presentClasses = attendanceRepository.countBySubjectAndStudentAndStatus(subject, student, AttendanceStatus.PRESENT);

                double percentage = 100.0;
                if (totalClasses > 0) {
                    percentage = (double) presentClasses / totalClasses * 100.0;
                }

                PdfPCell cellRoll = new PdfPCell(new Phrase(student.getRollNumber() != null ? student.getRollNumber() : "N/A", dataFont));
                cellRoll.setPadding(5);
                table.addCell(cellRoll);

                PdfPCell cellName = new PdfPCell(new Phrase(student.getUsername(), dataFont));
                cellName.setPadding(5);
                table.addCell(cellName);

                PdfPCell cellBranch = new PdfPCell(new Phrase(student.getBranch() != null ? student.getBranch() : "-", dataFont));
                cellBranch.setPadding(5);
                table.addCell(cellBranch);

                PdfPCell cellSem = new PdfPCell(new Phrase(student.getSemester() != null ? String.valueOf(student.getSemester()) : "-", dataFont));
                cellSem.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellSem.setPadding(5);
                table.addCell(cellSem);

                PdfPCell cellEmail = new PdfPCell(new Phrase(student.getEmail(), dataFont));
                cellEmail.setPadding(5);
                table.addCell(cellEmail);

                // Total Classes
                PdfPCell cellTotal = new PdfPCell(new Phrase(String.valueOf(totalClasses), dataFont));
                cellTotal.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellTotal.setPadding(5);
                table.addCell(cellTotal);

                // Present Count
                PdfPCell cellPresent = new PdfPCell(new Phrase(String.valueOf(presentClasses), dataFont));
                cellPresent.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellPresent.setPadding(5);
                table.addCell(cellPresent);

                // Percentage
                PdfPCell cellPerc = new PdfPCell(new Phrase(String.format("%.1f%%", percentage), dataFont));
                cellPerc.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellPerc.setPadding(5);
                table.addCell(cellPerc);

                // Status
                String statusStr = "OK";
                Font statusFont = okFont;
                if (percentage < subject.getAttendanceLimit() && totalClasses > 0) {
                    statusStr = "Low Attendance";
                    statusFont = warningFont;
                } else if (totalClasses == 0) {
                    statusStr = "No Classes";
                    statusFont = dataFont;
                }

                PdfPCell cellStatus = new PdfPCell(new Phrase(statusStr, statusFont));
                cellStatus.setHorizontalAlignment(Element.ALIGN_CENTER);
                cellStatus.setPadding(5);
                table.addCell(cellStatus);
            }

            document.add(table);
            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream generateOverallAttendanceReport(List<User> students, List<Subject> allSubjects) {
        Document document = new Document();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
            Paragraph title = new Paragraph("Overall Consolidated Attendance Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // Date
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
            Paragraph info = new Paragraph("Generated Date: " + LocalDate.now().toString() + "\n\n", metaFont);
            info.setAlignment(Element.ALIGN_CENTER);
            document.add(info);

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 3f, 2.5f, 1.5f, 4f, 2.5f, 2.5f, 3f});

            Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
            Color headerBg = new Color(106, 17, 203);

            String[] headers = {"Roll No", "Student", "Branch", "Sem", "Email", "Total Classes", "Present Count", "Overall %"};
            for (String header : headers) {
                PdfPCell headerCell = new PdfPCell(new Phrase(header, headFont));
                headerCell.setBackgroundColor(headerBg);
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                headerCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                headerCell.setPadding(6);
                table.addCell(headerCell);
            }

            Font dataFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            Font warningFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(192, 57, 43)); // Red
            Font okFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, new Color(39, 174, 96)); // Green

            for (User student : students) {
                long totalClasses = 0;
                long presentClasses = 0;
                boolean belowAnyLimit = false;

                for (Subject subject : allSubjects) {
                    if (subject.getStudents().stream().anyMatch(s -> s.getId().equals(student.getId()))) {
                        long subTotal = attendanceRepository.countBySubjectAndStudent(subject, student);
                        long subPresent = attendanceRepository.countBySubjectAndStudentAndStatus(subject, student, AttendanceStatus.PRESENT);
                        double subPercentage = 100.0;
                        if (subTotal > 0) {
                            subPercentage = (double) subPresent / subTotal * 100.0;
                        }
                        if (subPercentage < subject.getAttendanceLimit() && subTotal > 0) {
                            belowAnyLimit = true;
                        }
                        totalClasses += subTotal;
                        presentClasses += subPresent;
                    }
                }

                double overallPercentage = 100.0;
                if (totalClasses > 0) {
                    overallPercentage = (double) presentClasses / totalClasses * 100.0;
                }

                table.addCell(new PdfPCell(new Phrase(student.getRollNumber() != null ? student.getRollNumber() : "N/A", dataFont)));
                table.addCell(new PdfPCell(new Phrase(student.getUsername(), dataFont)));
                table.addCell(new PdfPCell(new Phrase(student.getBranch() != null ? student.getBranch() : "-", dataFont)));
                table.addCell(new PdfPCell(new Phrase(student.getSemester() != null ? String.valueOf(student.getSemester()) : "-", dataFont)));
                table.addCell(new PdfPCell(new Phrase(student.getEmail(), dataFont)));

                PdfPCell cellTotal = new PdfPCell(new Phrase(String.valueOf(totalClasses), dataFont));
                cellTotal.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellTotal);

                PdfPCell cellPresent = new PdfPCell(new Phrase(String.valueOf(presentClasses), dataFont));
                cellPresent.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellPresent);

                Font percentageFont = belowAnyLimit ? warningFont : okFont;
                PdfPCell cellPerc = new PdfPCell(new Phrase(String.format("%.1f%%", overallPercentage) + (belowAnyLimit ? " (Low)" : ""), percentageFont));
                cellPerc.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cellPerc);
            }

            document.add(table);
            document.close();

        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return new ByteArrayInputStream(out.toByteArray());
    }
}
