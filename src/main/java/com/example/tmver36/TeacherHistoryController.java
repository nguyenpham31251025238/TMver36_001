package com.example.tmver36;

import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Displays connection history from the live PortalStore data (TeacherHistory.fxml). */
public class TeacherHistoryController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_TeacherProfile;
    @FXML private Button nav_TeacherDocuments;
    @FXML private Button nav_TeacherSchedule;
    @FXML private Button nav_TeacherHistory;
    @FXML private Button nav_Login;
    @FXML private VBox historyTable;

    @FXML
    private void initialize() {
        wireNavigation();
        Parent root = FxNodes.rootOf(nav_TeacherHistory, nav_TeacherProfile, historyTable);
        ProfileCardBuilder.decorateScreen(root, STORE, this::refreshHistory);
        refreshHistory();
    }

    private void wireNavigation() {
        if (nav_TeacherProfile != null) nav_TeacherProfile.setOnAction(e -> navigate("TeacherProfile.fxml", nav_TeacherProfile));
        if (nav_TeacherDocuments != null) nav_TeacherDocuments.setOnAction(e -> navigate("TeacherDocuments.fxml", nav_TeacherDocuments));
        if (nav_TeacherSchedule != null) nav_TeacherSchedule.setOnAction(e -> navigate("TeacherSchedule.fxml", nav_TeacherSchedule));
        if (nav_TeacherHistory != null) nav_TeacherHistory.setOnAction(e -> refreshHistory());
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void refreshHistory() {
        Parent root = FxNodes.rootOf(nav_TeacherHistory, nav_TeacherProfile, historyTable);
        if (root == null) return;

        String username = LoginController.getCurrentUsername();
        if (username == null || username.isBlank()) username = "teacher";
        long teacherId = STORE.teacherIdForUser(username);
        if (teacherId < 0) teacherId = 1L;

        TeacherProfile profile = STORE.teacher(teacherId);
        if (profile != null) {
            for (Label label : FxNodes.findAllByStyleClass(root, "sidebar-name", Label.class)) {
                label.setText(profile.getName());
            }
            ProfileCardBuilder.updateAvatarSlot(root, profile, 50);
        }

        final long finalTeacherId = teacherId;
        List<Interview> interviews = new ArrayList<>(STORE.visibleInterviews("TEACHER", username));
        interviews.removeIf(i -> i == null || i.getTeacherId() != finalTeacherId);
        interviews.sort(Comparator.comparing(Interview::getDateTime, Comparator.nullsLast(String::compareTo)));

        long pending = interviews.stream().filter(i -> "PENDING".equalsIgnoreCase(safe(i.getStatus()))).count();
        long confirmed = interviews.stream().filter(i -> "CONFIRMED".equalsIgnoreCase(safe(i.getStatus()))).count();
        long partners = interviews.stream().map(Interview::getPartner).filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).map(s -> s.toLowerCase(Locale.ROOT)).distinct().count();

        List<Label> metrics = FxNodes.findAllByStyleClass(root, "metric", Label.class);
        if (metrics.size() >= 3) {
            metrics.get(0).setText(Long.toString(partners));
            metrics.get(1).setText(Long.toString(pending));
            metrics.get(2).setText(Long.toString(confirmed));
        }

        VBox table = historyTable != null ? historyTable : FxNodes.findFirstByStyleClass(root, "table", VBox.class);
        if (table == null) return;
        Node header = table.getChildren().stream()
                .filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header"))
                .findFirst().orElse(null);
        table.getChildren().clear();
        if (header != null) table.getChildren().add(header);

        List<HistoryRow> rows = new ArrayList<>();
        for (Interview interview : interviews) {
            String status = safe(interview.getStatus());
            String notes = safe(interview.getPartner()) + " · " + safe(interview.getTeacherName()) + " · " + safe(interview.getResult());
            if (!safe(interview.getFeedback()).isBlank() && !"Not reviewed".equalsIgnoreCase(interview.getFeedback())) {
                notes += " · " + interview.getFeedback().trim();
            }
            rows.add(new HistoryRow(formatDate(interview.getDateTime()), status, notes));
        }

        String teacherName = profile == null ? "" : safe(profile.getName());
        STORE.logs().stream()
                .filter(entry -> entry != null && entry.length >= 3)
                .filter(entry -> containsIgnoreCase(entry[2], teacherName) || containsIgnoreCase(entry[2], "teacher " + finalTeacherId))
                .limit(20)
                .forEach(entry -> rows.add(new HistoryRow(safe(entry[0]), "Activity", safe(entry[2]))));

        if (rows.isEmpty()) {
            table.getChildren().add(makeRow("—", "No history", "No interview or activity records are available yet."));
        } else {
            for (HistoryRow row : rows) {
                table.getChildren().add(makeRow(row.time(), row.stage(), row.notes()));
            }
        }
    }

    private HBox makeRow(String time, String stage, String notes) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("table-row");
        row.getChildren().add(cell(time, 200));
        VBox stageCell = new VBox();
        stageCell.setAlignment(Pos.CENTER_LEFT);
        stageCell.setMinWidth(280);
        stageCell.setPrefWidth(280);
        stageCell.setMaxWidth(280);
        Label stageLabel = new Label(stage);
        stageLabel.getStyleClass().add("badge");
        String normalized = stage.toUpperCase(Locale.ROOT);
        stageLabel.getStyleClass().add(normalized.contains("CONFIRMED") || normalized.contains("READY") ? "badge-green"
                : normalized.contains("PENDING") ? "badge-amber" : "badge-gray");
        stageCell.getChildren().add(stageLabel);
        row.getChildren().add(stageCell);
        row.getChildren().add(cell(notes, 500));
        return row;
    }

    private VBox cell(String text, double width) {
        VBox cell = new VBox();
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(width - 16);
        label.getStyleClass().add("table-text");
        cell.getChildren().add(label);
        return cell;
    }

    private String formatDate(String value) {
        if (value == null || value.isBlank()) return "—";
        try {
            LocalDateTime date = LocalDateTime.parse(value);
            return date.format(DateTimeFormatter.ofPattern("dd/MM · HH:mm"));
        } catch (RuntimeException ignored) {
            return value.replace('T', ' ');
        }
    }

    private void navigate(String fxml, Node source) {
        LoginController.navigateTo(source, fxml);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsIgnoreCase(String source, String needle) {
        return source != null && needle != null && !needle.isBlank()
                && source.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private record HistoryRow(String time, String stage, String notes) {}
}
