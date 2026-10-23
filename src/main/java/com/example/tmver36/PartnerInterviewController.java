package com.example.tmver36;

import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Partner interview requests and feedback backed by PortalStore. */
public class PartnerInterviewController {
    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_PartnerDashboard;
    @FXML private Button nav_PartnerProfile;
    @FXML private Button nav_PartnerInterview;
    @FXML private Button nav_Login;
    @FXML private ComboBox<String> requestedSlot;
    @FXML private Button requestInterviewButton;
    @FXML private Button openPartnerMeetButton;
    @FXML private ComboBox<String> interviewResult;
    @FXML private TextArea resultComments;
    @FXML private Button sendResultButton;

    private ComboBox<String> interviewSelector;
    private ComboBox<String> suitabilitySelector;
    private TextArea requestMessage;
    private javafx.scene.control.TextField interviewerField;
    private VBox requestsTable;

    @FXML
    private void initialize() {
        Parent root = nav_PartnerInterview == null ? null : nav_PartnerInterview.getParent();
        while (root != null && !(root instanceof javafx.scene.layout.BorderPane)) root = root.getParent();
        if (root == null && nav_PartnerInterview != null && nav_PartnerInterview.getScene() != null) root = nav_PartnerInterview.getScene().getRoot();
        if (root == null) return;

        // FXML currently lacks fx:id on these controls, so identify them by their containing labels.
        for (Node node : root.lookupAll("VBox")) {
            if (!(node instanceof VBox box)) continue;
            String labelText = descendantLabelText(box);
            if (labelText.contains("Interview") && box.lookup("ComboBox") instanceof ComboBox<?> combo && combo != requestedSlot && combo != interviewResult) {
                @SuppressWarnings("unchecked") ComboBox<String> typed = (ComboBox<String>) combo;
                interviewSelector = typed;
            }
            if (labelText.contains("Suitability") && box.lookup("ComboBox") instanceof ComboBox<?> combo) {
                @SuppressWarnings("unchecked") ComboBox<String> typed = (ComboBox<String>) combo;
                suitabilitySelector = typed;
            }
            if (labelText.contains("Message to teacher") && box.lookup("TextArea") instanceof TextArea area) requestMessage = area;
            if (labelText.contains("Interviewer") && box.lookup("TextField") instanceof javafx.scene.control.TextField field) interviewerField = field;
        }
        for (Node node : root.lookupAll(".table")) if (node instanceof VBox box) { requestsTable = box; break; }

        if (requestInterviewButton != null) requestInterviewButton.setOnAction(e -> handleRequestInterview());
        if (sendResultButton != null) sendResultButton.setOnAction(e -> handleSendResult());
        if (openPartnerMeetButton != null) openPartnerMeetButton.setOnAction(e -> openMeet());
        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> navigate("PartnerDashboard.fxml"));
        if (nav_PartnerProfile != null) nav_PartnerProfile.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerInterview != null) nav_PartnerInterview.setOnAction(e -> refreshView());
        if (nav_Login != null) nav_Login.setOnAction(e -> { if (!LoginController.navigateTo(nav_Login, "Login.fxml")) showError("Navigation failed", "Could not load Login.fxml."); });

        refreshView();
    }

    private void handleRequestInterview() {
        String username = LoginController.getCurrentUsername();
        String partnerName = partnerDisplayName(username);
        String slot = requestedSlot == null ? null : requestedSlot.getValue();
        if (username == null || partnerName.isBlank()) { showError("Session expired", "Please log in again as a partner."); return; }
        if (slot == null || slot.isBlank()) { showError("Choose a time", "Select an available interview time slot."); return; }

        List<TeacherProfile> teachers = STORE.visibleTeachers(username, "PARTNER");
        if (teachers.isEmpty()) { showError("No shared profiles", "Matching has not granted access to any teacher profiles."); return; }
        TeacherProfile selectedTeacher = teachers.stream().filter(t -> "Emily Martin".equalsIgnoreCase(String.valueOf(read(t, "getName")))).findFirst().orElse(teachers.get(0));
        String dateTime = toStoreDateTime(slot);
        if (dateTime == null) { showError("Invalid time slot", "The selected slot could not be parsed. Use dd/MM/yyyy · HH:mm - HH:mm format."); return; }

        boolean created = STORE.requestInterview(((Number) read(selectedTeacher, "getId")).longValue(), partnerName, dateTime);
        if (!created) { showError("Request failed", "The interview request could not be saved."); return; }
        if (requestMessage != null && !requestMessage.getText().isBlank()) {
            STORE.log(username, "Interview request note: " + requestMessage.getText().trim());
        }
        refreshView();
        showInfo("Request sent", "Interview request saved for " + read(selectedTeacher, "getName") + " at " + slot + ".");
    }

    private void handleSendResult() {
        String username = LoginController.getCurrentUsername();
        String selected = interviewSelector == null ? null : interviewSelector.getValue();
        String result = interviewResult == null ? null : interviewResult.getValue();
        String comments = resultComments == null ? "" : resultComments.getText().trim();
        if (username == null) { showError("Session expired", "Please log in again."); return; }
        if (selected == null || selected.isBlank()) { showError("Choose interview", "Select the interview to evaluate."); return; }
        if (result == null || result.isBlank() || comments.isBlank()) { showError("Incomplete result", "Choose a result and enter comments/reasons."); return; }

        Interview interview = findInterviewFromLabel(selected);
        if (interview == null) { showError("Interview not found", "The selected interview is no longer available in PortalStore."); return; }
        String mappedResult = switch (result) {
            case "Proceed with Candidate" -> "PROCEED";
            case "Not Proceeding" -> "NOT_PROCEEDING";
            case "Require Additional Interview/ Materials" -> "ADDITIONAL_INTERVIEW";
            default -> result;
        };
        String suitability = suitabilitySelector == null || suitabilitySelector.getValue() == null ? "" : suitabilitySelector.getValue();
        String feedback = suitability.isBlank() ? comments : "Suitability: " + suitability + "\n" + comments;
        boolean saved = STORE.updatePartnerFeedback(((Number) read(interview, "getId")).longValue(), username, mappedResult, feedback);
        if (!saved) { showError("Could not save result", "PortalStore rejected this update. Confirm that the interview belongs to the currently logged-in partner."); return; }
        refreshView();
        showInfo("Result saved", "Your evaluation and comments have been saved to PortalStore.");
    }

    private void refreshView() {
        String username = LoginController.getCurrentUsername();
        List<Interview> interviews = STORE.visibleInterviews("PARTNER", username);
        if (interviewSelector != null) {
            String previous = interviewSelector.getValue();
            interviewSelector.getItems().setAll(interviews.stream().map(this::interviewLabel).toList());
            if (previous != null && interviewSelector.getItems().contains(previous)) interviewSelector.setValue(previous);
            else if (!interviewSelector.getItems().isEmpty()) interviewSelector.getSelectionModel().selectFirst();
        }
        renderRequests(interviews);
    }

    private void renderRequests(List<Interview> interviews) {
        if (requestsTable == null || requestsTable.getChildren().isEmpty()) return;
        // Keep the FXML header row; replace only the mock data rows with real PortalStore rows.
        while (requestsTable.getChildren().size() > 1) requestsTable.getChildren().remove(1);
        for (Interview interview : interviews) {
            HBox row = new HBox(0);
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(String.valueOf(read(interview, "getTeacherName")), 210));
            row.getChildren().add(cell(formatDateTime(String.valueOf(read(interview, "getDateTime"))), 260));
            row.getChildren().add(cell(String.valueOf(read(interview, "getStatus")), 250));
            row.getChildren().add(cell("CONFIRMED".equalsIgnoreCase(String.valueOf(read(interview, "getStatus"))) ? "https://meet.google.com/" : "Awaiting confirmation", 250));
            requestsTable.getChildren().add(row);
        }
    }

    private VBox cell(String text, double width) {
        VBox box = new VBox(); box.setMinWidth(width); box.setPrefWidth(width); box.setMaxWidth(width); box.setAlignment(javafx.geometry.Pos.CENTER_LEFT); box.setStyle("-fx-padding: 12 8 12 8;");
        Label label = new Label(text == null || "null".equals(text) ? "—" : text); label.setWrapText(true); label.getStyleClass().add("table-text"); box.getChildren().add(label); return box;
    }

    private void openMeet() {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(new URI("https://meet.google.com/"));
            else showError("Browser unavailable", "Open https://meet.google.com/ in your browser.");
        } catch (Exception ex) { showError("Could not open Meet", ex.getMessage() == null ? "Open https://meet.google.com/ manually." : ex.getMessage()); }
    }

    private Interview findInterviewFromLabel(String label) {
        return STORE.visibleInterviews("PARTNER", LoginController.getCurrentUsername()).stream()
                .filter(i -> interviewLabel(i).equals(label)).findFirst().orElse(null);
    }

    private String interviewLabel(Interview interview) {
        return safe(read(interview, "getTeacherName")) + " · " + formatDateTime(safe(read(interview, "getDateTime")));
    }

    private String partnerDisplayName(String username) {
        return STORE.users().stream().filter(u -> u.getUsername().equals(username)).map(u -> u.getFullName() == null || u.getFullName().isBlank() ? u.getUsername() : u.getFullName()).findFirst().orElse("");
    }

    private String toStoreDateTime(String slot) {
        try {
            // Example: 16/10/2026 · 14:00 - 15:00
            String left = slot.split("-")[0].trim().replace("·", " ").trim();
            return LocalDateTime.parse(left, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")).toString();
        } catch (RuntimeException ex) { return null; }
    }

    private String formatDateTime(String raw) {
        try { return LocalDateTime.parse(raw).format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")); }
        catch (RuntimeException ex) { return raw == null || "null".equals(raw) ? "—" : raw; }
    }

    private String descendantLabelText(VBox box) {
        StringBuilder out = new StringBuilder();
        for (Node n : box.lookupAll("Label")) if (n instanceof Label l && l.getText() != null) out.append(l.getText()).append(' ');
        return out.toString();
    }
    private Object read(Object object, String getter) {
        if (object == null) return null;
        try { return object.getClass().getMethod(getter).invoke(object); }
        catch (ReflectiveOperationException ex) { return null; }
    }
    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private void navigate(String fxml) { if (nav_PartnerInterview != null && !LoginController.navigateTo(nav_PartnerInterview, fxml)) showError("Navigation failed", "Could not load " + fxml + "."); }
    private void showError(String title, String message) { Alert a = new Alert(Alert.AlertType.ERROR); a.setTitle(title); a.setHeaderText(null); a.setContentText(message); a.showAndWait(); }
    private void showInfo(String title, String message) { Alert a = new Alert(Alert.AlertType.INFORMATION); a.setTitle(title); a.setHeaderText(null); a.setContentText(message); a.showAndWait(); }
}
