package com.example.tmver36.model;

public class TeacherProfile {
    private long id;
    private String name;
    private String fullName;
    private String email;
    private String nationality;
    private String location;
    private String experience;
    private String certificates;
    private String subject;
    private String degree;
    private String major;
    private String expectedSalary;
    private String status;
    private String availability;
    private String phone;
    private String bio;
    private String targetAgeGroup;
    private String availableStartDate;
    private String timezone;
    private String avatarUrl;
    private String cvUrl;
    private String certificateUrl;
    private String introVideoUrl;
    private String demoVideoUrl;
    private String staffFeedback;
    private String reviewChecklist;

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
        this.name = nn(name);
        this.fullName = nn(name);
        this.email = nn(email);
        this.nationality = nn(nationality);
        this.location = nn(location);
        this.experience = nn(experience);
        this.certificates = nn(qualification);
        this.subject = "English Education";
        this.degree = "Bachelor's Degree";
        this.major = "English Education";
        this.expectedSalary = "$1,500 - $2,000";
        this.status = status == null || status.isBlank() ? "PENDING" : status.trim();
        this.availability = "Immediate";
        this.phone = "+84 912 345 678";
        this.bio = "Experienced English teacher with international classroom experience.";
        this.targetAgeGroup = "Primary (6-11)";
        this.availableStartDate = "01/12/2026";
        this.timezone = "UTC+7 (Vietnam)";
        this.avatarUrl = "";
        this.cvUrl = nn(cvUrl);
        this.certificateUrl = "";
        this.introVideoUrl = nn(videoUrl);
        this.demoVideoUrl = nn(videoUrl);
        this.staffFeedback = "";
        this.reviewChecklist = "1,1,1,0,0";
    }

    public TeacherProfile(
            long id,
            String fullName,
            String nationality,
            String location,
            String experience,
            String certificates,
            String subject,
            String degree,
            String major,
            String expectedSalary,
            String status,
            String availability,
            String email,
            String phone,
            String bio,
            String avatarUrl,
            String cvUrl,
            String certificateUrl,
            String introVideoUrl,
            String demoVideoUrl,
            String staffFeedback,
            String reviewChecklist
    ) {
        this.id = id;
        this.name = nn(fullName);
        this.fullName = nn(fullName);
        this.nationality = nn(nationality);
        this.location = nn(location);
        this.experience = nn(experience);
        this.certificates = nn(certificates);
        this.subject = nn(subject);
        this.degree = degree == null || degree.isBlank() ? "Bachelor's Degree" : degree.trim();
        this.major = major == null || major.isBlank() ? (subject == null || subject.isBlank() ? "English Education" : subject.trim()) : major.trim();
        this.expectedSalary = expectedSalary == null || expectedSalary.isBlank() ? "$1,500 - $2,000" : expectedSalary.trim();
        this.status = status == null || status.isBlank() ? "PENDING" : status.trim();
        this.availability = nn(availability);
        this.email = nn(email);
        this.phone = nn(phone);
        this.bio = nn(bio);
        this.targetAgeGroup = "Primary (6-11)";
        this.availableStartDate = nn(availability).isBlank() ? "01/12/2026" : nn(availability);
        this.timezone = "UTC+7 (Vietnam)";
        this.avatarUrl = nn(avatarUrl);
        this.cvUrl = nn(cvUrl);
        this.certificateUrl = nn(certificateUrl);
        this.introVideoUrl = nn(introVideoUrl);
        this.demoVideoUrl = nn(demoVideoUrl);
        this.staffFeedback = nn(staffFeedback);
        this.reviewChecklist = reviewChecklist == null || reviewChecklist.isBlank() ? "0,0,0,0,0" : reviewChecklist.trim();
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return fullName != null && !fullName.isBlank() ? fullName : nn(name);
    }

    public void setName(String name) {
        this.name = nn(name);
        this.fullName = nn(name);
    }

    public String getFullName() {
        return fullName != null && !fullName.isBlank() ? fullName : nn(name);
    }

    public void setFullName(String fullName) {
        this.fullName = nn(fullName);
        this.name = nn(fullName);
    }

    public String getEmail() {
        return nn(email);
    }

    public void setEmail(String email) {
        this.email = nn(email);
    }

    public String getNationality() {
        return nn(nationality);
    }

    public void setNationality(String nationality) {
        this.nationality = nn(nationality);
    }

    public String getLocation() {
        return nn(location);
    }

    public void setLocation(String location) {
        this.location = nn(location);
    }

    public String getExperience() {
        return nn(experience);
    }

    public void setExperience(String experience) {
        this.experience = nn(experience);
    }

    public String getQualification() {
        return nn(certificates);
    }

    public void setQualification(String qualification) {
        this.certificates = nn(qualification);
    }

    public String getCertificates() {
        return nn(certificates);
    }

    public void setCertificates(String certificates) {
        this.certificates = nn(certificates);
    }

    public String getSubject() {
        return subject != null && !subject.isBlank() ? subject : nn(major);
    }

    public void setSubject(String subject) {
        this.subject = nn(subject);
        if (this.major == null || this.major.isBlank()) {
            this.major = this.subject;
        }
    }

    public String getDegree() {
        return degree == null || degree.isBlank() ? "Bachelor's Degree" : degree;
    }

    public void setDegree(String degree) {
        this.degree = degree == null || degree.isBlank() ? "Bachelor's Degree" : degree.trim();
    }

    public String getMajor() {
        return major != null && !major.isBlank() ? major : (subject != null && !subject.isBlank() ? subject : "English Education");
    }

    public void setMajor(String major) {
        this.major = nn(major);
        if (this.subject == null || this.subject.isBlank()) {
            this.subject = this.major;
        }
    }

    public String getExpectedSalary() {
        return expectedSalary == null || expectedSalary.isBlank() ? "$1,500 - $2,000" : expectedSalary;
    }

    public void setExpectedSalary(String expectedSalary) {
        this.expectedSalary = expectedSalary == null || expectedSalary.isBlank() ? "$1,500 - $2,000" : expectedSalary.trim();
    }

    public String getStatus() {
        return status == null || status.isBlank() ? "PENDING" : status;
    }

    public void setStatus(String status) {
        this.status = status == null || status.isBlank() ? "PENDING" : status.trim();
    }

    public String getAvailability() {
        return availability != null && !availability.isBlank() ? availability : nn(availableStartDate);
    }

    public void setAvailability(String availability) {
        this.availability = nn(availability);
    }

    public String getPhone() {
        return nn(phone);
    }

    public void setPhone(String phone) {
        this.phone = nn(phone);
    }

    public String getBio() {
        return nn(bio);
    }

    public void setBio(String bio) {
        this.bio = nn(bio);
    }

    public String getTargetAgeGroup() {
        return nn(targetAgeGroup);
    }

    public void setTargetAgeGroup(String targetAgeGroup) {
        this.targetAgeGroup = nn(targetAgeGroup);
    }

    public String getAvailableFrom() {
        return availableStartDate != null && !availableStartDate.isBlank() ? availableStartDate : availability;
    }

    public String getAvailableStartDate() {
        return nn(availableStartDate);
    }

    public void setAvailableStartDate(String availableStartDate) {
        this.availableStartDate = nn(availableStartDate);
        this.availability = nn(availableStartDate);
    }

    public String getTimezone() {
        return timezone == null || timezone.isBlank() ? "UTC+7 (Vietnam)" : timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = nn(timezone);
    }

    public String getAvatarUrl() {
        return nn(avatarUrl);
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = nn(avatarUrl);
    }

    public String getAvatarPath() {
        return nn(avatarUrl);
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarUrl = nn(avatarPath);
    }

    public String getCvUrl() {
        return nn(cvUrl);
    }

    public void setCvUrl(String cvUrl) {
        this.cvUrl = nn(cvUrl);
    }

    public String getCertificateUrl() {
        return nn(certificateUrl);
    }

    public void setCertificateUrl(String certificateUrl) {
        this.certificateUrl = nn(certificateUrl);
    }

    public String getCertificatesUrl() {
        return certificateUrl != null && !certificateUrl.isBlank() ? certificateUrl : nn(cvUrl);
    }

    public String getIntroVideoUrl() {
        return nn(introVideoUrl);
    }

    public void setIntroVideoUrl(String introVideoUrl) {
        this.introVideoUrl = nn(introVideoUrl);
    }

    public String getDemoVideoUrl() {
        return nn(demoVideoUrl);
    }

    public void setDemoVideoUrl(String demoVideoUrl) {
        this.demoVideoUrl = nn(demoVideoUrl);
    }

    public String getDemoUrl() {
        return demoVideoUrl != null && !demoVideoUrl.isBlank() ? demoVideoUrl : nn(introVideoUrl);
    }

    public String getVideoUrl() {
        return introVideoUrl != null && !introVideoUrl.isBlank() ? introVideoUrl : nn(demoVideoUrl);
    }

    public void setVideoUrl(String videoUrl) {
        this.introVideoUrl = nn(videoUrl);
        if (this.demoVideoUrl == null || this.demoVideoUrl.isBlank()) {
            this.demoVideoUrl = this.introVideoUrl;
        }
    }

    public String getStaffFeedback() {
        return nn(staffFeedback);
    }

    public void setStaffFeedback(String staffFeedback) {
        this.staffFeedback = nn(staffFeedback);
    }

    public String getReviewChecklist() {
        return reviewChecklist == null || reviewChecklist.isBlank() ? "0,0,0,0,0" : reviewChecklist;
    }

    public void setReviewChecklist(String reviewChecklist) {
        this.reviewChecklist = reviewChecklist == null || reviewChecklist.isBlank() ? "0,0,0,0,0" : reviewChecklist.trim();
    }

    public boolean[] getChecklistFlags() {
        boolean[] flags = new boolean[5];
        String raw = getReviewChecklist();
        String[] parts = raw.split(",");
        for (int i = 0; i < flags.length && i < parts.length; i++) {
            String p = parts[i].trim();
            flags[i] = "1".equals(p) || "true".equalsIgnoreCase(p);
        }
        return flags;
    }

    public void setChecklistFlags(boolean... flags) {
        if (flags == null || flags.length == 0) {
            this.reviewChecklist = "0,0,0,0,0";
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                sb.append(',');
            }
            boolean val = i < flags.length && flags[i];
            sb.append(val ? '1' : '0');
        }
        this.reviewChecklist = sb.toString();
    }
}
