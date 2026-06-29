package com.planwizz.timetable.service;

import com.planwizz.timetable.model.Course;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CspSolverService {

    public Map<String, Object> solve(List<String> selectedSubjects, List<Course> coursesData, String leaveDay, Map<String, String> preferredFaculties) {
        SolverInstance instance = new SolverInstance(selectedSubjects, coursesData, leaveDay, preferredFaculties);
        return instance.solve();
    }

    public List<String> checkCompatibility(List<String> selectedSubjects, List<Course> coursesData, String leaveDay, Map<String, String> preferredFaculties) {
        Set<String> allSubjects = new HashSet<>();
        for (Course c : coursesData) {
            if (c.getCourse_name() != null) {
                allSubjects.add(c.getCourse_name());
            }
        }

        Set<String> candidates = new HashSet<>(allSubjects);
        if (selectedSubjects != null) {
            candidates.removeAll(selectedSubjects);
        }

        List<String> compatibleSubjects = new ArrayList<>();
        List<String> baseSelection = selectedSubjects != null ? selectedSubjects : new ArrayList<>();

        for (String cand : candidates) {
            List<String> tempSelection = new ArrayList<>(baseSelection);
            tempSelection.add(cand);

            SolverInstance instance = new SolverInstance(tempSelection, coursesData, leaveDay, preferredFaculties);
            if (instance.isSolvable()) {
                compatibleSubjects.add(cand);
            }
        }
        return compatibleSubjects;
    }

    private static class SolverInstance {
        private final List<String> selectedSubjects;
        private final List<Course> coursesData;
        private final String leaveDay;
        private final Map<String, String> preferredFaculties;

        private Map<String, List<List<Course>>> domains;
        private final Map<String, List<Course>> assignment = new LinkedHashMap<>();

        private record OptionKey(String courseName, String slot, String faculty) {}

        public SolverInstance(List<String> selectedSubjects, List<Course> coursesData, String leaveDay, Map<String, String> preferredFaculties) {
            this.selectedSubjects = selectedSubjects != null ? selectedSubjects : new ArrayList<>();
            this.coursesData = coursesData != null ? coursesData : new ArrayList<>();
            this.leaveDay = leaveDay;
            this.preferredFaculties = preferredFaculties != null ? preferredFaculties : new HashMap<>();
            
            buildDomains();
        }

        private void buildDomains() {
            Map<OptionKey, List<Course>> groupedOptions = new LinkedHashMap<>();
            for (Course c : coursesData) {
                if (c.getCourse_name() == null || !selectedSubjects.contains(c.getCourse_name())) {
                    continue;
                }
                OptionKey key = new OptionKey(c.getCourse_name(), c.getSlot(), c.getFaculty());
                groupedOptions.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
            }

            domains = new LinkedHashMap<>();
            for (Map.Entry<OptionKey, List<Course>> entry : groupedOptions.entrySet()) {
                OptionKey key = entry.getKey();
                List<Course> segments = entry.getValue();

                boolean hasLeaveDay = false;
                if (leaveDay != null && !leaveDay.trim().isEmpty()) {
                    for (Course seg : segments) {
                        if (leaveDay.equalsIgnoreCase(seg.getDay())) {
                            hasLeaveDay = true;
                            break;
                        }
                    }
                }
                if (hasLeaveDay) {
                    continue;
                }

                domains.computeIfAbsent(key.courseName(), k -> new ArrayList<>()).add(segments);
            }

            for (Map.Entry<String, List<List<Course>>> entry : domains.entrySet()) {
                String subject = entry.getKey();
                List<List<Course>> options = entry.getValue();
                String preferred = preferredFaculties.get(subject);
                if (preferred != null) {
                    options.sort((segs1, segs2) -> {
                        String f1 = segs1.isEmpty() ? "" : segs1.get(0).getFaculty();
                        String f2 = segs2.isEmpty() ? "" : segs2.get(0).getFaculty();
                        int score1 = (f1 != null && f1.equalsIgnoreCase(preferred)) ? 0 : 1;
                        int score2 = (f2 != null && f2.equalsIgnoreCase(preferred)) ? 0 : 1;
                        return Integer.compare(score1, score2);
                    });
                }
            }
        }

        public boolean isSolvable() {
            for (String subject : selectedSubjects) {
                if (!domains.containsKey(subject) || domains.get(subject).isEmpty()) {
                    return false;
                }
            }
            return backtrack();
        }

        public Map<String, Object> solve() {
            for (String subject : selectedSubjects) {
                if (!domains.containsKey(subject) || domains.get(subject).isEmpty()) {
                    Map<String, Object> errorResult = new LinkedHashMap<>();
                    if (leaveDay != null && !leaveDay.trim().isEmpty()) {
                        errorResult.put("status", "conflict");
                        errorResult.put("reason", "Subject '" + subject + "' is only available on your requested Leave Day (" + leaveDay + ").");
                        errorResult.put("suggestion", "Please choose a different leave day or remove this subject.");
                    } else {
                        errorResult.put("status", "error");
                        errorResult.put("reason", "No slots found for '" + subject + "'.");
                        errorResult.put("suggestion", "Check input data.");
                    }
                    return errorResult;
                }
            }

            Map<String, List<Map<String, String>>> debugDomains = new LinkedHashMap<>();
            for (Map.Entry<String, List<List<Course>>> entry : domains.entrySet()) {
                String subj = entry.getKey();
                List<List<Course>> options = entry.getValue();
                List<Map<String, String>> flatSlots = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (List<Course> segments : options) {
                    for (Course seg : segments) {
                        String uniqueKey = seg.getDay() + "|" + seg.getStart_time() + "|" + seg.getEnd_time();
                        if (!seen.contains(uniqueKey)) {
                            Map<String, String> slotMap = new LinkedHashMap<>();
                            slotMap.put("Day", seg.getDay());
                            slotMap.put("Time", seg.getStart_time() + " - " + seg.getEnd_time());
                            slotMap.put("Slot", seg.getSlot());
                            slotMap.put("Faculty", seg.getFaculty());
                            flatSlots.add(slotMap);
                            seen.add(uniqueKey);
                        }
                    }
                }
                debugDomains.put(subj, flatSlots);
            }

            if (backtrack()) {
                List<String> changes = new ArrayList<>();
                for (Map.Entry<String, List<Course>> entry : assignment.entrySet()) {
                    String subj = entry.getKey();
                    List<Course> segments = entry.getValue();
                    if (!segments.isEmpty()) {
                        String assignedFaculty = segments.get(0).getFaculty();
                        String preferred = preferredFaculties.get(subj);
                        if (preferred != null && !preferred.equalsIgnoreCase(assignedFaculty)) {
                            changes.add(subj + " (" + assignedFaculty + ")");
                        }
                    }
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("timetable", formatAssignment());
                result.put("all_possible_slots", debugDomains);

                if (!changes.isEmpty()) {
                    result.put("status", "success_with_adjustment");
                    result.put("message", "Auto-adjusted faculty to fit schedule: " + String.join(", ", changes) + ".");
                } else {
                    result.put("status", "success");
                }
                return result;
            }

            List<Map<String, Object>> potential = solveIgnoringLeaveDay();
            if (potential != null) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", "success_with_adjustment");
                result.put("timetable", potential);
                result.put("message", "Couldn't fit all classes on your Leave Day (" + leaveDay + "). Adjusted schedule options.");
                result.put("all_possible_slots", debugDomains);
                return result;
            }

            List<Map<String, Object>> conflictInfo = diagnoseConflictDetailed();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "conflict");
            result.put("reason", "Scheduling conflict detected.");
            result.put("conflict_details", conflictInfo);
            result.put("suggestion", generateSuggestions());
            result.put("all_possible_slots", debugDomains);
            return result;
        }

        private boolean backtrack() {
            if (assignment.size() == selectedSubjects.size()) {
                return true;
            }

            List<String> unassigned = new ArrayList<>();
            for (String s : selectedSubjects) {
                if (!assignment.containsKey(s)) {
                    unassigned.add(s);
                }
            }

            String var = null;
            int minSize = Integer.MAX_VALUE;
            for (String s : unassigned) {
                List<List<Course>> opts = domains.get(s);
                int size = (opts != null) ? opts.size() : 0;
                if (size < minSize) {
                    minSize = size;
                    var = s;
                }
            }

            if (var == null) {
                return false;
            }

            List<List<Course>> options = domains.get(var);
            if (options != null) {
                for (List<Course> value : options) {
                    if (isConsistent(var, value)) {
                        assignment.put(var, value);
                        if (backtrack()) {
                            return true;
                        }
                        assignment.remove(var);
                    }
                }
            }
            return false;
        }

        private boolean isConsistent(String var, List<Course> valueSegments) {
            for (Map.Entry<String, List<Course>> entry : assignment.entrySet()) {
                List<Course> assignedSegments = entry.getValue();
                for (Course newSeg : valueSegments) {
                    for (Course existingSeg : assignedSegments) {
                        if (newSeg.getDay() != null && newSeg.getDay().equalsIgnoreCase(existingSeg.getDay())) {
                            if (checkOverlap(newSeg.getStart_time(), newSeg.getEnd_time(),
                                             existingSeg.getStart_time(), existingSeg.getEnd_time())) {
                                return false;
                            }
                        }
                        if (newSeg.getFaculty() != null && newSeg.getFaculty().equalsIgnoreCase(existingSeg.getFaculty())) {
                            if (newSeg.getDay() != null && newSeg.getDay().equalsIgnoreCase(existingSeg.getDay())) {
                                if (checkOverlap(newSeg.getStart_time(), newSeg.getEnd_time(),
                                                 existingSeg.getStart_time(), existingSeg.getEnd_time())) {
                                    return false;
                                }
                            }
                        }
                    }
                }
            }
            return true;
        }

        private boolean checkOverlap(String start1, String end1, String start2, String end2) {
            if (start1 == null || end1 == null || start2 == null || end2 == null) {
                return false;
            }
            return (start1.compareTo(end2) < 0) && (start2.compareTo(end1) < 0);
        }

        private List<Map<String, Object>> formatAssignment() {
            List<Map<String, Object>> output = new ArrayList<>();
            for (Map.Entry<String, List<Course>> entry : assignment.entrySet()) {
                String subject = entry.getKey();
                List<Course> segments = entry.getValue();
                for (Course seg : segments) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("course_name", seg.getCourse_name() != null ? seg.getCourse_name() : subject);
                    map.put("course_code", seg.getCourse_code() != null ? seg.getCourse_code() : "");
                    map.put("faculty", seg.getFaculty());
                    map.put("day", seg.getDay());
                    map.put("time", seg.getStart_time() + " - " + seg.getEnd_time());
                    map.put("venue", seg.getSlot());
                    output.add(map);
                }
            }
            return output;
        }

        private List<Map<String, Object>> solveIgnoringLeaveDay() {
            Map<String, List<List<Course>>> originalDomains = this.domains;
            Map<String, List<Course>> originalAssignment = new LinkedHashMap<>(this.assignment);

            Map<OptionKey, List<Course>> groupedOptions = new LinkedHashMap<>();
            for (Course c : coursesData) {
                if (c.getCourse_name() != null && selectedSubjects.contains(c.getCourse_name())) {
                    OptionKey key = new OptionKey(c.getCourse_name(), c.getSlot(), c.getFaculty());
                    groupedOptions.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
                }
            }

            Map<String, List<List<Course>>> newDomains = new LinkedHashMap<>();
            for (Map.Entry<OptionKey, List<Course>> entry : groupedOptions.entrySet()) {
                newDomains.computeIfAbsent(entry.getKey().courseName(), k -> new ArrayList<>()).add(entry.getValue());
            }

            for (Map.Entry<String, List<List<Course>>> entry : newDomains.entrySet()) {
                String subject = entry.getKey();
                List<List<Course>> options = entry.getValue();
                String preferred = preferredFaculties.get(subject);
                if (preferred != null) {
                    options.sort((segs1, segs2) -> {
                        String f1 = segs1.isEmpty() ? "" : segs1.get(0).getFaculty();
                        String f2 = segs2.isEmpty() ? "" : segs2.get(0).getFaculty();
                        int score1 = (f1 != null && f1.equalsIgnoreCase(preferred)) ? 0 : 1;
                        int score2 = (f2 != null && f2.equalsIgnoreCase(preferred)) ? 0 : 1;
                        return Integer.compare(score1, score2);
                    });
                }
            }

            this.domains = newDomains;
            this.assignment.clear();

            List<Map<String, Object>> res = null;
            if (this.backtrack()) {
                res = this.formatAssignment();
            }

            this.domains = originalDomains;
            this.assignment.clear();
            this.assignment.putAll(originalAssignment);
            return res;
        }

        private List<Map<String, Object>> diagnoseConflictDetailed() {
            List<Map<String, Object>> conflicts = new ArrayList<>();

            List<String> subjects = this.selectedSubjects;
            for (int i = 0; i < subjects.size(); i++) {
                for (int j = i + 1; j < subjects.size(); j++) {
                    String s1 = subjects.get(i);
                    String s2 = subjects.get(j);

                    List<List<Course>> s1Options = domains.get(s1);
                    List<List<Course>> s2Options = domains.get(s2);

                    if (s1Options == null || s2Options == null || s1Options.isEmpty() || s2Options.isEmpty()) {
                        continue;
                    }

                    boolean totalClash = true;
                    Map<String, Object> exampleClash = null;

                    for (List<Course> opt1 : s1Options) {
                        for (List<Course> opt2 : s2Options) {
                            boolean currentPairClash = false;
                            for (Course seg1 : opt1) {
                                for (Course seg2 : opt2) {
                                    if (seg1.getDay() != null && seg1.getDay().equalsIgnoreCase(seg2.getDay())) {
                                        if (checkOverlap(seg1.getStart_time(), seg1.getEnd_time(),
                                                         seg2.getStart_time(), seg2.getEnd_time())) {
                                            currentPairClash = true;
                                            exampleClash = new LinkedHashMap<>();
                                            exampleClash.put("Subject1", s1);
                                            exampleClash.put("Subject2", s2);
                                            exampleClash.put("Day", seg1.getDay());
                                            exampleClash.put("Time", seg1.getStart_time() + " - " + seg1.getEnd_time());
                                            break;
                                        }
                                    }
                                }
                                if (currentPairClash) break;
                            }
                            if (!currentPairClash) {
                                totalClash = false;
                                break;
                            }
                        }
                        if (!totalClash) break;
                    }

                    if (totalClash) {
                        Map<String, Object> conflict = new LinkedHashMap<>();
                        conflict.put("type", "hard_overlap");
                        conflict.put("subjects", List.of(s1, s2));
                        conflict.put("message", "Conflict: '" + s1 + "' and '" + s2 + "' always overlap.");
                        conflict.put("example_clash", exampleClash);
                        conflicts.add(conflict);
                    }
                }
            }

            if (conflicts.isEmpty()) {
                Map<String, Object> generalConflict = new LinkedHashMap<>();
                generalConflict.put("type", "general");
                generalConflict.put("message", "Complex constraint failure (no direct pair overlap found).");
                return List.of(generalConflict);
            }

            return conflicts;
        }

        private String generateSuggestions() {
            // Strategy 2: Relax Leave Day
            Map<OptionKey, List<Course>> groupedOptionsNoLeave = new LinkedHashMap<>();
            for (Course c : coursesData) {
                if (c.getCourse_name() != null && selectedSubjects.contains(c.getCourse_name())) {
                    OptionKey key = new OptionKey(c.getCourse_name(), c.getSlot(), c.getFaculty());
                    groupedOptionsNoLeave.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
                }
            }
            Map<String, List<List<Course>>> tempDomains = new LinkedHashMap<>();
            for (Map.Entry<OptionKey, List<Course>> entry : groupedOptionsNoLeave.entrySet()) {
                tempDomains.computeIfAbsent(entry.getKey().courseName(), k -> new ArrayList<>()).add(entry.getValue());
            }

            if (trySolveCustomDomains(tempDomains, selectedSubjects)) {
                return "We found a valid timetable if you are willing to attend classes on " + leaveDay + " (your preferred leave).";
            }

            // Strategy 3: Identify the 'Dealbreaker' Subject
            for (String subjectToRemove : selectedSubjects) {
                List<String> remaining = new ArrayList<>(selectedSubjects);
                remaining.remove(subjectToRemove);

                Map<String, List<List<Course>>> remDomains = new LinkedHashMap<>();
                for (String s : remaining) {
                    if (domains.containsKey(s)) {
                        remDomains.put(s, domains.get(s));
                    }
                }

                if (trySolveCustomDomains(remDomains, remaining)) {
                    return "Conflict resolved if you remove '" + subjectToRemove + "'.";
                }
            }

            return "The combination of subjects selected is heavily conflicted. Try selecting fewer subjects.";
        }

        private boolean trySolveCustomDomains(Map<String, List<List<Course>>> customDomains, List<String> customSubjects) {
            Map<String, List<Course>> tempAssignment = new LinkedHashMap<>();
            return backtrackCustom(customDomains, customSubjects, tempAssignment);
        }

        private boolean backtrackCustom(Map<String, List<List<Course>>> customDomains, List<String> customSubjects, Map<String, List<Course>> tempAssignment) {
            if (tempAssignment.size() == customSubjects.size()) {
                return true;
            }

            String var = null;
            for (String s : customSubjects) {
                if (!tempAssignment.containsKey(s)) {
                    var = s;
                    break;
                }
            }

            if (var == null) return false;

            List<List<Course>> options = customDomains.get(var);
            if (options != null) {
                for (List<Course> valueSegments : options) {
                    boolean isSafe = true;
                    for (List<Course> assignedSegments : tempAssignment.values()) {
                        for (Course newSeg : valueSegments) {
                            for (Course existingSeg : assignedSegments) {
                                if (newSeg.getDay() != null && newSeg.getDay().equalsIgnoreCase(existingSeg.getDay())) {
                                    if (checkOverlap(newSeg.getStart_time(), newSeg.getEnd_time(),
                                                     existingSeg.getStart_time(), existingSeg.getEnd_time())) {
                                        isSafe = false;
                                        break;
                                    }
                                }
                                if (newSeg.getFaculty() != null && newSeg.getFaculty().equalsIgnoreCase(existingSeg.getFaculty())) {
                                    if (newSeg.getDay() != null && newSeg.getDay().equalsIgnoreCase(existingSeg.getDay())) {
                                        if (checkOverlap(newSeg.getStart_time(), newSeg.getEnd_time(),
                                                         existingSeg.getStart_time(), existingSeg.getEnd_time())) {
                                            isSafe = false;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!isSafe) break;
                        }
                        if (!isSafe) break;
                    }

                    if (isSafe) {
                        tempAssignment.put(var, valueSegments);
                        if (backtrackCustom(customDomains, customSubjects, tempAssignment)) {
                            return true;
                        }
                        tempAssignment.remove(var);
                    }
                }
            }
            return false;
        }
    }
}
