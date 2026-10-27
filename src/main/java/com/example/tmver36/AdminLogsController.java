package com.example.tmver36;

import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** System logs controller for AdminLogs.fxml. */
public class AdminLogsController {

    private static final PortalStore STORE = LoginController.STORE;
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("dd/MM · HH:mm");
    private static final DateTimeFormatter SOURCE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @FXML private Button nav_AdminAccounts;
    @FXML private Button nav_AdminLogs;
    @FXML private Button nav_Login;
    @FXML private TextField logSearch;
    @FXML private DatePicker logDate;
    @FXML private Button searchLogsButton;

    private ComboBox<String> logLevel;
    private VBox logRowsContainer;

    @FXML
    private void initialize() {
        resolveUnidentifiedNodes();
        Parent root = FxNodes.rootOf(nav_AdminLogs, searchLogsButton, logSearch, logDate);
        ProfileCardBuilder.decorateScreen(root, STORE, this::handleSearch);
        if (searchLogsButton != null) searchLogsButton.setOnAction(e -> handleSearch());
        if (logSearch != null) {
            logSearch.setOnAction(e -> handleSearch());
            logSearch.textProperty().addListener((obs, o, n) -> handleSearch());
        }
        if (logDate != null) {
            logDate.valueProperty().addListener((obs, o, n) -> handleSearch());
        }
        if (logLevel != null) {
            logLevel.setOnAction(e -> handleSearch());
        }
        if (nav_AdminAccounts != null) {
            nav_AdminAccounts.setOnAction(e -> LoginController.navigateTo(nav_AdminAccounts, "AdminAccounts.fxml"));
        }
        if (nav_AdminLogs != null) {
            nav_AdminLogs.setOnAction(e -> handleSearch());
        }
        if (nav_Login != null) {
            nav_Login.setOnAction(e -> LoginController.navigateTo(nav_Login, "Login.fxml"));
        }
        handleSearch();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void resolveUnidentifiedNodes() {
        Parent root = FxNodes.rootOf(nav_AdminLogs, searchLogsButton, logSearch, logDate);
        if (root == null) return;
        for (ComboBox combo : FxNodes.findAll(root, ComboBox.class)) {
            String joined = String.valueOf(combo.getItems());
            if (joined.contains("ALL") && joined.contains("INFO")) {
                logLevel = combo;
                if (logLevel.getValue() == null) {
                    logLevel.setValue("ALL");
                }
                break;
            }
        }
        logRowsContainer = FxNodes.findFirstByStyleClass(root, "table", VBox.class);
    }

    @FXML
    private void handleSearch() {
        if (logRowsContainer == null) {
            resolveUnidentifiedNodes();
        }
        String query = logSearch == null || logSearch.getText() == null
                ? ""
                : logSearch.getText().trim().toLowerCase(Locale.ROOT);
        String level = logLevel == null || logLevel.getValue() == null
                ? "ALL"
                : logLevel.getValue().trim().toUpperCase(Locale.ROOT);
        LocalDate date = logDate == null ? null : logDate.getValue();

        List<String[]> filtered = new ArrayList<>();
        for (String[] entry : STORE.logs()) {
            if (entry == null || entry.length < 3) continue;
            String timestamp = safe(entry[0]);
            String actor = safe(entry[1]);
            String action = safe(entry[2]);
            String entryLevel = inferLevel(action);

            boolean matchesQuery = query.isEmpty()
                    || timestamp.toLowerCase(Locale.ROOT).contains(query)
                    || actor.toLowerCase(Locale.ROOT).contains(query)
                    || action.toLowerCase(Locale.ROOT).contains(query);
            boolean matchesLevel = "ALL".equalsIgnoreCase(level) || entryLevel.equalsIgnoreCase(level);
            boolean matchesDate = matchesDate(timestamp, date);

            if (matchesQuery && matchesLevel && matchesDate) {
                filtered.add(entry);
            }
        }
        renderLogs(filtered);
    }

    private boolean matchesDate(String timestamp, LocalDate date) {
        if (date == null) return true;
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate().equals(date);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(timestamp, SOURCE_FORMAT).toLocalDate().equals(date);
            } catch (DateTimeParseException ignored) {
                return timestamp.startsWith(date.toString());
            }
        }
    }

    private void renderLogs(List<String[]> entries) {
        if (logRowsContainer == null) return;
        Node header = logRowsContainer.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("table-header"))
                .findFirst()
                .orElse(logRowsContainer.getChildren().isEmpty() ? null : logRowsContainer.getChildren().get(0));
        logRowsContainer.getChildren().clear();
        if (header != null) {
            logRowsContainer.getChildren().add(header);
        }
        if (entries.isEmpty()) {
            Label empty = new Label("No log entries match the selected filters.");
            empty.getStyleClass().add("note");
            logRowsContainer.getChildren().add(empty);
            return;
        }
        for (String[] entry : entries) {
            String timeText = formatTimestamp(safe(entry[0]));
            String actor = safe(entry[1]);
            String action = safe(entry[2]);
            String level = inferLevel(action);

            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");
            row.getChildren().addAll(
                    createTextCell(timeText, 160),
                    createBadgeCell(level, 130),
                    createTextCell(actor, 200),
                    createTextCell(action, 490)
            );
            logRowsContainer.getChildren().add(row);
        }
    }

    private String inferLevel(String action) {
        String lower = action.toLowerCase(Locale.ROOT);
        if (lower.contains("error") || lower.contains("fail") || lower.contains("denied")) {
            return "ERROR";
        }
        if (lower.contains("delete") || lower.contains("revoke") || lower.contains("disable") || lower.contains("decline") || lower.contains("warn")) {
            return "WARN";
        }
        return "INFO";
    }

    private VBox createTextCell(String text, double width) {
        VBox box = new VBox(0);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        Label label = new Label(text);
        label.setMaxWidth(width - 16);
        label.setWrapText(true);
        label.getStyleClass().add("table-text");
        box.getChildren().add(label);
        return box;
    }

    private VBox createBadgeCell(String level, double width) {
        VBox box = new VBox(0);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        Label badge = new Label(level);
        String badgeColor = switch (level) {
            case "ERROR" -> "badge-red";
            case "WARN" -> "badge-amber";
            default -> "badge-green";
        };
        badge.getStyleClass().addAll("badge", badgeColor);
        box.getChildren().add(badge);
        return box;
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp.isBlank()) return "";
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(DISPLAY_FORMAT);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(timestamp, SOURCE_FORMAT).format(DISPLAY_FORMAT);
            } catch (DateTimeParseException ignored) {
                return timestamp;
            }
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
