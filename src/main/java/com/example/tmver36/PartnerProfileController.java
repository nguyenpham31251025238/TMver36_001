package com.example.tmver36;

import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Partner candidate profile screen backed by PortalStore. */
public class PartnerProfileController {
    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_PartnerDashboard;
    @FXML private Button nav_PartnerProfile;
    @FXML private Button nav_PartnerInterview;
    @FXML private Button nav_Login;
    @FXML private Button playIntroButton;
    @FXML private Button partnerCvButton;
    @FXML private Button partnerCertButton;
    @FXML private Button partnerDemoButton;
    @FXML private ComboBox<String> shortlistDecision;
    @FXML private TextArea shortlistComment;
    @FXML private Button saveShortlistButton;
    @FXML private Button nav_PartnerInterview__2;

    private TeacherProfile currentTeacher;

    @FXML
    private void initialize() {
        String username = LoginController.getCurrentUsername();
        List<TeacherProfile> visible = STORE.visibleTeachers(username, "PARTNER");
        currentTeacher = visible.isEmpty() ? null : visible.get(0);

        if (shortlistDecision != null && shortlistDecision.getValue() == null && !shortlistDecision.getItems().isEmpty()) {
            shortlistDecision.getSelectionModel().selectFirst();
        }
        bindNavigation();
        bindDocumentButtons();
        if (saveShortlistButton != null) saveShortlistButton.setOnAction(e -> saveEvaluation());
        if (nav_PartnerInterview__2 != null) nav_PartnerInterview__2.setOnAction(e -> navigate("PartnerInterview.fxml", nav_PartnerInterview__2));
        refreshProfileText();
    }

    private void bindNavigation() {
        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> navigate("PartnerDashboard.fxml", nav_PartnerDashboard));
        if (nav_PartnerProfile != null) nav_PartnerProfile.setOnAction(e -> refreshProfileText());
        if (nav_PartnerInterview != null) nav_PartnerInterview.setOnAction(e -> navigate("PartnerInterview.fxml", nav_PartnerInterview));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void bindDocumentButtons() {
        if (playIntroButton != null) playIntroButton.setOnAction(e -> openTeacherUrl("getDemoUrl", "getIntroVideoUrl", "getVideoUrl"));
        if (partnerDemoButton != null) partnerDemoButton.setOnAction(e -> openTeacherUrl("getDemoUrl", "getIntroVideoUrl", "getVideoUrl"));
        if (partnerCvButton != null) partnerCvButton.setOnAction(e -> openTeacherUrl("getCvUrl", "getCVUrl", "getResumeUrl"));
        if (partnerCertButton != null) partnerCertButton.setOnAction(e -> openTeacherUrl("getCertificatesUrl", "getCertificateUrl", "getCvUrl", "getCVUrl"));
    }

    private void refreshProfileText() {
        if (nav_PartnerProfile == null) return;
        Parent root = nav_PartnerProfile.getScene() == null ? null : nav_PartnerProfile.getScene().getRoot();
        if (root == null) return;
        if (currentTeacher == null) {
            setLabelByExactText(root, "Emily Martin", "No shared teacher profile available");
            return;
        }
        String name = property(currentTeacher, "getName", "getFullName");
        setLabelByExactText(root, "Emily Martin", name);
        setLabelByExactText(root, "Emily Martin · Profile shared by Matching for Bright School.",
                name + " · Profile shared by Matching for " + safePartnerName() + ".");
        setLabelByExactText(root, "United Kingdom · 4 years · Primary · Hanoi",
                nonBlank(property(currentTeacher, "getNationality", "getCountry"), "Nationality not provided") + " · "
                        + nonBlank(property(currentTeacher, "getExperience", "getYearsExperience"), "Experience not provided") + " · "
                        + nonBlank(property(currentTeacher, "getCertificates", "getQualification"), "Certificates not provided") + " · "
                        + nonBlank(property(currentTeacher, "getLocation", "getCity"), "Location not provided"));
        setLabelByExactText(root, "Access Granted", STORE.canPartnerAccessTeacher(LoginController.getCurrentUsername(), currentTeacher.getId()) ? "Access Granted" : "Access Revoked");
        if (playIntroButton != null) playIntroButton.setDisable(firstNonBlank("getDemoUrl", "getIntroVideoUrl", "getVideoUrl").isBlank());
        if (partnerCvButton != null) partnerCvButton.setDisable(firstNonBlank("getCvUrl", "getCVUrl", "getResumeUrl").isBlank());
    }

    private void saveEvaluation() {
        if (currentTeacher == null) {
            showError("No profile", "There is no shared teacher profile available to evaluate.");
            return;
        }
        if (!STORE.canPartnerAccessTeacher(LoginController.getCurrentUsername(), currentTeacher.getId())) {
            showError("Access denied", "This partner account does not have access to the selected teacher profile.");
            return;
        }
        String decision = shortlistDecision == null || shortlistDecision.getValue() == null ? "Pending" : shortlistDecision.getValue();
        String status = switch (decision.trim().toLowerCase(Locale.ROOT)) {
            case "shortlist" -> "SHORTLISTED";
            case "unqualified" -> "UNQUALIFIED";
            default -> "PENDING";
        };
        STORE.status(currentTeacher.getId(), status);
        String comment = shortlistComment == null || shortlistComment.getText() == null ? "" : shortlistComment.getText().trim();
        STORE.log(LoginController.getCurrentUsername(), "Shortlist evaluation for " + property(currentTeacher, "getName", "getFullName")
                + ": " + decision + (comment.isBlank() ? "" : " — " + comment));
        if (saveShortlistButton != null) saveShortlistButton.setText("Evaluation saved");
        refreshProfileText();
    }

    private void openTeacherUrl(String... getters) {
        String url = firstNonBlank(getters);
        if (url.isBlank()) {
            showError("Document unavailable", "No URL is stored for this teacher resource in the current model.");
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop browsing is not supported on this device.");
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception ex) {
            showError("Could not open resource", "The teacher resource URL could not be opened: " + ex.getMessage());
        }
    }

    private String firstNonBlank(String... getters) {
        for (String getter : getters) {
            String value = property(currentTeacher, getter);
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }

    private String safePartnerName() {
        return STORE.users().stream()
                .filter(u -> u.getUsername().equals(LoginController.getCurrentUsername()))
                .map(u -> nonBlank(u.getFullName(), u.getUsername()))
                .findFirst().orElse("Partner");
    }

    private void setLabelByExactText(Parent root, String oldText, String newText) {
        for (Node node : root.lookupAll("Label")) {
            if (node instanceof Label label && oldText.equals(label.getText())) {
                label.setText(newText);
                return;
            }
        }
    }

    private String property(Object target, String... getters) {
        if (target == null) return "";
        for (String getter : getters) {
            try {
                Method method = target.getClass().getMethod(getter);
                Object value = method.invoke(target);
                if (value != null) return String.valueOf(value);
            } catch (ReflectiveOperationException ignored) { }
        }
        return "";
    }

    private String nonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private void navigate(String fxml, Node source) {
        if (!LoginController.navigateTo(source, fxml)) showError("Navigation failed", "Could not load " + fxml + ". Check the FXML resource path.");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
