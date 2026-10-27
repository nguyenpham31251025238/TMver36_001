package com.example.tmver36.model;

import java.util.Locale;

public record TeacherDocument(
        long id,
        long teacherId,
        String fileName,
        String type,
        String updatedAt,
        String location
) {
    public TeacherDocument {
        if (type != null) {
            String trimmed = type.trim();
            if (trimmed.equalsIgnoreCase("Demo video") || trimmed.equalsIgnoreCase("Demo Video")) {
                type = "Teaching video";
            } else if (trimmed.toLowerCase(Locale.ROOT).startsWith("demo video")) {
                type = "Teaching video" + trimmed.substring("demo video".length());
            }
        }
    }

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
