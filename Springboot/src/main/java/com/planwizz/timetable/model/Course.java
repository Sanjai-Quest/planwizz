package com.planwizz.timetable.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Course {
    @JsonProperty("course_name")
    private String course_name;

    @JsonProperty("course_code")
    private String course_code;

    @JsonProperty("credits")
    private String credits;

    @JsonProperty("faculty")
    private String faculty;

    @JsonProperty("slot")
    private String slot;

    @JsonProperty("day")
    private String day;

    @JsonProperty("start_time")
    private String start_time;

    @JsonProperty("end_time")
    private String end_time;

    public Course() {
    }

    public Course(String course_name, String course_code, String credits, String faculty, String slot, String day, String start_time, String end_time) {
        this.course_name = course_name;
        this.course_code = course_code;
        this.credits = credits;
        this.faculty = faculty;
        this.slot = slot;
        this.day = day;
        this.start_time = start_time;
        this.end_time = end_time;
    }

    // Getters and Setters
    public String getCourse_name() {
        return course_name;
    }

    public void setCourse_name(String course_name) {
        this.course_name = course_name;
    }

    public String getCourse_code() {
        return course_code;
    }

    public void setCourse_code(String course_code) {
        this.course_code = course_code;
    }

    public String getCredits() {
        return credits;
    }

    public void setCredits(String credits) {
        this.credits = credits;
    }

    public String getFaculty() {
        return faculty;
    }

    public void setFaculty(String faculty) {
        this.faculty = faculty;
    }

    public String getSlot() {
        return slot;
    }

    public void setSlot(String slot) {
        this.slot = slot;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getStart_time() {
        return start_time;
    }

    public void setStart_time(String start_time) {
        this.start_time = start_time;
    }

    public String getEnd_time() {
        return end_time;
    }

    public void setEnd_time(String end_time) {
        this.end_time = end_time;
    }
}
