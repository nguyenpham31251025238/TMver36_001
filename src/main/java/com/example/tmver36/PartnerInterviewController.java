package com.example.tmver36;

import com.example.tmver36.model.AvailabilitySlot;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Partner interview requests and feedback backed by PortalStore (PartnerInterview.fxml). */
public class PartnerInterviewController {

    private static final PortalStore STORE = LoginController.STORE;
    private static long preselectedTeacherId = -1L;

    @FXML private Button nav_PartnerDashboard;
    @FXML private Button nav_PartnerProfile;
    @FXML private Button nav_PartnerInterview;
    @FXML private Button nav_Login;

    @FXML private ComboBox<String> requestedSlot;
    @FXML private ComboBox<TeacherProfile> teacherSelector;
    @FXML private TextField interviewerField;
    @FXML private Button requestInterviewButton;
    @FXML private Button openPartnerMeetButton;
    @FXML private VBox requestsTable;

    @FXML private ComboBox<String> interviewSelector;
    @FXML private ComboBox<String> interviewResult;
    @FXML private ComboBox<String> suitabilityCombo;
    @FXML private TextArea resultComments;
    @FXML private Button sendResultButton;

    private TextArea requestMessage;

    public static void setPreselectedTeacherId(long teacherId) {
        preselectedTeacherId = teacherId;
    }

    @FXML
    private void initialize() {
        Parent root = FxNodes.rootOf(nav_PartnerInterview, requestInterviewButton, sendResultButton, requestsTable);
        if (root != null) {
            for (TextArea area : FxNodes.findAll(root, TextArea.class)) {
                if (area != resultComments) {
                    requestMessage = area;
                    break;
                }
            }
        }

        List<TeacherProfile> sharedTeachers = STORE.visibleTeachers(currentPartner(), "PARTNER");
        if (teacherSelector != null) {
            teacherSelector.getItems().setAll(sharedTeachers);
            teacherSelector.setConverter(new StringConverter<>() {
                @Override
                public String toString(TeacherProfile teacher) {
                    return teacher == null ? "" : teacher.getName() + " (" + teacher.getNationality() + " · " + teacher.getExpectedSalary() + ")";
                }

                @Override
                public TeacherProfile fromString(String value) {
                    return null;
                }
            });
            TeacherProfile initial = sharedTeachers.stream()
                    .filter(t -> t.getId() == preselectedTeacherId)
                    .findFirst()
                    .orElse(sharedTeachers.isEmpty() ? null : sharedTeachers.get(0));
            if (initial != null) {
                teacherSelector.setValue(initial);
            }
            teacherSelector.valueProperty().addListener((obs, oldValue, newValue) -> refreshAvailableSlots());
        }
        refreshAvailableSlots();

        if (requestInterviewButton != null) requestInterviewButton.setOnAction(e -> handleRequestInterview());
        if (sendResultButton != null) sendResultButton.setOnAction(e -> handleSendResult());
        if (openPartnerMeetButton != null) openPartnerMeetButton.setOnAction(e -> openMeet());
        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> navigate("PartnerDashboard.fxml"));
        if (nav_PartnerProfile != null) nav_PartnerProfile.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerInterview != null) nav_PartnerInterview.setOnAction(e -> refreshView());
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml"));

        refreshView();
    }

    private String currentPartner() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "partner" : u;
    }

    private void handleRequestInterview() {
        String username = currentPartner();
        String slot = requestedSlot == null ? null : requestedSlot.getValue();
        if (slot == null || slot.isBlank()) {
            showError("Choose a time", "Select an available interview time slot.");
            return;
        }
        TeacherProfile selectedTeacher = teacherSelector == null ? null : teacherSelector.getValue();
        if (selectedTeacher == null) {
            showError("No shared profiles", "Matching has not granted access to a teacher profile.");
            return;
        }
        String dateTime = toStoreDateTime(slot);
        if (dateTime == null) {
            showError("Invalid time slot", "The selected slot could not be parsed.");
            return;
        }

        boolean created = STORE.requestInterview(selectedTeacher.getId(), username, dateTime);
        if (!created) {
            showError("Request failed", "The interview request could not be saved.");
            return;
        }
        if (requestMessage != null && requestMessage.getText() != null && !requestMessage.getText().isBlank()) {
            STORE.log(username, "Interview request note: " + requestMessage.getText().trim());
        }
        refreshAvailableSlots();
        refreshView();
        showInfo("Request sent", "Interview request saved for " + selectedTeacher.getName() + " at " + slot + ". Meeting link generated and ready!");
    }

    private void handleSendResult() {
        String username = currentPartner();
        String selected = interviewSelector == null ? null : interviewSelector.getValue();
        String result = interviewResult == null ? null : interviewResult.getValue();
        String comments = resultComments == null || resultComments.getText() == null ? "" : resultComments.getText().trim();
        if (selected == null || selected.isBlank()) {
            showError("Choose interview", "Select the interview to evaluate.");
            return;
        }
        if (result == null || result.isBlank() || comments.isBlank()) {
            showError("Incomplete result", "Choose a result and enter comments/reasons.");
            return;
        }

        Interview interview = findInterviewFromLabel(selected);
        if (interview == null) {
            showError("Interview not found", "The selected interview is no longer available in PortalStore.");
            return;
        }
        String mappedResult = switch (result) {
            case "Proceed with Candidate" -> "PROCEED";
            case "Not Proceeding" -> "NOT_PROCEEDING";
            case "Require Additional Interview/ Materials" -> "ADDITIONAL_INTERVIEW";
            default -> result;
        };
        String suitability = suitabilityCombo == null || suitabilityCombo.getValue() == null ? "" : suitabilityCombo.getValue();
        String feedback = suitability.isBlank() ? comments : "Suitability: " + suitability + " | " + comments;
        boolean saved = STORE.updatePartnerFeedback(interview.getId(), username, mappedResult, feedback);
        if (!saved) {
            showError("Could not save result", "PortalStore rejected this update.");
            return;
        }
        refreshView();
        showInfo("Result saved", "Your evaluation and comments have been saved to PortalStore.");
    }

    private void refreshView() {
        String username = currentPartner();
        List<Interview> interviews = STORE.visibleInterviews("PARTNER", username);
        if (interviewSelector != null) {
            String previous = interviewSelector.getValue();
            interviewSelector.getItems().setAll(interviews.stream().map(this::interviewLabel).toList());
            if (previous != null && interviewSelector.getItems().contains(previous)) {
                interviewSelector.setValue(previous);
            } else if (!interviewSelector.getItems().isEmpty()) {
                interviewSelector.getSelectionModel().selectFirst();
            }
        }
        renderRequests(interviews);
    }

    private void refreshAvailableSlots() {
        if (requestedSlot == null) return;
        requestedSlot.getItems().clear();
        TeacherProfile teacher = teacherSelector == null ? null : teacherSelector.getValue();
        if (teacher == null) return;
        STORE.availabilitySlots(teacher.getId()).stream()
                .map(AvailabilitySlot::label)
                .forEach(requestedSlot.getItems()::add);
        if (!requestedSlot.getItems().isEmpty()) {
            requestedSlot.getSelectionModel().selectFirst();
        }
    }

    private void renderRequests(List<Interview> interviews) {
        if (requestsTable == null || requestsTable.getChildren().isEmpty()) return;
        while (requestsTable.getChildren().size() > 1) {
            requestsTable.getChildren().remove(1);
        }
        boolean firstRow = true;
        for (Interview interview : interviews) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(safe(interview.getTeacherName()), 200));
            row.getChildren().add(cell(formatDateTime(safe(interview.getDateTime())), 220));

            Label statusBadge = new Label(safe(interview.getStatus()));
            String badgeClass = "CONFIRMED".equalsIgnoreCase(interview.getStatus()) ? "badge-green" : "badge-amber";
            statusBadge.getStyleClass().addAll("badge", badgeClass);
            VBox statusCol = new VBox(statusBadge);
            statusCol.setAlignment(Pos.CENTER_LEFT);
            statusCol.setMinWidth(170);
            statusCol.setPrefWidth(190);
            row.getChildren().add(statusCol);

            String meetUrl = interview.getMeetingUrl() != null && !interview.getMeetingUrl().isBlank()
                    ? interview.getMeetingUrl()
                    : PortalStore.generateMeetingLink(interview);
            final String resolvedMeetUrl = meetUrl;

            Hyperlink meetLink = new Hyperlink(resolvedMeetUrl);
            meetLink.setOnAction(e -> joinAndCopyMeetUrl(resolvedMeetUrl));

            Button joinBtn = (firstRow && openPartnerMeetButton != null)
                    ? openPartnerMeetButton
                    : new Button("Join Meet");
            firstRow = false;
            joinBtn.setText("Join Meet");
            joinBtn.getStyleClass().setAll("button-sm", "primary");
            joinBtn.setOnAction(e -> joinAndCopyMeetUrl(resolvedMeetUrl));

            Button copyBtn = new Button("Copy Link");
            copyBtn.getStyleClass().setAll("button-sm", "secondary");
            copyBtn.setOnAction(e -> {
                copyToClipboard(resolvedMeetUrl);
                showInfo("Meeting Link Copied", "Copied shared meeting link to clipboard:\n" + resolvedMeetUrl);
            });

            HBox btnBar = new HBox(8, joinBtn, copyBtn);
            btnBar.setAlignment(Pos.CENTER_LEFT);

            VBox linkCol = new VBox(4, meetLink, btnBar);
            linkCol.setAlignment(Pos.CENTER_LEFT);
            linkCol.setMinWidth(280);
            linkCol.setPrefWidth(340);
            HBox.setHgrow(linkCol, Priority.ALWAYS);

            row.getChildren().add(linkCol);
            requestsTable.getChildren().add(row);
        }
    }

    private void joinAndCopyMeetUrl(String meetUrl) {
        copyToClipboard(meetUrl);
        STORE.log(currentPartner(), "Joined interview room " + meetUrl);
        if (!PortalStore.openInBrowser(meetUrl)) {
            showInfo("Meeting Room Link", "Meeting link copied to clipboard:\n" + meetUrl);
        }
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        } catch (Exception ignored) {
        }
    }

    private VBox cell(String text, double width) {
        VBox box = new VBox();
        box.setMinWidth(width - 20);
        box.setPrefWidth(width);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setStyle("-fx-padding: 12 8 12 8;");
        Label label = new Label(text == null || text.isBlank() ? "—" : text);
        label.setWrapText(true);
        label.getStyleClass().add("table-text");
        box.getChildren().add(label);
        return box;
    }

    private void openMeet() {
        try {
            String selected = interviewSelector == null ? null : interviewSelector.getValue();
            Interview interview = selected == null ? null : findInterviewFromLabel(selected);
            if (interview == null) {
                List<Interview> visible = STORE.visibleInterviews("PARTNER", currentPartner());
                if (!visible.isEmpty()) {
                    interview = visible.get(0);
                }
            }
            if (interview == null) {
                showError("No interview available", "Request or select an interview first.");
                return;
            }
            String meetUrl = safe(interview.getMeetingUrl());
            if (meetUrl.isBlank()) {
                meetUrl = PortalStore.generateMeetingLink(interview);
            }
            joinAndCopyMeetUrl(meetUrl);
        } catch (Exception ex) {
            showError("Could not open Meet", ex.getMessage());
        }
    }

    private Interview findInterviewFromLabel(String label) {
        return STORE.visibleInterviews("PARTNER", currentPartner()).stream()
                .filter(i -> interviewLabel(i).equals(label))
                .findFirst().orElse(null);
    }

    private String interviewLabel(Interview interview) {
        return "#" + interview.getId() + " · " + safe(interview.getTeacherName()) + " · " + formatDateTime(safe(interview.getDateTime()));
    }

    private String toStoreDateTime(String slot) {
        try {
            String left = slot.split("-")[0].trim().replace("·", " ").trim().replaceAll("\\s+", " ");
            return LocalDateTime.parse(left, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")).toString();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private String formatDateTime(String raw) {
        try {
            return LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm"));
        } catch (RuntimeException ex) {
            return raw == null || raw.isBlank() ? "—" : raw;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void navigate(String fxml) {
        if (nav_PartnerInterview != null && !LoginController.navigateTo(nav_PartnerInterview, fxml)) {
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
