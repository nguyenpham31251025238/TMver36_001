package com.example.tmver36.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Interview {
    private final long id;
    private final long teacherId;
    private String teacherName;
    private String partner;
    private String dateTime;
    private String endDateTime;
    private String status;
    private String result;
    private String feedback;
    private String meetingUrl;
    private final List<String> proposedSlots = new ArrayList<>();

    public Interview(
            long id,
            long teacherId,
            String teacherName,
            String partner,
            String dateTime,
            String status,
            String result,
            String feedback,
            String meetingUrl
    ) {
        this(id, teacherId, teacherName, partner, dateTime, "", status, result, feedback, meetingUrl);
    }

    public Interview(
            long id,
            long teacherId,
            String teacherName,
            String partner,
            String dateTime,
            String endDateTime,
            String status,
            String result,
            String feedback,
            String meetingUrl
    ) {
        this.id = id;
        this.teacherId = teacherId;
        this.teacherName = teacherName == null ? "" : teacherName;
        this.partner = partner == null ? "" : partner;
        this.dateTime = dateTime == null ? "" : dateTime;
        this.endDateTime = endDateTime == null ? "" : endDateTime;
        this.status = status;
        this.result = result;
        this.feedback = feedback;
        this.meetingUrl = meetingUrl;
        syncPrimaryToProposedSlots();
    }

    private void syncPrimaryToProposedSlots() {
        if (!this.dateTime.isBlank()) {
            String entry = this.endDateTime.isBlank() ? this.dateTime : (this.dateTime + "|" + this.endDateTime);
            if (proposedSlots.isEmpty()) {
                proposedSlots.add(entry);
            } else {
                proposedSlots.set(0, entry);
            }
        }
    }

    public long getId() {
        return id;
    }

    public long getTeacherId() {
        return teacherId;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName == null ? "" : teacherName;
    }

    public String getPartner() {
        return partner;
    }

    public void setPartner(String partner) {
        this.partner = partner == null ? "" : partner;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime == null ? "" : dateTime;
        syncPrimaryToProposedSlots();
    }

    public String getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(String endDateTime) {
        this.endDateTime = endDateTime == null ? "" : endDateTime;
        syncPrimaryToProposedSlots();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getFeedback() {
        return feedback;
    }

    public void setFeedback(String feedback) {
        this.feedback = feedback;
    }

    public String getMeetingUrl() {
        return meetingUrl;
    }

    public void setMeetingUrl(String meetingUrl) {
        this.meetingUrl = meetingUrl;
    }

    public List<String> getProposedSlots() {
        if (proposedSlots.isEmpty() && !dateTime.isBlank()) {
            syncPrimaryToProposedSlots();
        }
        return Collections.unmodifiableList(proposedSlots);
    }

    public void setProposedSlots(List<String> slots) {
        proposedSlots.clear();
        if (slots != null) {
            for (String s : slots) {
                if (s != null && !s.isBlank() && !proposedSlots.contains(s.trim())) {
                    proposedSlots.add(s.trim());
                }
            }
        }
        if (proposedSlots.isEmpty() && !dateTime.isBlank()) {
            syncPrimaryToProposedSlots();
        }
    }

    public String getProposedSlotsSerialized() {
        return String.join(";", getProposedSlots());
    }

    public void setProposedSlotsSerialized(String raw) {
        proposedSlots.clear();
        if (raw != null && !raw.isBlank()) {
            for (String token : raw.split(";")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty() && !proposedSlots.contains(trimmed)) {
                    proposedSlots.add(trimmed);
                }
            }
        }
        if (proposedSlots.isEmpty() && !dateTime.isBlank()) {
            syncPrimaryToProposedSlots();
        }
    }
}
