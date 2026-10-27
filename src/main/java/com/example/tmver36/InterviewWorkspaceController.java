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
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import java.util.stream.Collectors;

/** Controller for staff interview coordination, schedule adjustment, and result tracking (InterviewWorkspace.fxml). */
public class InterviewWorkspaceController {

    private final PortalStore store = LoginController.STORE;
    private final ObservableList<Interview> interviewItems = FXCollections.observableArrayList();
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm");

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;

    @FXML private ComboBox<String> interviewCombo;
    @FXML private ComboBox<String> stageCombo;
    @FXML private TextField meetUrl;
    @FXML private DatePicker rescheduleDatePicker;
    @FXML private ComboBox<String> rescheduleFromCombo;
    @FXML private ComboBox<String> rescheduleToCombo;
    @FXML private Button addDraftSlotButton;
    @FXML private Button saveMultiSlotsButton;
    @FXML private Button rescheduleInterviewButton;
    @FXML private VBox draftSlotsBox;
    private final List<PortalStore.TimeSlotSpec> draftSlots = new ArrayList<>();
    @FXML private TextField staffResultNotesField;
    @FXML private Button markInterviewSuccessButton;
    @FXML private Button createMeetButton;
    @FXML private Button finalizeInterviewButton;
    @FXML private Button updateHistoryButton;
    @FXML private VBox coordinationTable;
    @FXML private VBox feedbackTable;

    @FXML
    private void initialize() {
        wireNavigation();
        discoverUnidentifiedControls();
        Parent root = FxNodes.rootOf(nav_InterviewWorkspace, nav_Login, finalizeInterviewButton, updateHistoryButton);
        ProfileCardBuilder.decorateScreen(root, store, this::refreshInterviews);

        if (rescheduleDatePicker != null && rescheduleDatePicker.getValue() == null) {
            rescheduleDatePicker.setValue(LocalDate.now().plusDays(1));
        }

        refreshInterviews();
        if (createMeetButton != null) createMeetButton.setOnAction(event -> openMeetCreationPage());
        if (finalizeInterviewButton != null) finalizeInterviewButton.setOnAction(event -> finalizeSelectedInterview());
        if (addDraftSlotButton != null) addDraftSlotButton.setOnAction(event -> handleAddDraftSlot());
        if (saveMultiSlotsButton != null) saveMultiSlotsButton.setOnAction(event -> handleSaveMultiSlots());
        if (rescheduleInterviewButton != null) rescheduleInterviewButton.setOnAction(event -> rescheduleSelectedInterview());
        refreshDraftSlotsBox();
        if (updateHistoryButton != null) updateHistoryButton.setOnAction(event -> updateSelectedInterviewHistory());
        if (markInterviewSuccessButton != null) {
            markInterviewSuccessButton.setOnAction(event -> markSelectedInterviewSuccessful());
        }
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
        if (stageCombo == null && combos.size() > 1) stageCombo = combos.get(combos.size() - 1);

        List<VBox> tables = FxNodes.findAllByStyleClass(root, "table", VBox.class);
        if (coordinationTable == null && !tables.isEmpty()) coordinationTable = tables.get(0);
        if (feedbackTable == null && tables.size() > 1) feedbackTable = tables.get(1);
    }

    private void refreshInterviews() {
        String username = activeUsername();
        String prevChoice = interviewCombo == null ? null : interviewCombo.getValue();
        interviewItems.setAll(store.visibleInterviews("STAFF", username));

        if (interviewCombo != null) {
            ObservableList<String> options = FXCollections.observableArrayList(
                    interviewItems.stream()
                            .sorted(Comparator.comparingLong(Interview::getId))
                            .map(this::formatInterviewChoice)
                            .collect(Collectors.toList()));
            interviewCombo.setItems(options);
            if (prevChoice != null && options.contains(prevChoice)) {
                interviewCombo.setValue(prevChoice);
            } else if (!options.isEmpty()) {
                interviewCombo.getSelectionModel().selectFirst();
            }
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
            row.getChildren().add(cell(safe(interview.getTeacherName()) + " · " + safe(interview.getPartner()), 400));
            row.getChildren().add(cell(formatTimeSlot(interview), 320));
            String statusAndResult = safe(interview.getStatus());
            if (!safe(interview.getResult()).isBlank() && !"Pending".equalsIgnoreCase(safe(interview.getResult()))) {
                statusAndResult += " · " + safe(interview.getResult());
            }
            row.getChildren().add(cell(statusAndResult, 250));
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

    private String formatTimeSlot(Interview interview) {
        if (interview == null) return "—";
        LocalDateTime start = PortalStore.parseInterviewStart(interview.getDateTime());
        LocalDateTime end = PortalStore.parseInterviewEnd(interview);
        if (start != null && end != null) {
            return start.format(DISPLAY_FMT) + " - " + end.format(DateTimeFormatter.ofPattern("HH:mm")) + " (UTC+7)";
        }
        return safe(interview.getDateTime());
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
        loadSelectedMeetingUrl();
    }

    private void loadSelectedMeetingUrl() {
        Interview selected = selectedInterview();
        if (selected == null) return;
        if (meetUrl != null) {
            String url = safe(selected.getMeetingUrl());
            if (url.isBlank()) {
                url = PortalStore.generateMeetingLink(selected);
            }
            meetUrl.setText(url);
        }
        LocalDateTime start = PortalStore.parseInterviewStart(selected.getDateTime());
        LocalDateTime end = PortalStore.parseInterviewEnd(selected);
        if (start != null && rescheduleDatePicker != null) {
            rescheduleDatePicker.setValue(start.toLocalDate());
        }
        if (start != null && rescheduleFromCombo != null) {
            rescheduleFromCombo.setValue(start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (end != null && rescheduleToCombo != null) {
            rescheduleToCombo.setValue(end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        if (staffResultNotesField != null) {
            staffResultNotesField.setText(safe(selected.getFeedback()));
        }
    }

    public List<PortalStore.TimeSlotSpec> getDraftSlots() {
        return Collections.unmodifiableList(draftSlots);
    }

    public boolean addDraftSlot(LocalDate date, LocalTime start, LocalTime end) {
        if (date == null || start == null || end == null) {
            showError("Missing schedule details", "Choose a date, start time, and end time (HH:mm).");
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
            showError("Missing schedule details", "Choose a date, start time, and end time (HH:mm).");
            return;
        }
        try {
            addDraftSlot(date, LocalTime.parse(fromText), LocalTime.parse(toText));
        } catch (Exception ex) {
            showError("Invalid time format", "Please enter times in HH:mm format (e.g. 14:00 to 14:30).");
        }
    }

    public boolean handleSaveMultiSlots() {
        Interview selected = selectedInterview();
        if (selected == null) {
            showError("No interview selected", "Select an interview to reschedule.");
            return false;
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
        if (!store.rescheduleInterviewMultiSlots(activeUsername(), selected.getId(), batch)) {
            showError("Schedule Conflict / Validation Error", store.getLastValidationError());
            return false;
        }
        int count = batch.size();
        draftSlots.clear();
        refreshDraftSlotsBox();
        refreshInterviews();
        showInfo("Interview Schedule Updated", "Saved and synchronized " + count + " time slot(s) for interview #" + selected.getId() + ".");
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

    private void rescheduleSelectedInterview() {
        if (!draftSlots.isEmpty()) {
            handleSaveMultiSlots();
            return;
        }
        Interview selected = selectedInterview();
        if (selected == null) {
            showError("No interview selected", "Select an interview to reschedule.");
            return;
        }
        LocalDate date = rescheduleDatePicker == null ? null : rescheduleDatePicker.getValue();
        String fromText = comboText(rescheduleFromCombo);
        String toText = comboText(rescheduleToCombo);
        if (date == null || fromText.isBlank() || toText.isBlank()) {
            showError("Missing schedule details", "Choose a date, start time, and end time (HH:mm).");
            return;
        }
        try {
            LocalTime startTime = LocalTime.parse(fromText);
            LocalTime endTime = LocalTime.parse(toText);
            LocalDateTime startAt = LocalDateTime.of(date, startTime);
            LocalDateTime endAt = LocalDateTime.of(date, endTime);
            if (!store.rescheduleInterview(activeUsername(), selected.getId(), startAt, endAt)) {
                showError("Schedule Conflict / Validation Error", store.getLastValidationError());
                return;
            }
            refreshInterviews();
            showInfo("Interview Rescheduled", "Updated interview #" + selected.getId() + " to "
                    + startAt.format(DISPLAY_FMT) + " - " + endAt.format(DateTimeFormatter.ofPattern("HH:mm")) + " (UTC+7).");
        } catch (Exception ex) {
            showError("Invalid time format", "Please enter times in HH:mm format (e.g. 14:00 to 14:30).");
        }
    }

    private String comboText(ComboBox<String> combo) {
        if (combo == null) return "";
        if (combo.isEditable() && combo.getEditor() != null && combo.getEditor().getText() != null && !combo.getEditor().getText().isBlank()) {
            return combo.getEditor().getText().trim();
        }
        return combo.getValue() == null ? "" : combo.getValue().trim();
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

    private void markSelectedInterviewSuccessful() {
        if (stageCombo != null) {
            stageCombo.setValue("Interview Successful");
        }
        updateSelectedInterviewHistory();
    }

    private void updateSelectedInterviewHistory() {
        Interview selected = selectedInterview();
        if (selected == null) {
            showError("No interview selected", "Select an interview in the Interview Coordination section first.");
            return;
        }
        String stage = stageCombo == null || stageCombo.getValue() == null
                ? "Interview Successful" : stageCombo.getValue();
        String notes = staffResultNotesField == null || staffResultNotesField.getText() == null
                ? "" : staffResultNotesField.getText().trim();
        boolean updated = store.updateInterviewResultByStaff(activeUsername(), selected.getId(), stage, notes);
        if (!updated) {
            showError("Permission denied", "Only Matching Staff or Administrators can update interview results.");
            return;
        }
        refreshInterviews();
        showInfo("Interview Result Updated", "Updated interview #" + selected.getId()
                + " to '" + stage + "' and notified Teacher and Partner.");
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
