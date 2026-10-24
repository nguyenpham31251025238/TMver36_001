package com.example.tmver36.model;

public record TeacherDocument(
        long id,
        long teacherId,
        String fileName,
        String type,
        String updatedAt,
        String location
) {
}
