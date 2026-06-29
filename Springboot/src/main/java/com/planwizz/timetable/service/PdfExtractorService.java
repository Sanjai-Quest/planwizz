package com.planwizz.timetable.service;

import com.planwizz.timetable.model.Course;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfExtractorService {

    private static final Pattern COURSE_HEADER = Pattern.compile("^(\\d{2}[A-Z]{2}\\d{3})\\s*\\[(\\d+)\\s*Credits\\]");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(PROFESSIONAL CORE|PROFESSIONAL ELECTIVE|OPEN ELECTIVE|ENGINEERING SCIENCES|HUMANITIES AND SCIENCES)"
    );
    private static final Pattern SLOT_FACULTY_PATTERN_NEW = Pattern.compile("^UG\\s*-\\s*\\d+,\\s*([A-Z0-9\\-]+),\\s*[A-Z]+\\s*-\\s*(.+)");
    private static final Pattern SLOT_FACULTY_PATTERN_OLD = Pattern.compile("^([A-Z0-9\\-]+),\\s*(.+)");
    private static final Pattern DAY_PATTERN = Pattern.compile("^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday):");
    private static final Pattern TIME_PAIR_PATTERN = Pattern.compile("(\\d{2}:\\d{2})\\s*-\\s*(\\d{2}:\\d{2})");

    private static final LocalTime VALID_START = LocalTime.of(8, 0);
    private static final LocalTime VALID_END = LocalTime.of(17, 0);

    private static class CurrentCourse {
        String courseCode;
        String credits;
        String courseName;
    }

    private static class RawRow {
        String courseName;
        String courseCode;
        String credits;
        String facultyName;
        String slotName;
        String day;
        String start;
        String end;
    }

    private record CourseKey(
            String courseName,
            String courseCode,
            String credits,
            String facultyName,
            String slotName,
            String day
    ) {}

    public static boolean isValidTime(String start, String end) {
        try {
            LocalTime s = LocalTime.parse(start);
            LocalTime e = LocalTime.parse(end);
            return !s.isBefore(VALID_START) && s.isBefore(e) && !e.isAfter(VALID_END);
        } catch (Exception ex) {
            return false;
        }
    }

    public static int toMinutes(String t) {
        String[] parts = t.split(":");
        int h = Integer.parseInt(parts[0]);
        int m = Integer.parseInt(parts[1]);
        return h * 60 + m;
    }

    public static String fromMinutes(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    public String extractTextFromPdf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    public List<Course> extractCoursesFromText(String text) {
        List<RawRow> rawRows = parsePdfText(text);
        List<Course> merged = mergeSlots(rawRows);
        return merged;
    }

    private List<RawRow> parsePdfText(String text) {
        List<RawRow> rows = new ArrayList<>();

        CurrentCourse currentCourse = new CurrentCourse();
        String currentSlot = null;
        String currentFaculty = null;
        boolean skipPhase = false;

        String[] lines = text.split("\n");
        List<String> trimmedLines = new ArrayList<>();
        for (String l : lines) {
            if (l != null && !l.trim().isEmpty()) {
                trimmedLines.add(l.trim());
            }
        }

        int i = 0;
        while (i < trimmedLines.size()) {
            String line = trimmedLines.get(i);

            // COURSE HEADER
            Matcher headerMatcher = COURSE_HEADER.matcher(line);
            if (headerMatcher.find()) {
                currentCourse.courseCode = headerMatcher.group(1);
                currentCourse.credits = headerMatcher.group(2);
                currentCourse.courseName = "";
                currentSlot = null;
                currentFaculty = null;
                skipPhase = false;
                i++;
                continue;
            }

            // IGNORE DOMAIN
            Matcher domainMatcher = DOMAIN_PATTERN.matcher(line);
            if (domainMatcher.find()) {
                i++;
                continue;
            }

            // COURSE NAME
            if ("Course overview".equals(line)) {
                if (i + 1 < trimmedLines.size()) {
                    currentCourse.courseName = trimmedLines.get(i + 1);
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }

            // IGNORE PHASE
            if (line.startsWith("PHASE")) {
                skipPhase = true;
                currentSlot = null;
                currentFaculty = null;
                i++;
                continue;
            }

            // SLOT + FACULTY
            Matcher slotNewMatcher = SLOT_FACULTY_PATTERN_NEW.matcher(line);
            if (slotNewMatcher.find()) {
                currentSlot = slotNewMatcher.group(1);
                currentFaculty = slotNewMatcher.group(2);
                skipPhase = false;
                i++;
                continue;
            }

            Matcher slotOldMatcher = SLOT_FACULTY_PATTERN_OLD.matcher(line);
            if (slotOldMatcher.find()) {
                currentSlot = slotOldMatcher.group(1);
                currentFaculty = slotOldMatcher.group(2);
                skipPhase = false;
                i++;
                continue;
            }

            // DAY + MULTI TIME
            Matcher dayMatcher = DAY_PATTERN.matcher(line);
            if (dayMatcher.find() && currentCourse.courseCode != null && currentSlot != null && !skipPhase) {
                String day = dayMatcher.group(1);
                Matcher timeMatcher = TIME_PAIR_PATTERN.matcher(line);
                while (timeMatcher.find()) {
                    String start = timeMatcher.group(1);
                    String end = timeMatcher.group(2);
                    if (isValidTime(start, end)) {
                        RawRow r = new RawRow();
                        r.courseName = (currentCourse.courseName == null || currentCourse.courseName.isEmpty()) ? "Unknown" : currentCourse.courseName;
                        r.courseCode = currentCourse.courseCode;
                        r.credits = currentCourse.credits;
                        r.facultyName = currentFaculty;
                        r.slotName = currentSlot;
                        r.day = day;
                        r.start = start;
                        r.end = end;
                        rows.add(r);
                    }
                }
            }

            i++;
        }

        System.out.println("[INFO] PDF extraction complete: " + rows.size() + " course slots found");
        return rows;
    }

    private List<Course> mergeSlots(List<RawRow> rows) {
        Map<CourseKey, List<int[]>> grouped = new LinkedHashMap<>();
        for (RawRow r : rows) {
            CourseKey key = new CourseKey(
                    r.courseName,
                    r.courseCode,
                    r.credits,
                    r.facultyName,
                    r.slotName,
                    r.day
            );
            int start = toMinutes(r.start);
            int end = toMinutes(r.end);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new int[]{start, end});
        }

        List<Course> finalCourses = new ArrayList<>();
        for (Map.Entry<CourseKey, List<int[]>> entry : grouped.entrySet()) {
            CourseKey key = entry.getKey();
            List<int[]> intervals = entry.getValue();
            intervals.sort(Comparator.comparingInt(a -> a[0]));

            List<int[]> merged = new ArrayList<>();
            for (int[] interval : intervals) {
                if (merged.isEmpty()) {
                    merged.add(new int[]{interval[0], interval[1]});
                } else {
                    int[] last = merged.get(merged.size() - 1);
                    if (interval[0] <= last[1]) {
                        last[1] = Math.max(last[1], interval[1]);
                    } else {
                        merged.add(new int[]{interval[0], interval[1]});
                    }
                }
            }

            for (int[] m : merged) {
                Course c = new Course();
                c.setCourse_name(key.courseName());
                c.setCourse_code(key.courseCode());
                c.setCredits(key.credits());
                c.setFaculty(key.facultyName());
                c.setSlot(key.slotName());
                c.setDay(key.day());
                c.setStart_time(fromMinutes(m[0]));
                c.setEnd_time(fromMinutes(m[1]));
                finalCourses.add(c);
            }
        }

        return finalCourses;
    }
}
