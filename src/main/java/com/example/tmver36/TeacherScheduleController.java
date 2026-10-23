package com.example.tmver36;

import com.example.tmver36.model.Interview;
import com.example.tmver36.service.PortalStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
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
 * JavaFX controller for teacher availability and interview requests.
 * Interview decisions are persisted through PortalStore. Availability slots are UI-session
 * data because PortalStore currently has no availability-slot model or CRUD API.
 */
public class TeacherScheduleController {
    private static final PortalStore STORE = LoginController.STORE;
    private static final long DEMO_TEACHER_ID = 1L;
    private static final DateTimeFormatter SLOT_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @FXML private Button nav_TeacherProfile;
    @FXML private Button nav_TeacherDocuments;
    @FXML private Button nav_TeacherSchedule;
    @FXML private Button nav_TeacherHistory;
    @FXML private Button nav_Login;
    @FXML private DatePicker availabilityDate;
    @FXML private Button addSlotButton;
    @FXML private Button removeSlotButton;
    @FXML private Button removeSlot2Button;
    @FXML private Button acceptInterviewButton;
    @FXML private Button declineInterviewButton;
    @FXML private Button openMeetButton;

    private final ObservableList<AvailabilitySlot> slots = FXCollections.observableArrayList();
    private ComboBox<String> fromCombo;
    private ComboBox<String> toCombo;
    private TextField meetLinkField;
    private VBox slotsTable;
    private VBox pendingCard;
    private VBox confirmedCard;
    private Label statusMessage;
    private long pendingInterviewId = -1L;

    @FXML
    private void initialize() {
        Parent root = root();
        if (root == null) return;

        locateControls(root);
        wireNavigation();
        wireSlotActions();
        wireInterviewActions();
        wireMeetAction();
        loadSlotsFromExistingView();
        renderSlots();
        refreshInterviews();
    }

    private void locateControls(Parent root) {
        List<ComboBox> combos = root.lookupAll("ComboBox").stream()
                .filter(ComboBox.class::isInstance).map(ComboBox.class::cast).toList();
        if (!combos.isEmpty()) fromCombo = combos.get(0);
        if (combos.size() > 1) toCombo = combos.get(1);

        List<TextField> textFields = root.lookupAll("TextField").stream()
                .filter(TextField.class::isInstance).map(TextField.class::cast).toList();
        if (!textFields.isEmpty()) meetLinkField = textFields.get(0);

        slotsTable = root.lookupAll(".table").stream()
                .filter(VBox.class::isInstance).map(VBox.class::cast)
                .filter(box -> box.getChildren().stream().anyMatch(node -> node instanceof HBox h && h.getStyleClass().contains("table-header")))
                .findFirst().orElse(null);

        List<VBox> cards = root.lookupAll(".card").stream()
                .filter(VBox.class::isInstance).map(VBox.class::cast).toList();
        for (VBox card : cards) {
            String text = card.getChildren().stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).map(Label::getText).filter(s -> s != null)
                    .reduce("", (a, b) -> a + " " + b).toLowerCase(Locale.ROOT);
            if (text.contains("pending requests for you")) pendingCard = card;
            if (text.contains("confirmed interviews")) confirmedCard = card;
        }

        statusMessage = root.lookupAll(".note").stream().filter(Label.class::isInstance)
                .map(Label.class::cast).findFirst().orElse(null);
    }

    private void wireNavigation() {
        if (nav_TeacherProfile != null) nav_TeacherProfile.setOnAction(e -> navigate("TeacherProfile.fxml", nav_TeacherProfile));
        if (nav_TeacherDocuments != null) nav_TeacherDocuments.setOnAction(e -> navigate("TeacherDocuments.fxml", nav_TeacherDocuments));
        if (nav_TeacherSchedule != null) nav_TeacherSchedule.setOnAction(e -> refreshInterviews());
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

    private void loadSlotsFromExistingView() {
        // Preserve the two initial rows already present in the supplied FXML as demo-session slots.
        // They are not persisted by PortalStore because it has no availability-slot API.
        if (slotsTable == null) return;
        for (Node node : new ArrayList<>(slotsTable.getChildren())) {
            if (!(node instanceof HBox row) || !row.getStyleClass().contains("table-row")) continue;
            List<String> labels = row.lookupAll("Label").stream().filter(Label.class::isInstance)
                    .map(Label.class::cast).map(Label::getText).toList();
            if (labels.size() >= 2) {
                String date = labels.get(0).trim();
                String time = labels.get(1).trim();
                String[] parts = time.split("\\s*[-–]\\s*");
                if (parts.length == 2) slots.add(new AvailabilitySlot(date, parts[0], parts[1]));
            }
        }
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
        String dateText = date.format(SLOT_DATE);
        boolean duplicate = slots.stream().anyMatch(slot -> slot.date().equals(dateText)
                && slot.from().equals(from) && slot.to().equals(to));
        if (duplicate) {
            setMessage("That availability slot already exists.");
            return;
        }
        slots.add(new AvailabilitySlot(dateText, from, to));
        slots.sort(Comparator.comparing(AvailabilitySlot::date).thenComparing(AvailabilitySlot::from));
        STORE.log(currentUsername(), "Added availability slot " + dateText + " " + from + "-" + to + " (session only)");
        renderSlots();
        setMessage("Availability slot added for this session. PortalStore does not currently persist availability slots.");
    }

    private void removeSlotByButton(Button button) {
        if (slots.isEmpty()) {
            setMessage("There are no availability slots to delete.");
            return;
        }
        // The original FXML has two row-specific delete buttons; retain their intuitive first/second-row behavior.
        int index = button == removeSlot2Button ? Math.min(1, slots.size() - 1) : 0;
        AvailabilitySlot removed = slots.remove(index);
        STORE.log(currentUsername(), "Removed availability slot " + removed.date() + " " + removed.from() + "-" + removed.to() + " (session only)");
        renderSlots();
        setMessage("Availability slot removed from this session.");
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
        for (int i = 0; i < slots.size(); i++) {
            AvailabilitySlot slot = slots.get(i);
            HBox row = makeSlotRow(slot.date(), slot.from() + " - " + slot.to(), "Available");
            Button remove = new Button("Delete time");
            remove.getStyleClass().add("danger");
            remove.setOnAction(e -> {
                slots.remove(slot);
                STORE.log(currentUsername(), "Removed availability slot " + slot.date() + " " + slot.from() + "-" + slot.to() + " (session only)");
                renderSlots();
                setMessage("Availability slot removed from this session.");
            });
            VBox actionCell = (VBox) row.getChildren().get(3);
            actionCell.getChildren().clear();
            actionCell.getChildren().add(remove);
            slotsTable.getChildren().add(row);
        }
    }

    private HBox makeSlotRow(String date, String time, String status) {
        HBox row = new HBox(0);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getStyleClass().add("table-row");
        row.getChildren().add(cell(date, 230));
        row.getChildren().add(cell(time, 270));
        VBox statusCell = new VBox();
        statusCell.setMinWidth(230); statusCell.setPrefWidth(230); statusCell.setMaxWidth(230);
        statusCell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label statusLabel = new Label(status);
        statusLabel.getStyleClass().addAll("badge", "badge-green");
        statusCell.getChildren().add(statusLabel);
        row.getChildren().add(statusCell);
        VBox actionCell = new VBox();
        actionCell.setMinWidth(250); actionCell.setPrefWidth(250); actionCell.setMaxWidth(250);
        actionCell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.getChildren().add(actionCell);
        return row;
    }

    private VBox cell(String value, double width) {
        VBox cell = new VBox();
        cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        cell.setMinWidth(width); cell.setPrefWidth(width); cell.setMaxWidth(width);
        Label label = new Label(value);
        label.setWrapText(true); label.setMaxWidth(width - 16);
        label.getStyleClass().add("table-text");
        cell.getChildren().add(label);
        return cell;
    }

    private void refreshInterviews() {
        String username = currentUsername();
        List<Interview> interviews = new ArrayList<>(STORE.visibleInterviews("TEACHER", username));
        interviews.removeIf(i -> i == null || i.getTeacherId() != DEMO_TEACHER_ID);
        interviews.sort(Comparator.comparing(Interview::getDateTime, Comparator.nullsLast(String::compareTo)));

        Interview pending = interviews.stream().filter(i -> "PENDING".equalsIgnoreCase(safe(i.getStatus())))
                .findFirst().orElse(null);
        pendingInterviewId = pending == null ? -1L : pending.getId();
        renderInterviewCard(pendingCard, pending, true);

        List<Interview> confirmed = interviews.stream().filter(i -> "CONFIRMED".equalsIgnoreCase(safe(i.getStatus()))).toList();
        renderConfirmedCard(confirmedCard, confirmed);
        if (acceptInterviewButton != null) acceptInterviewButton.setDisable(pending == null);
        if (declineInterviewButton != null) declineInterviewButton.setDisable(pending == null);
    }

    private void renderInterviewCard(VBox card, Interview interview, boolean isPending) {
        if (card == null) return;
        Label detail = card.lookupAll("Label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> safe(label.getText()).contains("Online interview") || safe(label.getText()).contains("No pending"))
                .findFirst().orElse(null);
        Label when = card.lookupAll("Label").stream().filter(Label.class::isInstance).map(Label.class::cast)
                .filter(label -> safe(label.getText()).contains("UTC+7") || safe(label.getText()).contains("waiting for"))
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
        List<Label> labels = card.lookupAll("Label").stream().filter(Label.class::isInstance).map(Label.class::cast).toList();
        Label detail = labels.stream().filter(label -> safe(label.getText()).contains("Green English Center") || safe(label.getText()).contains("No confirmed interviews"))
                .findFirst().orElse(null);
        if (confirmed.isEmpty()) {
            if (detail != null) detail.setText("No confirmed interviews yet.");
            if (meetLinkField != null) meetLinkField.clear();
            if (openMeetButton != null) openMeetButton.setDisable(true);
            return;
        }
        Interview first = confirmed.get(0);
        if (detail != null) detail.setText(safe(first.getPartner()) + " · " + formatDateTime(first.getDateTime()) + " · CONFIRMED");
        if (meetLinkField != null) {
            // The supplied Interview model may not expose a meeting-link getter; only replace the field when available.
            String url = readStringProperty(first, "getMeetUrl", "getMeetingUrl", "getMeetLink", "getUrl");
            meetLinkField.setText(url);
        }
        if (openMeetButton != null) openMeetButton.setDisable(meetLinkField == null || safe(meetLinkField.getText()).isBlank());
    }

    private void updatePendingInterview(String status) {
        if (pendingInterviewId < 0) {
            setMessage("There is no pending interview request to update.");
            return;
        }
        STORE.updateInterview(pendingInterviewId, status, null, null, "");
        STORE.log(currentUsername(), "Teacher " + status.toLowerCase(Locale.ROOT) + " interview #" + pendingInterviewId);
        refreshInterviews();
        setMessage("Interview request updated: " + status + ".");
    }

    private void openMeetLink() {
        String url = meetLinkField == null ? "" : safe(meetLinkField.getText());
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
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(uri);
            else setMessage("Opening a browser is not supported on this computer.");
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

    private String readStringProperty(Object target, String... getterNames) {
        for (String getter : getterNames) {
            try {
                Object value = target.getClass().getMethod(getter).invoke(target);
                if (value != null && !value.toString().isBlank()) return value.toString();
            } catch (ReflectiveOperationException ignored) { }
        }
        return "";
    }

    private Parent root() {
        Button source = nav_TeacherSchedule != null ? nav_TeacherSchedule : nav_TeacherProfile;
        return source == null || source.getScene() == null ? null : source.getScene().getRoot();
    }

    private void navigate(String fxml, Node source) { LoginController.navigateTo(source, fxml); }
    private String currentUsername() {
        String username = LoginController.getCurrentUsername();
        return username == null || username.isBlank() ? "teacher" : username;
    }
    private void setMessage(String message) { if (statusMessage != null) statusMessage.setText(message); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private record AvailabilitySlot(String date, String from, String to) { }
}
