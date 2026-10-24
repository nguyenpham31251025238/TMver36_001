package com.example.tmver36;

import com.example.tmver36.model.Interview;
import com.example.tmver36.service.PortalStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Controller for staff interview coordination and interview-result tracking (InterviewWorkspace.fxml). */
public class InterviewWorkspaceController {

    private final PortalStore store = LoginController.STORE;
    private final ObservableList<Interview> interviewItems = FXCollections.observableArrayList();

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;

    @FXML private ComboBox<String> interviewCombo;
    @FXML private ComboBox<String> stageCombo;
    @FXML private TextField meetUrl;
    @FXML private Button createMeetButton;
    @FXML private Button finalizeInterviewButton;
    @FXML private Button updateHistoryButton;
    @FXML private VBox coordinationTable;
    @FXML private VBox feedbackTable;

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
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> refreshInterviews());
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate(nav_Login, "Login.fxml"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void discoverUnidentifiedControls() {
        Parent root = FxNodes.rootOf(nav_InterviewWorkspace, nav_Login, finalizeInterviewButton, updateHistoryButton);
        if (root == null) return;
        List<ComboBox> combos = FxNodes.findAll(root, ComboBox.class);
        if (interviewCombo == null && !combos.isEmpty()) interviewCombo = combos.get(0);
        if (stageCombo == null && combos.size() > 1) stageCombo = combos.get(1);

        List<VBox> tables = FxNodes.findAllByStyleClass(root, "table", VBox.class);
        if (coordinationTable == null && !tables.isEmpty()) coordinationTable = tables.get(0);
        if (feedbackTable == null && tables.size() > 1) feedbackTable = tables.get(1);
    }

    private void refreshInterviews() {
        String username = activeUsername();
        interviewItems.setAll(store.visibleInterviews("STAFF", username));

        if (interviewCombo != null) {
            ObservableList<String> options = FXCollections.observableArrayList(
                    interviewItems.stream()
                            .sorted(Comparator.comparingLong(Interview::getId))
                            .map(this::formatInterviewChoice)
                            .collect(Collectors.toList()));
            interviewCombo.setItems(options);
            if (!options.isEmpty()) interviewCombo.getSelectionModel().selectFirst();
        }
        renderCoordinationTable();
        renderFeedbackTable();
        loadSelectedMeetingUrl();
    }

    private void renderCoordinationTable() {
        if (coordinationTable == null) return;
        removeExistingRows(coordinationTable);
        for (Interview interview : interviewItems) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(safe(interview.getTeacherName()), 400));
            row.getChildren().add(cell(safe(interview.getDateTime()), 320));
            row.getChildren().add(cell(safe(interview.getStatus()), 250));
            row.setOnMouseClicked(event -> selectInterview(interview));
            coordinationTable.getChildren().add(row);
        }
    }

    private void renderFeedbackTable() {
        if (feedbackTable == null) return;
        removeExistingRows(feedbackTable);
        for (Interview interview : interviewItems) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(safe(interview.getTeacherName()), 240));
            row.getChildren().add(cell(safe(interview.getPartner()), 230));
            row.getChildren().add(cell(safe(interview.getResult()), 180));
            row.getChildren().add(cell(safe(interview.getFeedback()), 320));
            row.setOnMouseClicked(event -> selectInterview(interview));
            feedbackTable.getChildren().add(row);
        }
    }

    private void removeExistingRows(VBox table) {
        if (table.getChildren().size() > 1) {
            table.getChildren().remove(1, table.getChildren().size());
        }
    }

    private VBox cell(String text, double width) {
        VBox cell = new VBox();
        cell.setAlignment(Pos.CENTER_LEFT);
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
        if (interviewCombo != null) {
            String option = formatInterviewChoice(interview);
            if (interviewCombo.getItems().contains(option)) {
                interviewCombo.getSelectionModel().select(option);
            }
        }
        if (meetUrl != null) {
            String url = safe(interview.getMeetingUrl());
            if (url.isBlank()) {
                url = PortalStore.generateMeetingLink(interview);
            }
            meetUrl.setText(url);
        }
    }

    private void loadSelectedMeetingUrl() {
        Interview selected = selectedInterview();
        if (selected != null && meetUrl != null) {
            String url = safe(selected.getMeetingUrl());
            if (url.isBlank()) {
                url = PortalStore.generateMeetingLink(selected);
            }
            meetUrl.setText(url);
        }
    }

    private void openMeetCreationPage() {
        Interview selected = selectedInterview();
        String url = meetUrl == null ? "" : safe(meetUrl.getText());
        if (url.isBlank() || url.startsWith("https://meet.google.com/tm-") || "https://meet.google.com/new".equalsIgnoreCase(url)) {
            url = PortalStore.generateMeetingLink(selected);
            if (meetUrl != null) {
                meetUrl.setText(url);
            }
        }
        if (selected != null) {
            store.updateInterview(activeUsername(), selected.getId(), selected.getStatus(),
                    selected.getResult(), selected.getFeedback(), url);
        }
        copyToClipboard(url);
        PortalStore.openInBrowser(url);
    }

    private void finalizeSelectedInterview() {
        Interview selected = selectedInterview();
        if (selected == null) {
            showError("No interview selected", "Select an interview before saving its meeting link.");
            return;
        }
        String url = meetUrl == null || meetUrl.getText() == null ? "" : meetUrl.getText().trim();
        if (url.isBlank()) {
            url = safe(selected.getMeetingUrl());
        }
        if (url.isBlank() || !(url.startsWith("https://") || url.startsWith("http://"))) {
            url = PortalStore.generateMeetingLink(selected);
            if (meetUrl != null) {
                meetUrl.setText(url);
            }
        }
        store.updateInterview(activeUsername(), selected.getId(), "CONFIRMED",
                selected.getResult(), selected.getFeedback(), url);
        store.log(activeUsername(), "Saved meeting link for interview #" + selected.getId() + ": " + url);
        copyToClipboard(url);
        refreshInterviews();
        showInfo("Interview Confirmed", "Confirmed interview #" + selected.getId() + " with live room link:\n" + url);
    }

    private void copyToClipboard(String text) {
        if (text == null || text.isBlank()) return;
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        } catch (Exception ignored) {
        }
    }

    private void updateSelectedInterviewHistory() {
        Interview selected = selectedInterview();
        if (selected == null) {
            showError("No interview selected", "Select an interview in the Interview Coordination section first.");
            return;
        }
        String stage = stageCombo == null || stageCombo.getValue() == null
                ? "Awaiting Results" : stageCombo.getValue();
        String result = switch (stage) {
            case "Accepted" -> "Proceed";
            case "Rejected" -> "Do not proceed";
            case "Interviewed" -> "Interviewed";
            default -> "Awaiting Results";
        };
        store.updateInterview(activeUsername(), selected.getId(), selected.getStatus(), result,
                selected.getFeedback(), selected.getMeetingUrl());
        store.log(activeUsername(), "Updated connection history for interview #" + selected.getId() + " to " + stage);
        refreshInterviews();
        showInfo("Stage Updated", "Updated interview #" + selected.getId() + " stage to " + stage + ".");
    }

    private Interview selectedInterview() {
        if (interviewCombo == null || interviewCombo.getValue() == null) {
            return interviewItems.isEmpty() ? null : interviewItems.get(0);
        }
        String choice = interviewCombo.getValue();
        for (Interview interview : interviewItems) {
            if (formatInterviewChoice(interview).equals(choice)) return interview;
        }
        return interviewItems.isEmpty() ? null : interviewItems.get(0);
    }

    private String formatInterviewChoice(Interview interview) {
        return "#" + interview.getId() + " · " + safe(interview.getTeacherName())
                + " · " + safe(interview.getPartner()) + " · " + safe(interview.getDateTime());
    }

    private String activeUsername() {
        String username = LoginController.getCurrentUsername();
        return username == null || username.isBlank() ? "staff" : username;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private void navigate(Node source, String fxml) {
        if (!LoginController.navigateTo(source, fxml)) {
            showError("Navigation failed", "Could not load " + fxml + ".");
        }
    }

    private void showError(String title, String message) {
        LoginController.showAlert(Alert.AlertType.ERROR, title, message);
    }

    private void showInfo(String title, String message) {
        LoginController.showAlert(Alert.AlertType.INFORMATION, title, message);
    }
}
