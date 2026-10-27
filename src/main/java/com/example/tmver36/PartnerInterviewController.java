package com.example.tmver36;

import com.example.tmver36.model.AvailabilitySlot;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.NotificationItem;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.scene.layout.Region;

/** Partner interview requests, schedule adjustment, notifications, and feedback backed by PortalStore (PartnerInterview.fxml). */
public class PartnerInterviewController {

    private static final PortalStore STORE = LoginController.STORE;
    private static long preselectedTeacherId = -1L;

    @FXML private Button nav_PartnerDashboard;
    @FXML private Button nav_PartnerProfile;
    @FXML private Button nav_PartnerInterview;
    @FXML private Button nav_Login;

    @FXML private ComboBox<Integer> leadDaysCombo;
    @FXML private VBox partnerNotificationsBox;

    @FXML private ComboBox<String> requestedSlot;
    @FXML private ComboBox<TeacherProfile> teacherSelector;
    @FXML private TextField interviewerField;
    @FXML private Button requestInterviewButton;
    @FXML private DatePicker rescheduleDatePicker;
    @FXML private ComboBox<String> rescheduleFromCombo;
    @FXML private ComboBox<String> rescheduleToCombo;
    @FXML private Button addDraftSlotButton;
    @FXML private Button saveMultiSlotsButton;
    @FXML private Button rescheduleInterviewButton;
    @FXML private VBox draftSlotsBox;
    private final List<PortalStore.TimeSlotSpec> draftSlots = new ArrayList<>();
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
        ProfileCardBuilder.decorateScreen(root, STORE, () -> {
            refreshTeachersList();
            refreshAvailableSlots();
            refreshView();
        });

        if (leadDaysCombo != null) {
            leadDaysCombo.getItems().setAll(1, 2, 3, 5, 7, 14);
            leadDaysCombo.setValue(STORE.getNotificationLeadDays());
            leadDaysCombo.valueProperty().addListener((obs, oldVal, val) -> {
                if (val != null) {
                    STORE.setNotificationLeadDays(currentPartner(), val);
                    renderNotifications();
                }
            });
        }
        if (rescheduleDatePicker != null && rescheduleDatePicker.getValue() == null) {
            rescheduleDatePicker.setValue(LocalDate.now().plusDays(1));
        }

        refreshTeachersList();
        refreshAvailableSlots();

        if (requestInterviewButton != null) requestInterviewButton.setOnAction(e -> handleRequestInterview());
        if (addDraftSlotButton != null) addDraftSlotButton.setOnAction(e -> handleAddDraftSlot());
        if (saveMultiSlotsButton != null) saveMultiSlotsButton.setOnAction(e -> handleSaveMultiSlots());
        if (rescheduleInterviewButton != null) rescheduleInterviewButton.setOnAction(e -> handleRescheduleInterview());
        refreshDraftSlotsBox();
        if (sendResultButton != null) sendResultButton.setOnAction(e -> handleSendResult());
        if (openPartnerMeetButton != null) openPartnerMeetButton.setOnAction(e -> openMeet());
        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> navigate("PartnerDashboard.fxml"));
        if (nav_PartnerProfile != null) nav_PartnerProfile.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerInterview != null) nav_PartnerInterview.setOnAction(e -> refreshView());
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml"));

        refreshView();
    }

    private void refreshTeachersList() {
        List<TeacherProfile> sharedTeachers = STORE.visibleTeachers(currentPartner(), "PARTNER");
        if (teacherSelector != null) {
            TeacherProfile prev = teacherSelector.getValue();
            teacherSelector.getItems().setAll(sharedTeachers);
            teacherSelector.setConverter(new StringConverter<>() {
                @Override
                public String toString(TeacherProfile teacher) {
                    return teacher == null ? "" : teacher.getName() + " ("
                            + CatalogData.formatNationality(teacher.getNationality()) + " · "
                            + CatalogData.formatSalary(teacher.getExpectedSalary()) + ")";
                }

                @Override
                public TeacherProfile fromString(String value) {
                    return null;
                }
            });
            TeacherProfile initial = sharedTeachers.stream()
                    .filter(t -> t.getId() == preselectedTeacherId || (prev != null && t.getId() == prev.getId()))
                    .findFirst()
                    .orElse(sharedTeachers.isEmpty() ? null : sharedTeachers.get(0));
            if (initial != null) {
                teacherSelector.setValue(initial);
            }
            teacherSelector.valueProperty().addListener((obs, oldValue, newValue) -> refreshAvailableSlots());
        }
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
            showError("Schedule Conflict / Request Rejected",
                    "Could not create the interview request (either access is not granted or there is an overlapping interview slot).");
            return;
        }
        if (requestMessage != null && requestMessage.getText() != null && !requestMessage.getText().isBlank()) {
            STORE.log(username, "Interview request note: " + requestMessage.getText().trim());
        }
        refreshAvailableSlots();
        refreshView();
        showInfo("Request sent", "Interview request saved for " + selectedTeacher.getName() + " at " + slot + ". Meeting link generated and ready!");
    }

    public List<PortalStore.TimeSlotSpec> getDraftSlots() {
        return Collections.unmodifiableList(draftSlots);
    }

    public boolean addDraftSlot(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null) {
            showError("Missing slot details", "Choose a date, start time, and end time (HH:mm).");
            return false;
        }
        if (!end.isAfter(start)) {
            showError("Invalid time range", "End time must be after start time.");
            return false;
        }
        PortalStore.TimeSlotSpec candidate = new PortalStore.TimeSlotSpec(date, start, end);
        for (PortalStore.TimeSlotSpec existing : draftSlots) {
            if (candidate.startAt().isBefore(existing.endAt()) && candidate.endAt().isAfter(existing.startAt())) {
                showError("Overlapping slot in setup",
                        "Slot overlaps with another slot in your setup list: " + existing.displayLabel());
                return false;
            }
        }
        draftSlots.add(candidate);
        refreshDraftSlotsBox();
        return true;
    }

    public boolean removeDraftSlot(int index) {
        if (index < 0 || index >= draftSlots.size()) return false;
        draftSlots.remove(index);
        refreshDraftSlotsBox();
        return true;
    }

    private void handleAddDraftSlot() {
        LocalDate date = rescheduleDatePicker == null ? null : rescheduleDatePicker.getValue();
        String fromText = comboText(rescheduleFromCombo);
        String toText = comboText(rescheduleToCombo);
        if (date == null || fromText.isBlank() || toText.isBlank()) {
            showError("Missing time slot", "Choose a date, start time, and end time (HH:mm).");
            return;
        }
        try {
            addDraftSlot(date, LocalTime.parse(fromText), LocalTime.parse(toText));
        } catch (Exception ex) {
            showError("Invalid time format", "Please enter times in HH:mm format.");
        }
    }

    public boolean handleSaveMultiSlots() {
        String selected = interviewSelector == null ? null : interviewSelector.getValue();
        Interview interview = selected == null ? null : findInterviewFromLabel(selected);
        if (interview == null) {
            List<Interview> visible = STORE.visibleInterviews("PARTNER", currentPartner());
            if (!visible.isEmpty()) interview = visible.get(0);
        }
        if (draftSlots.isEmpty()) {
            LocalDate date = rescheduleDatePicker == null ? null : rescheduleDatePicker.getValue();
            String fromText = comboText(rescheduleFromCombo);
            String toText = comboText(rescheduleToCombo);
            if (date != null && !fromText.isBlank() && !toText.isBlank()) {
                try {
                    if (!addDraftSlot(date, LocalTime.parse(fromText), LocalTime.parse(toText))) {
                        return false;
                    }
                } catch (Exception ex) {
                    showError("Invalid time format", "Please enter times in HH:mm format.");
                    return false;
                }
            }
        }
        if (draftSlots.isEmpty()) {
            showError("No slots added", "Please add at least one time slot before saving.");
            return false;
        }
        List<PortalStore.TimeSlotSpec> batch = new ArrayList<>(draftSlots);
        if (interview != null) {
            if (!STORE.rescheduleInterviewMultiSlots(currentPartner(), interview.getId(), batch)) {
                showError("Schedule Conflict", STORE.getLastValidationError());
                return false;
            }
        } else {
            TeacherProfile selectedTeacher = teacherSelector == null ? null : teacherSelector.getValue();
            if (selectedTeacher == null) {
                showError("Select teacher or interview", "Please select an interview or teacher first.");
                return false;
            }
            if (!STORE.requestInterviewsBatch(selectedTeacher.getId(), currentPartner(), batch)) {
                showError("Schedule Conflict", STORE.getLastValidationError());
                return false;
            }
        }
        int count = batch.size();
        draftSlots.clear();
        refreshDraftSlotsBox();
        refreshView();
        showInfo("Slots Saved", "Saved and synchronized " + count + " interview time slot(s).");
        return true;
    }

    private void refreshDraftSlotsBox() {
        if (draftSlotsBox == null) return;
        draftSlotsBox.getChildren().clear();
        for (int i = 0; i < draftSlots.size(); i++) {
            final int idx = i;
            PortalStore.TimeSlotSpec spec = draftSlots.get(i);
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            Label lbl = new Label("Slot #" + (i + 1) + ": " + spec.displayLabel());
            lbl.setStyle("-fx-font-weight: 600; -fx-text-fill: #1E40AF;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Button removeBtn = new Button("Remove");
            removeBtn.getStyleClass().add("danger");
            removeBtn.setOnAction(e -> removeDraftSlot(idx));
            row.getChildren().addAll(lbl, spacer, removeBtn);
            draftSlotsBox.getChildren().add(row);
        }
    }

    private void handleRescheduleInterview() {
        if (!draftSlots.isEmpty()) {
            handleSaveMultiSlots();
            return;
        }
        String selected = interviewSelector == null ? null : interviewSelector.getValue();
        Interview interview = selected == null ? null : findInterviewFromLabel(selected);
        if (interview == null) {
            List<Interview> visible = STORE.visibleInterviews("PARTNER", currentPartner());
            if (!visible.isEmpty()) interview = visible.get(0);
        }
        if (interview == null) {
            showError("No interview selected", "Select an interview to reschedule.");
            return;
        }
        LocalDate date = rescheduleDatePicker == null ? null : rescheduleDatePicker.getValue();
        String fromText = comboText(rescheduleFromCombo);
        String toText = comboText(rescheduleToCombo);
        if (date == null || fromText.isBlank() || toText.isBlank()) {
            showError("Missing time slot", "Choose a date, start time, and end time (HH:mm).");
            return;
        }
        try {
            LocalDateTime startAt = LocalDateTime.of(date, LocalTime.parse(fromText));
            LocalDateTime endAt = LocalDateTime.of(date, LocalTime.parse(toText));
            if (!STORE.rescheduleInterview(currentPartner(), interview.getId(), startAt, endAt)) {
                showError("Schedule Conflict", STORE.getLastValidationError());
                return;
            }
            refreshView();
            showInfo("Interview Rescheduled", "Updated interview schedule to " + formatDateTime(startAt.toString())
                    + " - " + endAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) + " (UTC+7).");
        } catch (Exception ex) {
            showError("Invalid time format", "Please enter times in HH:mm format.");
        }
    }

    private String comboText(ComboBox<String> combo) {
        if (combo == null) return "";
        if (combo.isEditable() && combo.getEditor() != null && combo.getEditor().getText() != null && !combo.getEditor().getText().isBlank()) {
            return combo.getEditor().getText().trim();
        }
        return combo.getValue() == null ? "" : combo.getValue().trim();
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
        if (leadDaysCombo != null) {
            leadDaysCombo.setValue(STORE.getNotificationLeadDays());
        }
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
        renderNotifications();
    }

    private void renderNotifications() {
        if (partnerNotificationsBox == null) return;
        partnerNotificationsBox.getChildren().clear();
        List<NotificationItem> notes = STORE.notificationsForUser(currentPartner());
        if (notes.isEmpty()) {
            Label none = new Label("No upcoming interview reminders within " + STORE.getNotificationLeadDays() + " day(s) or new result updates.");
            none.getStyleClass().add("muted");
            partnerNotificationsBox.getChildren().add(none);
            return;
        }
        for (NotificationItem item : notes) {
            Label line = new Label("• [" + item.getCreatedAt() + "] " + item.getMessage());
            line.setWrapText(true);
            line.getStyleClass().add("table-text");
            partnerNotificationsBox.getChildren().add(line);
        }
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
            row.getChildren().add(cell(formatTimeRange(interview), 220));

            String statusText = safe(interview.getStatus());
            if (!safe(interview.getResult()).isBlank() && !"Pending".equalsIgnoreCase(safe(interview.getResult()))) {
                statusText += " · " + safe(interview.getResult());
            }
            Label statusBadge = new Label(statusText);
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
            row.setOnMouseClicked(e -> {
                if (interviewSelector != null) {
                    interviewSelector.setValue(interviewLabel(interview));
                }
            });
            requestsTable.getChildren().add(row);
        }
    }

    private String formatTimeRange(Interview interview) {
        LocalDateTime start = PortalStore.parseInterviewStart(interview.getDateTime());
        LocalDateTime end = PortalStore.parseInterviewEnd(interview);
        if (start != null && end != null) {
            return start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm"))
                    + " - " + end.format(DateTimeFormatter.ofPattern("HH:mm")) + " (UTC+7)";
        }
        return formatDateTime(safe(interview.getDateTime()));
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
