package com.example.tmver36;

import com.example.tmver36.model.Interview;
import com.example.tmver36.service.PortalStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Controller for staff interview coordination and interview-result tracking. */
public class InterviewWorkspaceController {
    private final PortalStore store = LoginController.STORE;
    private final ObservableList<Interview> interviewItems = FXCollections.observableArrayList();

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;
    @FXML private TextField meetUrl;
    @FXML private Button createMeetButton;
    @FXML private Button finalizeInterviewButton;
    @FXML private Button updateHistoryButton;

    private ComboBox<?> interviewCombo;
    private ComboBox<?> stageCombo;
    private VBox coordinationTable;
    private VBox feedbackTable;

    @FXML
    private void initialize() {
        wireNavigation();
        discoverUnidentifiedControls();
        refreshInterviews();
        if (createMeetButton != null) createMeetButton.setOnAction(event -> openMeetCreationPage());
        if (finalizeInterviewButton != null) finalizeInterviewButton.setOnAction(event -> finalizeSelectedInterview());
        if (updateHistoryButton != null) updateHistoryButton.setOnAction(event -> updateSelectedInterviewHistory());
        if (interviewCombo != null) interviewCombo.setOnAction(event -> loadSelectedMeetingUrl());
    }

    private void wireNavigation() {
        if (nav_StaffDashboard != null) nav_StaffDashboard.setOnAction(e -> navigate(nav_StaffDashboard, "StaffDashboard.fxml"));
        if (nav_StaffReview != null) nav_StaffReview.setOnAction(e -> navigate(nav_StaffReview, "StaffReview.fxml"));
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> navigate(nav_InterviewWorkspace, "InterviewWorkspace.fxml"));
        if (nav_Login != null) nav_Login.setOnAction(e -> {
            store.log(activeUsername(), "Logged out");
            navigate(nav_Login, "Login.fxml");
        });
    }

    private void discoverUnidentifiedControls() {
        List<ComboBox> combos = new ArrayList<>();
        Node root = rootNode();
        if (root == null) return;
        for (Node node : root.lookupAll("ComboBox")) {
            if (node instanceof ComboBox combo) combos.add(combo);
        }
        // In this FXML the first ComboBox is the interview selector and the second is the next-stage selector.
        if (!combos.isEmpty()) interviewCombo = combos.get(0);
        if (combos.size() > 1) stageCombo = combos.get(1);

        List<VBox> tables = new ArrayList<>();
        for (Node node : root.lookupAll(".table")) {
            if (node instanceof VBox box) tables.add(box);
        }
        if (!tables.isEmpty()) coordinationTable = tables.get(0);
        if (tables.size() > 1) feedbackTable = tables.get(1);
    }

    private Node rootNode() {
        // initialize() runs before the Scene is attached, so walk up the FXML parent tree.
        Node node = nav_InterviewWorkspace != null ? nav_InterviewWorkspace : nav_Login;
        if (node == null) return null;
        while (node.getParent() != null) node = node.getParent();
        return node;
    }

    private void refreshInterviews() {
        String role = LoginController.getCurrentRole();
        String username = LoginController.getCurrentUsername();
        if (role == null) role = "STAFF";
        if (username == null) username = "staff";
        interviewItems.setAll(store.visibleInterviews(role, username));

        if (interviewCombo != null) {
            ObservableList<String> options = FXCollections.observableArrayList(
                    interviewItems.stream()
                            .sorted(Comparator.comparingLong(this::interviewId))
                            .map(this::formatInterviewChoice)
                            .collect(Collectors.toList()));
            ((ComboBox<String>) interviewCombo).setItems(options);
            if (!options.isEmpty()) ((ComboBox<String>) interviewCombo).getSelectionModel().selectFirst();
        }
        renderCoordinationTable();
        renderFeedbackTable();
        loadSelectedMeetingUrl();
    }

    private void renderCoordinationTable() {
        if (coordinationTable == null) return;
        removeExistingRows(coordinationTable);
        for (Interview interview : interviewItems) {
            HBox row = new HBox();
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(read(interview, "teacherName", "name"), 400));
            row.getChildren().add(cell(read(interview, "dateTime", "scheduledAt", "time"), 320));
            row.getChildren().add(cell(read(interview, "status"), 250));
            row.setOnMouseClicked(event -> selectInterview(interview));
            coordinationTable.getChildren().add(row);
        }
    }

    private void renderFeedbackTable() {
        if (feedbackTable == null) return;
        removeExistingRows(feedbackTable);
        for (Interview interview : interviewItems) {
            HBox row = new HBox();
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(read(interview, "teacherName", "name"), 240));
            row.getChildren().add(cell(read(interview, "partner"), 230));
            row.getChildren().add(cell(read(interview, "result"), 180));
            row.getChildren().add(cell(read(interview, "feedback"), 320));
            row.setOnMouseClicked(event -> selectInterview(interview));
            feedbackTable.getChildren().add(row);
        }
    }

    private void removeExistingRows(VBox table) {
        // Keep the table header and remove only the static sample rows supplied in the FXML.
        if (table.getChildren().size() > 1) {
            table.getChildren().remove(1, table.getChildren().size());
        }
    }

    private VBox cell(String text, double width) {
        VBox cell = new VBox();
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.getStyleClass().add("table-text");
        label.setWrapText(true);
        label.setMaxWidth(width - 16);
        cell.getChildren().add(label);
        return cell;
    }

    private void selectInterview(Interview interview) {
        if (interviewCombo instanceof ComboBox<?> raw) {
            @SuppressWarnings("unchecked") ComboBox<String> combo = (ComboBox<String>) raw;
            String option = formatInterviewChoice(interview);
            if (combo.getItems().contains(option)) combo.getSelectionModel().select(option);
        }
        if (meetUrl != null) meetUrl.setText(read(interview, "meetUrl", "meetingUrl", "url"));
    }

    private void loadSelectedMeetingUrl() {
        Interview selected = selectedInterview();
        if (selected != null && meetUrl != null) {
            meetUrl.setText(read(selected, "meetUrl", "meetingUrl", "url"));
        }
    }

    private void openMeetCreationPage() {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                showError("Browser unavailable", "Open https://meet.google.com/new in your browser, create a meeting, then paste its link here.");
                return;
            }
            Desktop.getDesktop().browse(URI.create("https://meet.google.com/new"));
        } catch (Exception ex) {
            showError("Could not open Google Meet", ex.getMessage() == null ? "Open https://meet.google.com/new manually." : ex.getMessage());
        }
    }

    private void finalizeSelectedInterview() {
        Interview selected = selectedInterview();
        String url = meetUrl == null ? "" : meetUrl.getText().trim();
        if (selected == null) {
            showError("No interview selected", "Select an interview before saving its meeting link.");
            return;
        }
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            showError("Invalid meeting link", "Paste a valid http:// or https:// meeting URL.");
            return;
        }

        // Persist the URL on the model when its API exposes a meeting-link setter.
        boolean savedOnModel = write(selected, url, "setMeetUrl", "setMeetingUrl", "setUrl");
        String status = "CONFIRMED";
        store.updateInterview(interviewId(selected), status,
                (String) read(selected, "result"), (String) read(selected, "feedback"), url);
        store.log(activeUsername(), "Saved meeting link for interview #" + interviewId(selected)
                + (savedOnModel ? "" : " (model has no meeting-link setter)"));
        refreshInterviews();
    }

    private void updateSelectedInterviewHistory() {
        Interview selected = selectedInterview();
        if (selected == null) {
            showError("No interview selected", "Select an interview in the Interview Coordination section first.");
            return;
        }
        String stage = stageCombo == null || stageCombo.getValue() == null
                ? "Awaiting Results" : stageCombo.getValue().toString();
        String result = switch (stage) {
            case "Accepted" -> "Proceed";
            case "Rejected" -> "Do not proceed";
            case "Interviewed" -> "Interviewed";
            default -> "Awaiting Results";
        };
        store.updateInterview(interviewId(selected), read(selected, "status"), result,
                read(selected, "feedback"), "");
        store.log(activeUsername(), "Updated connection history for interview #" + interviewId(selected) + " to " + stage);
        refreshInterviews();
    }

    private Interview selectedInterview() {
        if (interviewCombo == null || interviewCombo.getValue() == null) return null;
        String choice = interviewCombo.getValue().toString();
        for (Interview interview : interviewItems) {
            if (formatInterviewChoice(interview).equals(choice)) return interview;
        }
        return null;
    }

    private long interviewId(Interview interview) {
        try { return ((Number) interview.getClass().getMethod("getId").invoke(interview)).longValue(); }
        catch (Exception ex) { return -1L; }
    }

    private String formatInterviewChoice(Interview interview) {
        return "#" + interviewId(interview) + " · " + read(interview, "teacherName", "name")
                + " · " + read(interview, "partner") + " · " + read(interview, "dateTime", "scheduledAt", "time");
    }

    /** Reads a model property without assuming optional getter names that PortalStore doesn't define. */
    private String read(Object target, String... propertyNames) {
        if (target == null) return "";
        for (String property : propertyNames) {
            String getter = "get" + Character.toUpperCase(property.charAt(0)) + property.substring(1);
            try {
                Method method = target.getClass().getMethod(getter);
                Object value = method.invoke(target);
                return value == null ? "" : value.toString();
            } catch (ReflectiveOperationException ignored) { }
        }
        return "";
    }

    private boolean write(Object target, String value, String... setterNames) {
        if (target == null) return false;
        for (String setterName : setterNames) {
            try {
                Method method = target.getClass().getMethod(setterName, String.class);
                method.invoke(target, value);
                return true;
            } catch (ReflectiveOperationException ignored) { }
        }
        return false;
    }

    private String activeUsername() {
        String username = LoginController.getCurrentUsername();
        return username == null || username.isBlank() ? "staff" : username;
    }

    private void navigate(Node source, String fxml) {
        if (!LoginController.navigateTo(source, fxml)) {
            showError("Navigation failed", "Could not load " + fxml + ". Check the FXML resource path.");
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
