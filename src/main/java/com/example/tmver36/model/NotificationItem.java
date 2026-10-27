package com.example.tmver36.model;

public record NotificationItem(
        long id,
        String targetRole,
        String targetUser,
        long teacherId,
        String partner,
        String type,
        String title,
        String message,
        String createdAt,
        boolean read
) {
    public String getCreatedAt() {
        return createdAt;
    }

    public String getMessage() {
        return message;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return type;
    }
}
