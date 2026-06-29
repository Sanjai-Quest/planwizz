package com.planwizz.timetable.controller;

import com.planwizz.timetable.model.Course;
import com.planwizz.timetable.model.PreferenceRequest;
import com.planwizz.timetable.model.TextUploadRequest;
import com.planwizz.timetable.service.CspSolverService;
import com.planwizz.timetable.service.PdfExtractorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TimetableController {

    private final PdfExtractorService pdfExtractorService;
    private final CspSolverService cspSolverService;

    @Autowired
    public TimetableController(PdfExtractorService pdfExtractorService, CspSolverService cspSolverService) {
        this.pdfExtractorService = pdfExtractorService;
        this.cspSolverService = cspSolverService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getOriginalFilename() == null ||
                !file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Invalid file type. Please upload a PDF."));
        }

        try {
            byte[] bytes = file.getBytes();
            String text = pdfExtractorService.extractTextFromPdf(bytes);
            List<Course> courses = pdfExtractorService.extractCoursesFromText(text);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("courses", courses);
            response.put("raw_text", text);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to process PDF: " + e.getMessage()));
        }
    }

    @PostMapping("/upload-text")
    public ResponseEntity<?> uploadText(@RequestBody TextUploadRequest request) {
        if (request == null || request.getText() == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Request body or text field is missing."));
        }

        try {
            List<Course> courses = pdfExtractorService.extractCoursesFromText(request.getText());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("courses", courses);
            response.put("raw_text", request.getText());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to process text: " + e.getMessage()));
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateTimetable(@RequestBody PreferenceRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Request body is missing."));
        }

        try {
            Map<String, Object> result = cspSolverService.solve(
                    request.getSelectedSubjects(),
                    request.getCoursesData(),
                    request.getLeaveDay(),
                    request.getPreferredFaculties()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to generate timetable: " + e.getMessage()));
        }
    }

    @PostMapping("/check-compatibility")
    public ResponseEntity<?> checkCompatibility(@RequestBody PreferenceRequest request) {
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Request body is missing."));
        }

        try {
            List<String> compatible = cspSolverService.checkCompatibility(
                    request.getSelectedSubjects(),
                    request.getCoursesData(),
                    request.getLeaveDay(),
                    request.getPreferredFaculties()
            );
            return ResponseEntity.ok(Map.of("compatible_subjects", compatible));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("detail", "Failed to check compatibility: " + e.getMessage()));
        }
    }
}
