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
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Review workflow for the currently selected teacher profile. */
public class StaffReviewController {
    private static final PortalStore STORE = LoginController.STORE;
    /** The dashboard currently opens this screen without passing a selected ID. */
    private static long selectedTeacherId = 1L;

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;
    @FXML private Button reviewCvButton;
    @FXML private Button reviewCertButton;
    @FXML private Button reviewIntroButton;
    @FXML private Button reviewDemoButton;
    @FXML private Button requestChangesButton;
    @FXML private Button readyStatusButton;

    private TextArea introductionArea;
    private TextArea commentsArea;
    private List<CheckBox> checklist = new ArrayList<>();
    private TeacherProfile currentTeacher;

    public static void setSelectedTeacherId(long teacherId) {
        selectedTeacherId = teacherId;
    }

    @FXML
    private void initialize() {
        currentTeacher = STORE.teacher(selectedTeacherId);
        Parent root = rootNode();
        if (root != null) discoverUnidentifiedControls(root);
        bindNavigation();
        bindDocumentButtons();
        if (requestChangesButton != null) requestChangesButton.setOnAction(e -> requestChanges());
        if (readyStatusButton != null) readyStatusButton.setOnAction(e -> markReady());
        updateProfileHeader();
    }

    private Parent rootNode() {
        Node source = nav_StaffDashboard != null ? nav_StaffDashboard : requestChangesButton;
        if (source == null) return null;
        Node n = source;
        while (n.getParent() != null) n = n.getParent();
        return n instanceof Parent p ? p : null;
    }

    private void discoverUnidentifiedControls(Parent root) {
        List<TextArea> areas = root.lookupAll("TextArea").stream()
                .filter(TextArea.class::isInstance).map(TextArea.class::cast).toList();
        // The first area is the profile introduction; the second is staff comments.
        if (!areas.isEmpty()) introductionArea = areas.get(0);
        if (areas.size() > 1) commentsArea = areas.get(1);
        checklist = root.lookupAll("CheckBox").stream()
                .filter(CheckBox.class::isInstance).map(CheckBox.class::cast).toList();
    }

    private void bindNavigation() {
        if (nav_StaffDashboard != null) nav_StaffDashboard.setOnAction(e -> navigate("StaffDashboard.fxml", nav_StaffDashboard));
        if (nav_StaffReview != null) nav_StaffReview.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview));
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> navigate("InterviewWorkspace.fxml", nav_InterviewWorkspace));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void bindDocumentButtons() {
        if (reviewCvButton != null) reviewCvButton.setOnAction(e -> openTeacherUrl("CV", "getCvUrl", "getCVUrl", "getResumeUrl"));
        if (reviewCertButton != null) reviewCertButton.setOnAction(e -> openTeacherUrl("certificate", "getCertificatesUrl", "getCertificateUrl"));
        if (reviewIntroButton != null) reviewIntroButton.setOnAction(e -> openTeacherUrl("introduction video", "getIntroVideoUrl", "getDemoUrl", "getVideoUrl"));
        if (reviewDemoButton != null) reviewDemoButton.setOnAction(e -> openTeacherUrl("teaching demo", "getDemoUrl", "getTeachingVideoUrl", "getVideoUrl"));
    }

    private void updateProfileHeader() {
        Parent root = rootNode();
        if (root == null || currentTeacher == null) return;
        List<Label> labels = root.lookupAll("Label").stream().filter(Label.class::isInstance).map(Label.class::cast).toList();
        String name = read(currentTeacher, "getName", "getFullName");
        String summary = read(currentTeacher, "getNationality", "getCountry") + " · "
                + read(currentTeacher, "getExperience", "getYearsExperience") + " · "
                + read(currentTeacher, "getCertificates", "getQualification") + " · "
                + read(currentTeacher, "getLocation", "getCity");
        for (Label label : labels) {
            if ("Emily Martin".equals(label.getText()) || "Daniel Walker".equals(label.getText()) || "Sarah Thompson".equals(label.getText())) {
                label.setText(name);
            } else if (label.getText() != null && label.getText().contains("United Kingdom · 4 years")) {
                label.setText(summary);
            }
        }
    }

    private void requestChanges() {
        if (currentTeacher == null) return;
        STORE.status(currentTeacher.getId(), "UPDATE_REQUIRED");
        String comments = commentsArea == null ? "" : commentsArea.getText().trim();
        STORE.log(currentActor("staff"),
                "Requested profile update for " + read(currentTeacher, "getName", "getFullName")
                        + (comments.isBlank() ? "" : ": " + comments));
        showInfo("Profile update requested", "The teacher profile status was changed to UPDATE_REQUIRED.");
    }

    private void markReady() {
        if (currentTeacher == null) return;
        List<String> incomplete = new ArrayList<>();
        for (CheckBox box : checklist) if (!box.isSelected()) incomplete.add(box.getText());
        if (!incomplete.isEmpty()) {
            showError("Review incomplete", "Complete the checklist before marking the profile ready:\n• " + String.join("\n• ", incomplete));
            return;
        }
        STORE.status(currentTeacher.getId(), "READY");
        String intro = introductionArea == null ? "" : introductionArea.getText().trim();
        STORE.log(currentActor("staff"),
                "Marked profile ready for " + read(currentTeacher, "getName", "getFullName")
                        + (intro.isBlank() ? "" : "; introduction reviewed"));
        showInfo("Profile updated", "The profile status is now READY.");
    }

    private void openTeacherUrl(String label, String... getterNames) {
        if (currentTeacher == null) return;
        String url = read(currentTeacher, getterNames);
        if (url.isBlank()) {
            showError("Document unavailable", "No URL is stored for this teacher's " + label + ".");
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop browsing is not supported on this device.");
            Desktop.getDesktop().browse(URI.create(url));
            STORE.log(currentActor("staff"), "Opened " + label + " for teacher #" + currentTeacher.getId());
        } catch (Exception ex) {
            showError("Could not open document", "Unable to open this link: " + ex.getMessage());
        }
    }

    private String read(Object target, String... methodNames) {
        if (target == null) return "";
        for (String methodName : methodNames) {
            try {
                Method method = target.getClass().getMethod(methodName);
                Object value = method.invoke(target);
                if (value != null && !value.toString().isBlank()) return value.toString();
            } catch (ReflectiveOperationException ignored) { }
        }
        return "";
    }

    private String currentActor(String fallback) {
        String username = LoginController.getCurrentUsername();
        return username == null || username.isBlank() ? fallback : username;
    }

    private void navigate(String fxml, Node source) {
        if (!LoginController.navigateTo(source, fxml)) showError("Navigation failed", "Could not load " + fxml + ".");
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }
}
