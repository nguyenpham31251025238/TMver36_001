package com.example.tmver36.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record AvailabilitySlot(long id, long teacherId, LocalDateTime startAt, LocalDateTime endAt) {
    private static final DateTimeFormatter LABEL_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public String label() {
        return startAt.format(LABEL_FORMAT) + " - " + endAt.format(TIME_FORMAT);
    }

    public String dateText() {
        return startAt.format(DATE_FORMAT);
    }

    public String timeRangeText() {
        return startAt.format(TIME_FORMAT) + " - " + endAt.format(TIME_FORMAT);
    }
}
