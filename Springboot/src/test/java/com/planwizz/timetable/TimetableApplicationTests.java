package com.planwizz.timetable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planwizz.timetable.model.Course;
import com.planwizz.timetable.model.PreferenceRequest;
import com.planwizz.timetable.model.TextUploadRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TimetableApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testHealthCheck() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testUploadTextAndGenerate() throws Exception {
        // Sample text representing a course structure from the enrollment PDF
        String sampleText = "22IT301 [3 Credits]\n" +
                "Course overview\n" +
                "Software Engineering\n" +
                "UG - 04, T1-B13, MECH - MUTHUKUMAR V\n" +
                "Monday: 08:00 - 08:50\n" +
                "Wednesday: 08:55 - 09:45\n";

        TextUploadRequest uploadRequest = new TextUploadRequest();
        uploadRequest.setText(sampleText);

        String uploadResponse = mockMvc.perform(post("/api/upload-text")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses[0].course_name", is("Software Engineering")))
                .andExpect(jsonPath("$.courses[0].course_code", is("22IT301")))
                .andExpect(jsonPath("$.courses[0].credits", is("3")))
                .andReturn().getResponse().getContentAsString();

        // Parse extracted courses
        Map<String, Object> uploadResultMap = objectMapper.readValue(uploadResponse, Map.class);
        List<Map<String, Object>> rawCourses = (List<Map<String, Object>>) uploadResultMap.get("courses");

        List<Course> courses = objectMapper.convertValue(
                rawCourses,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Course.class)
        );

        // Test Timetable Generation
        PreferenceRequest prefRequest = new PreferenceRequest();
        prefRequest.setSelectedSubjects(List.of("Software Engineering"));
        prefRequest.setCoursesData(courses);
        prefRequest.setLeaveDay("Tuesday");

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prefRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("success")))
                .andExpect(jsonPath("$.timetable[0].course_name", is("Software Engineering")));
    }
}
