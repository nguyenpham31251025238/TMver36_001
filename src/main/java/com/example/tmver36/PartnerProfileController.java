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

import java.awt.Desktop;
import java.net.URI;
import java.util.List;

/** Partner candidate profile screen backed by PortalStore (PartnerProfile.fxml). */
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
    @FXML private Label shortlistTitle;
    @FXML private ComboBox<String> shortlistDecision;
    @FXML private TextArea shortlistComment;
    @FXML private Button saveShortlistButton;
    @FXML private Button nav_PartnerInterview__2;

    private TeacherProfile currentTeacher;
    private static long selectedTeacherId = -1L;

    public static void setSelectedTeacherId(long teacherId) {
        selectedTeacherId = teacherId;
    }

    public static long getSelectedTeacherId() {
        return selectedTeacherId;
    }

    @FXML
    private void initialize() {
        String username = currentPartner();
        List<TeacherProfile> visible = STORE.visibleTeachers(username, "PARTNER");
        currentTeacher = visible.stream().filter(teacher -> teacher.getId() == selectedTeacherId).findFirst()
                .orElse(visible.isEmpty() ? null : visible.get(0));
        if (currentTeacher != null) {
            selectedTeacherId = currentTeacher.getId();
        }

        if (shortlistDecision != null && shortlistDecision.getValue() == null && !shortlistDecision.getItems().isEmpty()) {
            shortlistDecision.getSelectionModel().selectFirst();
        }
        if (currentTeacher != null) {
            String[] savedEval = STORE.partnerEvaluation(username, currentTeacher.getId());
            if (savedEval != null && savedEval.length >= 2) {
                if (shortlistDecision != null && !savedEval[0].isBlank()) {
                    shortlistDecision.setValue(savedEval[0]);
                }
                if (shortlistComment != null) {
                    shortlistComment.setText(savedEval[1]);
                }
            }
        }

        bindNavigation();
        bindDocumentButtons();
        if (saveShortlistButton != null) saveShortlistButton.setOnAction(e -> saveEvaluation());
        if (nav_PartnerInterview__2 != null) {
            nav_PartnerInterview__2.setOnAction(e -> {
                if (currentTeacher != null) {
                    PartnerInterviewController.setPreselectedTeacherId(currentTeacher.getId());
                }
                navigate("PartnerInterview.fxml", nav_PartnerInterview__2);
            });
        }
        refreshProfileText();
    }

    private String currentPartner() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "partner" : u;
    }

    private void bindNavigation() {
        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> navigate("PartnerDashboard.fxml", nav_PartnerDashboard));
        if (nav_PartnerProfile != null) nav_PartnerProfile.setOnAction(e -> refreshProfileText());
        if (nav_PartnerInterview != null) nav_PartnerInterview.setOnAction(e -> navigate("PartnerInterview.fxml", nav_PartnerInterview));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void bindDocumentButtons() {
        if (playIntroButton != null) playIntroButton.setOnAction(e -> openUrl(currentTeacher == null ? "" : currentTeacher.getIntroVideoUrl()));
        if (partnerDemoButton != null) partnerDemoButton.setOnAction(e -> openUrl(currentTeacher == null ? "" : currentTeacher.getDemoUrl()));
        if (partnerCvButton != null) partnerCvButton.setOnAction(e -> openUrl(currentTeacher == null ? "" : currentTeacher.getCvUrl()));
        if (partnerCertButton != null) partnerCertButton.setOnAction(e -> openUrl(currentTeacher == null ? "" : currentTeacher.getCertificatesUrl()));
    }

    private void refreshProfileText() {
        Parent root = FxNodes.rootOf(nav_PartnerProfile, saveShortlistButton, shortlistDecision);
        if (root == null) return;
        if (currentTeacher == null) {
            setLabelByExactText(root, "Emily Martin", "No shared teacher profile available");
            return;
        }
        String name = safe(currentTeacher.getName());
        setLabelByExactText(root, "Emily Martin", name);
        setLabelByExactText(root, "Emily Martin · Profile shared by Matching for Bright School.",
                name + " · Profile shared by Matching for " + safePartnerName() + ".");
        setLabelByExactText(root, "United Kingdom · 4 years · Primary · Hanoi",
                nonBlank(currentTeacher.getNationality(), "Nationality not provided") + " · "
                        + nonBlank(currentTeacher.getExperience(), "Experience not provided") + " · "
                        + nonBlank(currentTeacher.getCertificates(), "Certificates not provided") + " · "
                        + nonBlank(currentTeacher.getLocation(), "Location not provided"));
        setLabelByExactText(root, "Access Granted",
                STORE.canPartnerAccessTeacher(currentPartner(), currentTeacher.getId()) ? "Access Granted" : "Access Revoked");
    }

    private void saveEvaluation() {
        if (currentTeacher == null) {
            showError("No profile", "There is no shared teacher profile available to evaluate.");
            return;
        }
        String partner = currentPartner();
        if (!STORE.canPartnerAccessTeacher(partner, currentTeacher.getId())) {
            showError("Access denied", "This partner account does not have access to the selected teacher profile.");
            return;
        }
        String decision = shortlistDecision == null || shortlistDecision.getValue() == null ? "Shortlist" : shortlistDecision.getValue();
        String comment = shortlistComment == null || shortlistComment.getText() == null ? "" : shortlistComment.getText().trim();
        if (!STORE.savePartnerEvaluation(partner, currentTeacher.getId(), decision, comment)) {
            showError("Could not save evaluation", "The partner account no longer has access to this teacher profile.");
            return;
        }
        if (saveShortlistButton != null) saveShortlistButton.setText("Evaluation saved");
        refreshProfileText();
        LoginController.showAlert(Alert.AlertType.INFORMATION, "Evaluation Saved", "Saved shortlist evaluation for " + currentTeacher.getName() + ".");
    }

    private void openUrl(String url) {
        if (url == null || url.isBlank()) {
            showError("Document unavailable", "No URL is stored for this teacher resource.");
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception ex) {
            showError("Could not open resource", "The teacher resource URL could not be opened: " + ex.getMessage());
        }
    }

    private String safePartnerName() {
        return STORE.users().stream()
                .filter(u -> u.getUsername().equals(currentPartner()))
                .map(u -> nonBlank(u.getFullName(), u.getUsername()))
                .findFirst().orElse("Partner");
    }

    private void setLabelByExactText(Parent root, String oldText, String newText) {
        for (Label label : FxNodes.findAll(root, Label.class)) {
            if (oldText.equals(label.getText())) {
                label.setText(newText);
                return;
            }
        }
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void navigate(String fxml, Node source) {
        if (!LoginController.navigateTo(source, fxml)) {
            showError("Navigation failed", "Could not load " + fxml + ".");
        }
    }

    private void showError(String title, String message) {
        LoginController.showAlert(Alert.AlertType.ERROR, title, message);
    }
}
