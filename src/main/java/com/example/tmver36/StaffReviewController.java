package com.example.tmver36;

import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Review workflow for the currently selected teacher profile (StaffReview.fxml). */
public class StaffReviewController {

    private static final PortalStore STORE = LoginController.STORE;
    private static long selectedTeacherId = 1L;

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;

    @FXML private Button reviewCvButton;
    @FXML private Button reviewCertButton;
    @FXML private Button reviewIntroButton;
    @FXML private Button reviewDemoButton;
    @FXML private TextArea introductionArea;
    @FXML private TextArea commentsArea;
    @FXML private Button requestChangesButton;
    @FXML private Button readyStatusButton;

    private List<CheckBox> checklist = new ArrayList<>();
    private TeacherProfile currentTeacher;

    public static void setSelectedTeacherId(long teacherId) {
        selectedTeacherId = teacherId;
    }

    public static long getSelectedTeacherId() {
        return selectedTeacherId;
    }

    @FXML
    private void initialize() {
        currentTeacher = STORE.teacher(selectedTeacherId);
        if (currentTeacher == null && !STORE.teachers().isEmpty()) {
            currentTeacher = STORE.teachers().get(0);
            selectedTeacherId = currentTeacher.getId();
        }
        Parent root = rootNode();
        if (root != null) {
            discoverUnidentifiedControls(root);
        }
        bindNavigation();
        bindDocumentButtons();
        if (requestChangesButton != null) requestChangesButton.setOnAction(e -> requestChanges());
        if (readyStatusButton != null) readyStatusButton.setOnAction(e -> markReady());
        updateProfileHeader();
    }

    private Parent rootNode() {
        return FxNodes.rootOf(nav_StaffReview, nav_StaffDashboard, requestChangesButton, readyStatusButton);
    }

    private void discoverUnidentifiedControls(Parent root) {
        List<TextArea> areas = FxNodes.findAll(root, TextArea.class);
        if (introductionArea == null && !areas.isEmpty()) introductionArea = areas.get(0);
        if (commentsArea == null && areas.size() > 1) commentsArea = areas.get(1);
        checklist = FxNodes.findAll(root, CheckBox.class);
    }

    private void bindNavigation() {
        if (nav_StaffDashboard != null) nav_StaffDashboard.setOnAction(e -> navigate("StaffDashboard.fxml", nav_StaffDashboard));
        if (nav_StaffReview != null) nav_StaffReview.setOnAction(e -> updateProfileHeader());
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> navigate("InterviewWorkspace.fxml", nav_InterviewWorkspace));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void bindDocumentButtons() {
        if (reviewCvButton != null) reviewCvButton.setOnAction(e -> openUrl("CV", currentTeacher == null ? "" : currentTeacher.getCvUrl()));
        if (reviewCertButton != null) reviewCertButton.setOnAction(e -> openUrl("certificate", currentTeacher == null ? "" : currentTeacher.getCertificatesUrl()));
        if (reviewIntroButton != null) reviewIntroButton.setOnAction(e -> openUrl("introduction video", currentTeacher == null ? "" : currentTeacher.getIntroVideoUrl()));
        if (reviewDemoButton != null) reviewDemoButton.setOnAction(e -> openUrl("teaching demo", currentTeacher == null ? "" : currentTeacher.getDemoUrl()));
    }

    private void updateProfileHeader() {
        Parent root = rootNode();
        if (root == null || currentTeacher == null) return;
        List<Label> labels = FxNodes.findAll(root, Label.class);
        String name = safe(currentTeacher.getName());
        String summary = safe(currentTeacher.getNationality()) + " · "
                + safe(currentTeacher.getExperience()) + " · "
                + safe(currentTeacher.getCertificates()) + " · "
                + safe(currentTeacher.getLocation());
        for (Label label : labels) {
            if ("Emily Martin".equals(label.getText()) || "Daniel Walker".equals(label.getText()) || "Sarah Thompson".equals(label.getText())) {
                label.setText(name);
            } else if (label.getText() != null && label.getText().contains("United Kingdom · 4 years")) {
                label.setText(summary);
            }
        }
        if (introductionArea != null && !safe(currentTeacher.getBio()).isBlank()) {
            introductionArea.setText(currentTeacher.getBio());
        }
    }

    private void requestChanges() {
        if (currentTeacher == null) return;
        if (!STORE.status(currentActor(), currentTeacher.getId(), "NEED_UPDATE")) {
            showError("Update failed", "Your account is not allowed to update this profile status.");
            return;
        }
        String comments = commentsArea == null || commentsArea.getText() == null ? "" : commentsArea.getText().trim();
        STORE.log(currentActor(),
                "Requested profile update for " + safe(currentTeacher.getName())
                        + (comments.isBlank() ? "" : ": " + comments));
        showInfo("Profile update requested", "The teacher profile status was changed to NEED_UPDATE.");
    }

    private void markReady() {
        if (currentTeacher == null) return;
        List<String> incomplete = new ArrayList<>();
        for (CheckBox box : checklist) {
            if (!box.isSelected()) incomplete.add(box.getText());
        }
        if (!incomplete.isEmpty()) {
            showError("Review incomplete", "Complete the checklist before marking the profile ready:\n- " + String.join("\n- ", incomplete));
            return;
        }
        if (!STORE.status(currentActor(), currentTeacher.getId(), "READY")) {
            showError("Update failed", "Your account is not allowed to update this profile status.");
            return;
        }
        String intro = introductionArea == null || introductionArea.getText() == null ? "" : introductionArea.getText().trim();
        if (!intro.isBlank()) {
            currentTeacher.setBio(intro);
            STORE.saveTeacherProfile(currentActor(), currentTeacher);
        }
        STORE.log(currentActor(), "Marked profile ready for " + safe(currentTeacher.getName()));
        showInfo("Profile updated", "The profile status is now READY.");
    }

    private void openUrl(String label, String url) {
        if (url == null || url.isBlank()) {
            showError("Document unavailable", "No URL is stored for this teacher's " + label + ".");
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
            STORE.log(currentActor(), "Opened " + label + " for teacher #" + currentTeacher.getId());
        } catch (Exception ex) {
            showError("Could not open document", "Unable to open this link: " + ex.getMessage());
        }
    }

    private String currentActor() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "staff" : u;
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private void navigate(String fxml, Node source) {
        if (!LoginController.navigateTo(source, fxml)) {
            showError("Navigation failed", "Could not load " + fxml + ".");
        }
    }

    private void showInfo(String title, String message) {
        LoginController.showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    private void showError(String title, String message) {
        LoginController.showAlert(Alert.AlertType.ERROR, title, message);
    }
}
