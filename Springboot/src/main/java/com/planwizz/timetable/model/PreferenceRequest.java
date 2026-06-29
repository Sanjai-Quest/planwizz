package com.planwizz.timetable.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PreferenceRequest {
    @JsonProperty("selected_subjects")
    private List<String> selectedSubjects;

    @JsonProperty("courses_data")
    private List<Course> coursesData;

    @JsonProperty("leave_day")
    private String leaveDay;

    @JsonProperty("preferred_faculties")
    private Map<String, String> preferredFaculties = new HashMap<>();

    public PreferenceRequest() {
    }

    public List<String> getSelectedSubjects() {
        return selectedSubjects;
    }

    public void setSelectedSubjects(List<String> selectedSubjects) {
        this.selectedSubjects = selectedSubjects;
    }

    public List<Course> getCoursesData() {
        return coursesData;
    }

    public void setCoursesData(List<Course> coursesData) {
        this.coursesData = coursesData;
    }

    public String getLeaveDay() {
        return leaveDay;
    }

    public void setLeaveDay(String leaveDay) {
        this.leaveDay = leaveDay;
    }

    public Map<String, String> getPreferredFaculties() {
        return preferredFaculties;
    }

    public void setPreferredFaculties(Map<String, String> preferredFaculties) {
        this.preferredFaculties = preferredFaculties;
    }
}
