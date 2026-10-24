package com.example.tmver36.model;

public class TeacherProfile {
    private long id;
    private String name;
    private String fullName;
    private String email;
    private String nationality;
    private String location;
    private String experience;
    private String qualification;
    private String status;
    private String videoUrl;
    private String cvUrl;
    private String subject;
    private String bio;
    private String phone;
    private String targetAgeGroup;
    private String availableStartDate;
    private String timezone;

    public TeacherProfile(
            long id,
            String name,
            String email,
            String nationality,
            String location,
            String experience,
            String qualification,
            String status,
            String videoUrl,
            String cvUrl
    ) {
        this.id = id;
        this.name = name;
        this.fullName = name;
        this.email = email;
        this.nationality = nationality;
        this.location = location;
        this.experience = experience;
        this.qualification = qualification;
        this.status = status;
        this.videoUrl = videoUrl;
        this.cvUrl = cvUrl;
        this.subject = "English (ESL)";
        this.bio = "Experienced English teacher with international classroom experience.";
        this.phone = "+84 912 345 678";
        this.targetAgeGroup = "Primary (6-11)";
        this.availableStartDate = "01/12/2026";
        this.timezone = "UTC+7 (Vietnam)";
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.fullName = name;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.name = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getExperience() {
        return experience;
    }

    public void setExperience(String experience) {
        this.experience = experience;
    }

    public String getQualification() {
        return qualification;
    }

    public void setQualification(String qualification) {
        this.qualification = qualification;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(String videoUrl) {
        this.videoUrl = videoUrl;
    }

    public String getCvUrl() {
        return cvUrl;
    }

    public void setCvUrl(String cvUrl) {
        this.cvUrl = cvUrl;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getTargetAgeGroup() {
        return targetAgeGroup;
    }

    public void setTargetAgeGroup(String targetAgeGroup) {
        this.targetAgeGroup = targetAgeGroup;
    }

    public String getAvailableStartDate() {
        return availableStartDate;
    }

    public void setAvailableStartDate(String availableStartDate) {
        this.availableStartDate = availableStartDate;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getCertificates() { return qualification; }
    public String getCertificatesUrl() { return cvUrl; }
    public String getIntroVideoUrl() { return videoUrl; }
    public String getDemoUrl() { return videoUrl; }
}
