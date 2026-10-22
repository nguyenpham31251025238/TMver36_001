package com.example.tmver36.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogEntry {

    private final LocalDateTime timestamp;
    private final String level;
    private final String user;
    private final String event;

    public LogEntry(
            LocalDateTime timestamp,
            String level,
            String user,
            String event
    ) {
        this.timestamp = timestamp;
        this.level = level;
        this.user = user;
        this.event = event;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getTime() {
        return timestamp.format(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
        );
    }

    public String getDate() {
        return timestamp.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        );
    }

    public String getLevel() {
        return level;
    }

    public String getUser() {
        return user;
    }

    public String getEvent() {
        return event;
    }
}