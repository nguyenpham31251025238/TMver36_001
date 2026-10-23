package com.example.tmver36.service;
import com.example.tmver36.model.AppUser;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherProfile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PortalStore {

    private final Map<String, AppUser> users = new LinkedHashMap<>();
    private final List<TeacherProfile> teachers = new ArrayList<>();
    private final List<Interview> interviews = new ArrayList<>();
    private final List<String[]> logs = new ArrayList<>();

    // Stores access grants in the form username:teacherId.
    private final Set<String> granted = new HashSet<>();

    private final AtomicLong interviewIds = new AtomicLong(3);

    public PortalStore() {
        // Demo accounts
        users.put(
                "admin",
                new AppUser(
                        "admin",
                        "admin123",
                        "ADMIN",
                        "System Administrator"
                )
        );

        users.put(
                "staff",
                new AppUser(
                        "staff",
                        "staff123",
                        "STAFF",
                        "Matching Staff"
                )
        );

        users.put(
                "partner",
                new AppUser(
                        "partner",
                        "partner123",
                        "PARTNER",
                        "Bright English Center"
                )
        );

        users.put(
                "teacher",
                new AppUser(
                        "teacher",
                        "teacher123",
                        "TEACHER",
                        "Emily Martin"
                )
        );

        // Demo teacher profiles
        teachers.add(
                new TeacherProfile(
                        1,
                        "Emily Martin",
                        "emily.martin@example.com",
                        "American",
                        "Ho Chi Minh City",
                        "5 years",
                        "TEFL, Bachelor's degree",
                        "READY",
                        "https://example.com/emily-demo",
                        "https://example.com/emily-cv"
                )
        );

        teachers.add(
                new TeacherProfile(
                        2,
                        "Daniel Walker",
                        "daniel.walker@example.com",
                        "British",
                        "Hanoi",
                        "3 years",
                        "CELTA",
                        "READY",
                        "https://example.com/daniel-demo",
                        "https://example.com/daniel-cv"
                )
        );

        teachers.add(
                new TeacherProfile(
                        3,
                        "Sarah Thompson",
                        "sarah.thompson@example.com",
                        "Australian",
                        "Da Nang",
                        "7 years",
                        "TESOL, Master's degree",
                        "PENDING",
                        "https://example.com/sarah-demo",
                        "https://example.com/sarah-cv"
                )
        );

        // Initially, the demo Partner can view teachers 1 and 2.
        granted.add("partner:1");
        granted.add("partner:2");

        // Demo interviews
        interviews.add(
                new Interview(
                        1,
                        1,
                        "Emily Martin",
                        "Bright English Center",
                        "2026-10-20T10:00",
                        "PENDING",
                        "Not reviewed",
                        "",
                        ""
                )
        );

        interviews.add(
                new Interview(
                        2,
                        2,
                        "Daniel Walker",
                        "Bright English Center",
                        "2026-10-21T14:30",
                        "CONFIRMED",
                        "Not reviewed",
                        "",
                        "https://meet.google.com/"
                )
        );

        log("SYSTEM", "Seed data loaded");
    }

    // =========================
    // USERS
    // =========================

    public Collection<AppUser> users() {
        return users.values();
    }

    public boolean userExists(String username) {
        return username != null && users.containsKey(username);
    }

    public String userRole(String username) {
        AppUser user = users.get(username);
        return user == null ? null : user.getRole();
    }

    public AppUser authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }

        AppUser user = users.get(username);

        if (user == null) {
            return null;
        }

        return user.getPassword().equals(password) ? user : null;
    }

    public void addUser(String username, String password, String role) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()
                || role == null || role.trim().isEmpty()) {
            return;
        }

        if (users.containsKey(username)) {
            return;
        }

        users.put(
                username,
                new AppUser(
                        username,
                        password,
                        role,
                        username
                )
        );

        log("ADMIN", "Created account " + username);
    }

    // =========================
    // TEACHER PROFILES
    // =========================

    public List<TeacherProfile> teachers() {
        return teachers;
    }

    public TeacherProfile teacher(long id) {
        return teachers.stream()
                .filter(teacher -> teacher.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public List<TeacherProfile> visibleTeachers(
            String username,
            String role) {

        if ("STAFF".equals(role) || "ADMIN".equals(role)) {
            return teachers;
        }

        if ("PARTNER".equals(role)) {
            return teachers.stream()
                    .filter(teacher ->
                            granted.contains(
                                    username + ":" + teacher.getId()
                            )
                    )
                    .toList();
        }

        // Other roles must not see Partner-shared profiles.
        return List.of();
    }

    public boolean canPartnerAccessTeacher(
            String username,
            long teacherId) {

        if (username == null || !userExists(username)) {
            return false;
        }

        if (!"PARTNER".equals(userRole(username))) {
            return false;
        }

        if (teacher(teacherId) == null) {
            return false;
        }

        return granted.contains(username + ":" + teacherId);
    }

    public void grant(String username, long teacherId) {
        if (username == null || !userExists(username)) {
            return;
        }

        if (!"PARTNER".equals(userRole(username))) {
            return;
        }

        if (teacher(teacherId) == null) {
            return;
        }

        granted.add(username + ":" + teacherId);

        log(
                "STAFF",
                "Granted teacher " + teacherId + " to " + username
        );
    }

    public void status(long id, String status) {
        TeacherProfile teacher = teacher(id);

        if (teacher == null || status == null || status.isBlank()) {
            return;
        }

        teacher.setStatus(status);

        log(
                "STAFF",
                "Updated " + teacher.getName()
                        + " status to " + status
        );
    }

    // =========================
    // INTERVIEWS
    // =========================

    public List<Interview> interviews() {
        return interviews;
    }

    public List<Interview> visibleInterviews(
            String role,
            String username) {

        if ("STAFF".equals(role) || "ADMIN".equals(role)) {
            return interviews;
        }

        if ("TEACHER".equals(role)) {
            // Retained from the original demo mapping.
            // Replace this when TeacherProfile is linked to AppUser.
            return interviews.stream()
                    .filter(interview -> interview.getTeacherId() == 1)
                    .toList();
        }

        if ("PARTNER".equals(role)) {
            AppUser user = users.get(username);

            if (user == null) {
                return List.of();
            }

            String partnerName = user.getFullName();

            if (partnerName == null || partnerName.isBlank()) {
                partnerName = user.getUsername();
            }

            final String currentPartner = partnerName;

            return interviews.stream()
                    .filter(interview ->
                            interview.getPartner() != null
                                    && interview.getPartner()
                                    .equalsIgnoreCase(currentPartner)
                    )
                    .toList();
        }

        return List.of();
    }

    public boolean requestInterview(
            long teacherId,
            String partnerName,
            String dateTime) {

        TeacherProfile teacher = teacher(teacherId);

        if (teacher == null
                || partnerName == null
                || partnerName.isBlank()
                || dateTime == null
                || dateTime.isBlank()) {
            return false;
        }

        long newId = interviewIds.incrementAndGet();

        Interview interview = new Interview(
                newId,
                teacherId,
                teacher.getName(),
                partnerName,
                dateTime,
                "PENDING",
                "Not reviewed",
                "",
                ""
        );

        interviews.add(interview);

        log(
                partnerName,
                "Interview requested for " + teacher.getName()
                        + " at " + dateTime
        );

        return true;
    }

    public void updateInterview(
            long id,
            String status,
            String result,
            String feedback,
            String meetUrl) {

        Interview interview = findInterview(id);
        if (meetUrl != null && !meetUrl.isBlank()) {
            interview.setMeetingUrl(meetUrl);
        }

        if (interview == null) {
            return;
        }

        if (status != null && !status.isBlank()) {
            interview.setStatus(status);
        }

        if (result != null && !result.isBlank()) {
            interview.setResult(result);
        }

        if (feedback != null) {
            interview.setFeedback(feedback);
        }

        // This is a demo placeholder, not a generated meeting.
        // A real meeting URL must be supplied by the scheduling workflow.
        log("INTERVIEW", "Updated interview #" + id);
    }

    public boolean updatePartnerFeedback(
            long interviewId,
            String username,
            String result,
            String feedback) {

        if (username == null || result == null || result.isBlank()
                || feedback == null || feedback.isBlank()) {
            return false;
        }

        AppUser user = users.get(username);

        if (user == null || !"PARTNER".equals(user.getRole())) {
            return false;
        }

        String partnerName = user.getFullName();

        if (partnerName == null || partnerName.isBlank()) {
            partnerName = user.getUsername();
        }

        Interview interview = findInterview(interviewId);

        if (interview == null || interview.getPartner() == null) {
            return false;
        }

        // A Partner can only update an interview assigned to that Partner.
        if (!interview.getPartner().equalsIgnoreCase(partnerName)) {
            return false;
        }

        interview.setResult(result);
        interview.setFeedback(feedback);

        log(
                username,
                "Submitted feedback for interview #" + interviewId
                        + ": " + result
        );

        return true;
    }

    private Interview findInterview(long id) {
        return interviews.stream()
                .filter(interview -> interview.getId() == id)
                .findFirst()
                .orElse(null);
    }

    // =========================
    // SYSTEM LOGS
    // =========================

    public List<String[]> logs() {
        return logs;
    }

    public void log(String actor, String action) {
        logs.add(
                0,
                new String[]{
                        LocalDateTime.now()
                                .toString()
                                .replace('T', ' '),
                        actor,
                        action
                }
        );
    }
}