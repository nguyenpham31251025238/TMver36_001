package com.example.tmver36;

import com.example.tmver36.model.AvailabilitySlot;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherProfile;
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

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * JavaFX controller for teacher availability and interview requests (TeacherSchedule.fxml).
 */
public class TeacherScheduleController {

    private static final PortalStore STORE = LoginController.STORE;
    private static final DateTimeFormatter SLOT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Button nav_TeacherProfile;
    @FXML private Button nav_TeacherDocuments;
    @FXML private Button nav_TeacherSchedule;
    @FXML private Button nav_TeacherHistory;
    @FXML private Button nav_Login;

    @FXML private DatePicker availabilityDate;
    @FXML private ComboBox<String> fromCombo;
    @FXML private ComboBox<String> toCombo;
    @FXML private Button addSlotButton;
    @FXML private Button removeSlotButton;
    @FXML private Button removeSlot2Button;
    @FXML private VBox slotsTable;

    @FXML private Button acceptInterviewButton;
    @FXML private Button declineInterviewButton;
    @FXML private TextField meetLinkField;
    @FXML private Button openMeetButton;

    private final ObservableList<AvailabilitySlot> slots = FXCollections.observableArrayList();
    private VBox pendingCard;
    private VBox confirmedCard;
    private Label statusMessage;
    private long pendingInterviewId = -1L;

    @FXML
    private void initialize() {
        wireNavigation();

        Parent root = FxNodes.rootOf(
                nav_TeacherSchedule,
                nav_TeacherDocuments,
                nav_TeacherProfile,
                addSlotButton,
                slotsTable,
                meetLinkField
        );
        if (root != null) {
            locateControls(root);
            updateSidebarHeader(root);
        }

        if (availabilityDate != null && availabilityDate.getValue() == null) {
            availabilityDate.setValue(LocalDate.now().plusDays(2));
        }

        wireSlotActions();
        wireInterviewActions();
        wireMeetAction();

        slots.setAll(STORE.availabilitySlots(currentTeacherId()));
        renderSlots();
        refreshInterviews();
    }

    private void updateSidebarHeader(Parent root) {
        TeacherProfile profile = STORE.teacher(currentTeacherId());
        if (profile == null) return;
        for (Label label : FxNodes.findAllByStyleClass(root, "sidebar-name", Label.class)) {
            label.setText(profile.getName());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void locateControls(Parent root) {
        if (slotsTable == null) {
            slotsTable = FxNodes.findFirstByStyleClass(root, "table", VBox.class);
        }
        if (fromCombo == null || toCombo == null) {
            List<ComboBox> combos = FxNodes.findAll(root, ComboBox.class);
            if (!combos.isEmpty() && fromCombo == null) fromCombo = combos.get(0);
            if (combos.size() > 1 && toCombo == null) toCombo = combos.get(1);
        }
        if (meetLinkField == null) {
            for (TextField tf : FxNodes.findAll(root, TextField.class)) {
                meetLinkField = tf;
                break;
            }
        }
        for (VBox card : FxNodes.findAllByStyleClass(root, "card", VBox.class)) {
            String text = FxNodes.findAll(card, Label.class).stream()
                    .map(Label::getText)
                    .filter(s -> s != null)
                    .reduce("", (a, b) -> a + " " + b)
                    .toLowerCase(Locale.ROOT);
            if (text.contains("pending requests for you")) pendingCard = card;
            if (text.contains("confirmed interviews")) confirmedCard = card;
        }
        statusMessage = FxNodes.findFirstByStyleClass(root, "note", Label.class);
    }

    private void wireNavigation() {
        if (nav_TeacherProfile != null) nav_TeacherProfile.setOnAction(e -> navigate("TeacherProfile.fxml", nav_TeacherProfile));
        if (nav_TeacherDocuments != null) nav_TeacherDocuments.setOnAction(e -> navigate("TeacherDocuments.fxml", nav_TeacherDocuments));
        if (nav_TeacherSchedule != null) nav_TeacherSchedule.setOnAction(e -> {
            slots.setAll(STORE.availabilitySlots(currentTeacherId()));
            renderSlots();
            refreshInterviews();
        });
        if (nav_TeacherHistory != null) nav_TeacherHistory.setOnAction(e -> navigate("TeacherHistory.fxml", nav_TeacherHistory));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void wireSlotActions() {
        if (addSlotButton != null) addSlotButton.setOnAction(e -> addSlot());
        if (removeSlotButton != null) removeSlotButton.setOnAction(e -> removeSlotByButton(removeSlotButton));
        if (removeSlot2Button != null) removeSlot2Button.setOnAction(e -> removeSlotByButton(removeSlot2Button));
    }

    private void wireInterviewActions() {
        if (acceptInterviewButton != null) acceptInterviewButton.setOnAction(e -> updatePendingInterview("CONFIRMED"));
        if (declineInterviewButton != null) declineInterviewButton.setOnAction(e -> updatePendingInterview("DECLINED"));
    }

    private void wireMeetAction() {
        if (openMeetButton != null) openMeetButton.setOnAction(e -> openMeetLink());
    }

    private void addSlot() {
        if (availabilityDate == null || availabilityDate.getValue() == null || fromCombo == null || toCombo == null) {
            setMessage("Choose a date and both start and end times before adding a slot.");
            return;
        }
        LocalDate date = availabilityDate.getValue();
        String from = fromCombo.getValue();
        String to = toCombo.getValue();
        if (from == null || to == null) {
            setMessage("Choose a start time and an end time.");
            return;
        }
        try {
            if (!LocalTime.parse(to).isAfter(LocalTime.parse(from))) {
                setMessage("The end time must be later than the start time.");
                return;
            }
        } catch (RuntimeException ex) {
            setMessage("The selected time format is invalid.");
            return;
        }
        LocalDateTime startAt = LocalDateTime.of(date, LocalTime.parse(from));
        LocalDateTime endAt = LocalDateTime.of(date, LocalTime.parse(to));
        long teacherId = currentTeacherId();
        if (!STORE.addAvailabilitySlot(currentUsername(), teacherId, startAt, endAt)) {
            setMessage("The slot must not overlap an existing slot.");
            return;
        }
        slots.setAll(STORE.availabilitySlots(teacherId));
        renderSlots();
        setMessage("Availability slot added.");
    }

    private void removeSlotByButton(Button button) {
        if (slots.isEmpty()) {
            setMessage("There are no availability slots to delete.");
            return;
        }
        int index = button == removeSlot2Button ? Math.min(1, slots.size() - 1) : 0;
        AvailabilitySlot removed = slots.get(index);
        STORE.removeAvailabilitySlot(currentUsername(), removed.id());
        slots.setAll(STORE.availabilitySlots(currentTeacherId()));
        renderSlots();
        setMessage("Availability slot removed.");
    }

    private void renderSlots() {
        if (slotsTable == null) return;
        Node header = slotsTable.getChildren().stream()
                .filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header"))
                .findFirst().orElse(null);
        slotsTable.getChildren().clear();
        if (header != null) slotsTable.getChildren().add(header);
        if (slots.isEmpty()) {
            slotsTable.getChildren().add(makeSlotRow("—", "No availability slots", "Add a slot above."));
            return;
        }
        for (AvailabilitySlot slot : slots) {
            HBox row = makeSlotRow(
                    slot.startAt().format(SLOT_DATE),
                    slot.startAt().format(DateTimeFormatter.ofPattern("HH:mm")) + " - " + slot.endAt().format(DateTimeFormatter.ofPattern("HH:mm")),
                    "Available"
            );
            Button remove = new Button("Delete time");
            remove.getStyleClass().add("danger");
            remove.setOnAction(e -> {
                STORE.removeAvailabilitySlot(currentUsername(), slot.id());
                slots.setAll(STORE.availabilitySlots(currentTeacherId()));
                renderSlots();
                setMessage("Availability slot removed.");
            });
            VBox actionCell = (VBox) row.getChildren().get(3);
            actionCell.getChildren().clear();
            actionCell.getChildren().add(remove);
            slotsTable.getChildren().add(row);
        }
    }

    private HBox makeSlotRow(String date, String time, String status) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("table-row");
        row.getChildren().add(cell(date, 230));
        row.getChildren().add(cell(time, 270));
        VBox statusCell = new VBox();
        statusCell.setMinWidth(230);
        statusCell.setPrefWidth(230);
        statusCell.setMaxWidth(230);
        statusCell.setAlignment(Pos.CENTER_LEFT);
        Label statusLabel = new Label(status);
        statusLabel.getStyleClass().addAll("badge", "badge-green");
        statusCell.getChildren().add(statusLabel);
        row.getChildren().add(statusCell);
        VBox actionCell = new VBox();
        actionCell.setMinWidth(250);
        actionCell.setPrefWidth(250);
        actionCell.setMaxWidth(250);
        actionCell.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().add(actionCell);
        return row;
    }

    private VBox cell(String value, double width) {
        VBox cell = new VBox();
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        Label label = new Label(value);
        label.setWrapText(true);
        label.setMaxWidth(width - 16);
        label.getStyleClass().add("table-text");
        cell.getChildren().add(label);
        return cell;
    }

    private void refreshInterviews() {
        long teacherId = currentTeacherId();
        List<Interview> interviews = new ArrayList<>(STORE.visibleInterviews("TEACHER", currentUsername()));
        interviews.removeIf(i -> i == null || i.getTeacherId() != teacherId);
        interviews.sort(Comparator.comparing(Interview::getDateTime, Comparator.nullsLast(String::compareTo)));

        Interview pending = interviews.stream()
                .filter(i -> "PENDING".equalsIgnoreCase(safe(i.getStatus())))
                .findFirst().orElse(null);
        pendingInterviewId = pending == null ? -1L : pending.getId();
        renderInterviewCard(pendingCard, pending);

        List<Interview> confirmed = interviews.stream()
                .filter(i -> "CONFIRMED".equalsIgnoreCase(safe(i.getStatus())))
                .toList();
        renderConfirmedCard(confirmedCard, confirmed);
        if (acceptInterviewButton != null) acceptInterviewButton.setDisable(pending == null);
        if (declineInterviewButton != null) declineInterviewButton.setDisable(pending == null);
    }

    private void renderInterviewCard(VBox card, Interview interview) {
        if (card == null) return;
        List<Label> labels = FxNodes.findAll(card, Label.class);
        Label detail = labels.stream()
                .filter(label -> safe(label.getText()).contains("Online interview") || safe(label.getText()).contains("No pending"))
                .findFirst().orElse(null);
        Label when = labels.stream()
                .filter(label -> safe(label.getText()).contains("UTC+7") || safe(label.getText()).contains("waiting for") || safe(label.getText()).contains("New requests"))
                .findFirst().orElse(null);
        if (interview == null) {
            if (detail != null) detail.setText("No pending interview requests");
            if (when != null) when.setText("New requests will appear here.");
            return;
        }
        if (detail != null) detail.setText(safe(interview.getPartner()) + " · Online interview");
        if (when != null) when.setText(formatDateTime(interview.getDateTime()) + " · UTC+7 · " + safe(interview.getStatus()));
    }

    private void renderConfirmedCard(VBox card, List<Interview> confirmed) {
        if (card == null) return;
        List<Label> labels = FxNodes.findAll(card, Label.class);
        Label detail = labels.stream()
                .filter(label -> safe(label.getText()).contains("Green English Center")
                        || safe(label.getText()).contains("Bright School")
                        || safe(label.getText()).contains("No confirmed interviews")
                        || safe(label.getText()).contains("CONFIRMED"))
                .findFirst().orElse(null);
        if (confirmed.isEmpty()) {
            if (detail != null) detail.setText("No confirmed interviews yet.");
            if (meetLinkField != null) meetLinkField.clear();
            if (openMeetButton != null) openMeetButton.setDisable(true);
            return;
        }
        Interview first = confirmed.get(0);
        if (detail != null) detail.setText(safe(first.getPartner()) + " · " + formatDateTime(first.getDateTime()) + " · CONFIRMED");
        String url = safe(first.getMeetingUrl());
        if (url.isBlank()) {
            url = PortalStore.generateMeetingLink(first);
        }
        if (meetLinkField != null) {
            meetLinkField.setText(url);
        }
        if (openMeetButton != null) {
            openMeetButton.setDisable(false);
        }
    }

    private void updatePendingInterview(String status) {
        if (pendingInterviewId < 0) {
            setMessage("There is no pending interview request to update.");
            return;
        }
        String link = meetLinkField == null ? "" : safe(meetLinkField.getText());
        if (!STORE.updateInterview(currentUsername(), pendingInterviewId, status, null, null, link)) {
            setMessage("This account cannot update the selected interview.");
            return;
        }
        refreshInterviews();
        setMessage("Interview request updated: " + status + ".");
    }

    private void openMeetLink() {
        String url = meetLinkField == null ? "" : safe(meetLinkField.getText());
        if (url.isBlank()) {
            List<Interview> visible = STORE.visibleInterviews("TEACHER", currentUsername());
            if (!visible.isEmpty()) {
                url = PortalStore.generateMeetingLink(visible.get(0));
                if (meetLinkField != null) {
                    meetLinkField.setText(url);
                }
            }
        }
        if (url.isBlank()) {
            setMessage("No meeting link is available for this interview yet.");
            return;
        }
        try {
            URI uri = URI.create(url);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                setMessage("The meeting link must begin with http:// or https://.");
                return;
            }
            try {
                ClipboardContent content = new ClipboardContent();
                content.putString(url);
                Clipboard.getSystemClipboard().setContent(content);
            } catch (Exception ignored) {
            }
            PortalStore.openInBrowser(url);
            setMessage("Opened and copied meeting link: " + url);
        } catch (Exception ex) {
            setMessage("Could not open the meeting link: " + ex.getMessage());
        }
    }

    private String formatDateTime(String value) {
        if (value == null || value.isBlank()) return "Date/time not set";
        try {
            return LocalDateTime.parse(value).format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm"));
        } catch (RuntimeException ignored) {
            return value.replace('T', ' ');
        }
    }

    private long currentTeacherId() {
        long id = STORE.teacherIdForUser(currentUsername());
        return id > 0 ? id : 1L;
    }

    private void navigate(String fxml, Node source) {
        LoginController.navigateTo(source, fxml);
    }

    private String currentUsername() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "teacher" : u;
    }

    private void setMessage(String message) {
        if (statusMessage != null) {
            statusMessage.setText(message);
        }
        if ("The end time must be later than the start time.".equals(message)) {
            LoginController.showAlert(Alert.AlertType.ERROR, "Invalid Time Range", message);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
