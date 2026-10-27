package com.example.tmver36.model;

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
        this.teacherName = teacherName;
        this.partner = partner;
        this.dateTime = dateTime;
        this.endDateTime = endDateTime == null ? "" : endDateTime;
        this.status = status;
        this.result = result;
        this.feedback = feedback;
        this.meetingUrl = meetingUrl;
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
    }

    public String getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(String endDateTime) {
        this.endDateTime = endDateTime == null ? "" : endDateTime;
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
}
