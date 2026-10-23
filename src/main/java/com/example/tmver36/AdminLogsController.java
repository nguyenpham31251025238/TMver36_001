package com.example.tmver36;

import com.example.tmver36.service.PortalStore;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Controller for AdminLogs.fxml. */
public class AdminLogsController {

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
        if (searchLogsButton != null) {
            searchLogsButton.setOnAction(event -> handleSearch());
        }
        if (nav_AdminAccounts != null) {
            nav_AdminAccounts.setOnAction(event -> navigateTo("AdminAccounts.fxml"));
        }
        if (nav_AdminLogs != null) {
            nav_AdminLogs.setOnAction(event -> handleSearch());
        }
        if (nav_Login != null) {
            nav_Login.setOnAction(event -> handleLogout());
        }
        if (logSearch != null) {
            logSearch.textProperty().addListener((observable, oldValue, newValue) -> handleSearch());
        }
        if (logDate != null) {
            logDate.valueProperty().addListener((observable, oldValue, newValue) -> handleSearch());
        }
        // AdminLogs.fxml does not assign fx:id to its level ComboBox or log-row VBox.
        // Find these existing nodes after the FXML root has been attached to a Scene.
        Platform.runLater(() -> {
            locateUnidentifiedControls();
            handleSearch();
        });
    }

    private void locateUnidentifiedControls() {
        if (nav_AdminLogs == null || nav_AdminLogs.getScene() == null) {
            return;
        }
        Parent root = nav_AdminLogs.getScene().getRoot();
        for (Node node : root.lookupAll("ComboBox")) {
            if (node instanceof ComboBox<?> candidate) {
                boolean hasAll = candidate.getItems().stream().anyMatch(item -> "ALL".equals(String.valueOf(item)));
                boolean hasInfo = candidate.getItems().stream().anyMatch(item -> "INFO".equals(String.valueOf(item)));
                if (hasAll && hasInfo) {
                    @SuppressWarnings("unchecked")
                    ComboBox<String> typedCombo = (ComboBox<String>) candidate;
                    logLevel = typedCombo;
                    if (logLevel.getValue() == null) {
                        logLevel.setValue("ALL");
                    }
                    logLevel.valueProperty().addListener((observable, oldValue, newValue) -> handleSearch());
                    break;
                }
            }
        }
        for (Node node : root.lookupAll(".table")) {
            if (node instanceof VBox candidate) {
                logRowsContainer = candidate;
                break;
            }
        }
    }

    @FXML
    private void handleSearch() {
        String query = logSearch == null || logSearch.getText() == null
                ? "" : logSearch.getText().trim().toLowerCase(Locale.ROOT);
        String level = logLevel == null || logLevel.getValue() == null
                ? "ALL" : logLevel.getValue();
        LocalDate selectedDate = logDate == null ? null : logDate.getValue();

        List<String[]> filteredLogs = new ArrayList<>();
        for (String[] entry : AdminAccountsController.STORE.logs()) {
            if (entry == null || entry.length < 3) {
                continue;
            }
            if (!matchesQuery(entry, query) || !matchesLevel(entry, level) || !matchesDate(entry, selectedDate)) {
                continue;
            }
            filteredLogs.add(entry);
        }
        renderLogs(filteredLogs);
    }

    private boolean matchesQuery(String[] entry, String query) {
        if (query.isBlank()) {
            return true;
        }
        return safe(entry[1]).toLowerCase(Locale.ROOT).contains(query)
                || safe(entry[2]).toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesLevel(String[] entry, String level) {
        if (level == null || "ALL".equalsIgnoreCase(level)) {
            return true;
        }
        String action = safe(entry[2]).toUpperCase(Locale.ROOT);
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "ERROR" -> action.contains("ERROR") || action.contains("FAILED") || action.contains("EXCEPTION");
            case "WARN" -> action.contains("WARN") || action.contains("PENDING") || action.contains("DENIED");
            case "INFO" -> !(action.contains("ERROR") || action.contains("FAILED")
                    || action.contains("EXCEPTION") || action.contains("WARN"));
            default -> true;
        };
    }

    private boolean matchesDate(String[] entry, LocalDate date) {
        if (date == null) {
            return true;
        }
        String timestamp = safe(entry[0]);
        try {
            LocalDateTime dateTime = LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return dateTime.toLocalDate().equals(date);
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(timestamp, SOURCE_FORMAT).toLocalDate().equals(date);
            } catch (DateTimeParseException ignored) {
                return timestamp.startsWith(date.toString());
            }
        }
    }

    private void renderLogs(List<String[]> entries) {
        if (logRowsContainer == null) {
            return;
        }
        // Preserve the first child as the table header; replace only the sample/data rows.
        if (logRowsContainer.getChildren().isEmpty()) {
            return;
        }
        Node header = logRowsContainer.getChildren().get(0);
        logRowsContainer.getChildren().clear();
        logRowsContainer.getChildren().add(header);

        for (String[] entry : entries) {
            HBox row = new HBox(0);
            row.getStyleClass().add("table-row");
            row.getChildren().add(createCell(formatTimestamp(entry[0]), 160, "table-text"));
            row.getChildren().add(createCell(detectLevel(entry[2]), 130, levelStyle(entry[2])));
            row.getChildren().add(createCell(safe(entry[1]), 200, "table-text"));
            Label event = createCell(safe(entry[2]), 490, "table-text");
            event.setWrapText(true);
            row.getChildren().add(event);
            logRowsContainer.getChildren().add(row);
        }
        if (entries.isEmpty()) {
            Label empty = new Label("No log entries match the selected filters.");
            empty.getStyleClass().add("note");
            logRowsContainer.getChildren().add(empty);
        }
    }

    private Label createCell(String text, double width, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        label.setMinWidth(width);
        label.setPrefWidth(width);
        label.setMaxWidth(width);
        label.setWrapText(true);
        return label;
    }

    private String detectLevel(String actionText) {
        String action = safe(actionText).toUpperCase(Locale.ROOT);
        if (action.contains("ERROR") || action.contains("FAILED") || action.contains("EXCEPTION")) return "ERROR";
        if (action.contains("WARN") || action.contains("PENDING") || action.contains("DENIED")) return "WARN";
        return "INFO";
    }

    private String levelStyle(String actionText) {
        return switch (detectLevel(actionText)) {
            case "ERROR" -> "badge,badge-red";
            case "WARN" -> "badge,badge-amber";
            default -> "badge,badge-blue";
        };
    }

    private String formatTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) return "";
        try {
            return LocalDateTime.parse(timestamp, DateTimeFormatter.ISO_LOCAL_DATE_TIME).format(DISPLAY_FORMAT);
        } catch (DateTimeParseException exception) {
            try {
                return LocalDateTime.parse(timestamp, SOURCE_FORMAT).format(DISPLAY_FORMAT);
            } catch (DateTimeParseException ignored) {
                return timestamp;
            }
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @FXML
    private void handleLogout() {
        navigateTo("Login.fxml");
    }

    private void navigateTo(String fxmlFile) {
        try {
            FXMLLoader loader = createLoader(fxmlFile);
            Parent root = loader.load();
            Node source = nav_Login != null ? nav_Login : nav_AdminLogs;
            if (source == null || source.getScene() == null) return;
            Scene scene = source.getScene();
            scene.setRoot(root);
            Stage stage = (Stage) scene.getWindow();
            stage.sizeToScene();
        } catch (IOException exception) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Navigation failed");
            alert.setHeaderText(null);
            alert.setContentText("Could not load " + fxmlFile + ": " + exception.getMessage());
            alert.showAndWait();
        }
    }

    private FXMLLoader createLoader(String fxmlFile) throws IOException {
        String[] resourcePaths = {"/com/example/tmver36/" + fxmlFile, "/" + fxmlFile};
        for (String resourcePath : resourcePaths) {
            java.net.URL resource = getClass().getResource(resourcePath);
            if (resource != null) return new FXMLLoader(resource);
        }
        throw new IOException("FXML resource not found in the classpath: " + fxmlFile);
    }
}
