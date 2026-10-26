package com.example.tmver36.model;

public record TeacherDocument(
        long id,
        long teacherId,
        String fileName,
        String type,
        String updatedAt,
        String location
) {
    public String getCategory() {
        return type == null ? "" : type;
    }

    public String getFileName() {
        return fileName == null ? "" : fileName;
    }

    public String getUpdatedAt() {
        return updatedAt == null ? "" : updatedAt;
    }

    public String getLocation() {
        return location == null ? "" : location;
    }
}
