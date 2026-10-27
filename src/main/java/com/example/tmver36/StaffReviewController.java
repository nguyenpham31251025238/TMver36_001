package com.example.tmver36;

import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

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

    @FXML private ComboBox<String> teacherSelector;
    @FXML private StackPane avatarContainer;
    @FXML private Label teacherNameLabel;
    @FXML private Label teacherSummaryLabel;
    @FXML private Label teacherMetaLabel;
    @FXML private Label statusBadgeLabel;
    @FXML private Label checklistProgressLabel;
    @FXML private Label reviewFeedbackBanner;

    @FXML private Button reviewCvButton;
    @FXML private Button reviewCertButton;
    @FXML private Button reviewIntroButton;
    @FXML private Button reviewDemoButton;
    @FXML private VBox documentsTable;
    @FXML private TextArea introductionArea;
    @FXML private TextArea commentsArea;
    @FXML private CheckBox checkPersonalInfo;
    @FXML private CheckBox checkReadableCv;
    @FXML private CheckBox checkCertificates;
    @FXML private CheckBox checkVideos;
    @FXML private CheckBox checkScheduleSlots;
    @FXML private Button requestChangesButton;
    @FXML private Button markQcvButton;
    @FXML private Button readyStatusButton;

    private List<CheckBox> checklist = new ArrayList<>();
    private TeacherProfile currentTeacher;
    private boolean loadingChecklist;

    public static void setSelectedTeacherId(long teacherId) {
        selectedTeacherId = teacherId;
    }

    public static long getSelectedTeacherId() {
        return selectedTeacherId;
    }

    @FXML
    private void initialize() {
        loadCurrentTeacher();
        Parent root = rootNode();
        if (root != null) {
            discoverUnidentifiedControls(root);
        }
        configureTeacherSelector();
        bindNavigation();
        bindDocumentButtons();
        ProfileCardBuilder.decorateScreen(root, STORE, () -> {
            configureTeacherSelector();
            refreshProfileView();
        });
        bindChecklistListeners();
        if (requestChangesButton != null) requestChangesButton.setOnAction(e -> requestChanges());
        if (markQcvButton != null) markQcvButton.setOnAction(e -> markPendingQcv());
        if (readyStatusButton != null) readyStatusButton.setOnAction(e -> markReady());
        refreshProfileView();
    }

    private void loadCurrentTeacher() {
        currentTeacher = STORE.teacher(selectedTeacherId);
        if (currentTeacher == null && !STORE.teachers().isEmpty()) {
            currentTeacher = STORE.teachers().get(0);
            selectedTeacherId = currentTeacher.getId();
        }
    }

    private Parent rootNode() {
        return FxNodes.rootOf(nav_StaffReview, nav_StaffDashboard, requestChangesButton, readyStatusButton);
    }

    private void discoverUnidentifiedControls(Parent root) {
        List<TextArea> areas = FxNodes.findAll(root, TextArea.class);
        if (introductionArea == null && !areas.isEmpty()) introductionArea = areas.get(0);
        if (commentsArea == null && areas.size() > 1) commentsArea = areas.get(1);
        if (checkPersonalInfo != null && checkReadableCv != null && checkCertificates != null
                && checkVideos != null && checkScheduleSlots != null) {
            checklist = List.of(checkPersonalInfo, checkReadableCv, checkCertificates, checkVideos, checkScheduleSlots);
        } else {
            checklist = FxNodes.findAll(root, CheckBox.class);
        }
    }

    private void configureTeacherSelector() {
        if (teacherSelector == null) return;
        List<TeacherProfile> teachers = STORE.teachers();
        List<String> items = new ArrayList<>();
        String selectedItem = null;
        for (TeacherProfile t : teachers) {
            String label = "#" + t.getId() + " · " + safe(t.getName()) + " (" + CatalogData.formatShortStatusCode(t.getStatus()) + ")";
            items.add(label);
            if (t.getId() == selectedTeacherId) {
                selectedItem = label;
            }
        }
        teacherSelector.getItems().setAll(items);
        if (selectedItem != null) {
            teacherSelector.setValue(selectedItem);
        }
        teacherSelector.setOnAction(e -> {
            String val = teacherSelector.getValue();
            if (val != null && val.startsWith("#")) {
                int dot = val.indexOf('·');
                if (dot > 1) {
                    try {
                        long id = Long.parseLong(val.substring(1, dot).trim());
                        selectedTeacherId = id;
                        loadCurrentTeacher();
                        refreshProfileView();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });
    }

    private void bindChecklistListeners() {
        for (CheckBox box : checklist) {
            box.selectedProperty().addListener((obs, oldV, newV) -> {
                if (loadingChecklist || currentTeacher == null) return;
                persistChecklistOnly();
                updateChecklistProgressLabel();
            });
        }
    }

    private void persistChecklistOnly() {
        if (currentTeacher == null) return;
        boolean[] flags = currentChecklistFlags();
        String feedback = commentsArea == null || commentsArea.getText() == null ? currentTeacher.getStaffFeedback() : commentsArea.getText().trim();
        String intro = introductionArea == null || introductionArea.getText() == null ? currentTeacher.getBio() : introductionArea.getText().trim();
        STORE.saveReviewState(currentActor(), currentTeacher.getId(), currentTeacher.getStatus(), feedback, flags, intro);
        currentTeacher = STORE.teacher(currentTeacher.getId());
    }

    private boolean[] currentChecklistFlags() {
        boolean[] flags = new boolean[5];
        for (int i = 0; i < Math.min(5, checklist.size()); i++) {
            flags[i] = checklist.get(i).isSelected();
        }
        return flags;
    }

    private void updateChecklistProgressLabel() {
        if (checklistProgressLabel == null) return;
        int checked = 0;
        for (CheckBox box : checklist) {
            if (box.isSelected()) checked++;
        }
        checklistProgressLabel.setText(checked + "/" + checklist.size() + " verified");
    }

    private void bindNavigation() {
        if (nav_StaffDashboard != null) nav_StaffDashboard.setOnAction(e -> navigate("StaffDashboard.fxml", nav_StaffDashboard));
        if (nav_StaffReview != null) nav_StaffReview.setOnAction(e -> refreshProfileView());
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> navigate("InterviewWorkspace.fxml", nav_InterviewWorkspace));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void bindDocumentButtons() {
        if (reviewCvButton != null) reviewCvButton.setOnAction(e -> openCategoryDocument("CV"));
        if (reviewCertButton != null) reviewCertButton.setOnAction(e -> openCategoryDocument("Certificate"));
        if (reviewIntroButton != null) reviewIntroButton.setOnAction(e -> openCategoryDocument("Intro video"));
        if (reviewDemoButton != null) reviewDemoButton.setOnAction(e -> openCategoryDocument("Teaching video"));
    }

    private void openCategoryDocument(String category) {
        if (currentTeacher == null) return;
        TeacherDocument doc = ProfileCardBuilder.findDocumentByCategory(STORE, currentTeacher.getId(), category);
        if (ProfileCardBuilder.openDocumentWithAuthAndPlayback(STORE, currentActor(), doc, documentsTable)) {
            return;
        }
        if (doc == null) {
            showError("Document unavailable", "No " + category + " document is currently uploaded for " + safe(currentTeacher.getName()) + ".");
        }
    }

    private void refreshProfileView() {
        loadCurrentTeacher();
        if (currentTeacher == null) return;

        if (avatarContainer != null) {
            avatarContainer.getChildren().setAll(ProfileCardBuilder.createAvatarNode(currentTeacher, 60));
        }
        if (teacherNameLabel != null) {
            teacherNameLabel.setText(safe(currentTeacher.getName()));
        }
        String degreeMajor = safe(currentTeacher.getDegree());
        if (!safe(currentTeacher.getMajor()).isBlank()) {
            degreeMajor = degreeMajor.isBlank() ? safe(currentTeacher.getMajor()) : degreeMajor + " in " + safe(currentTeacher.getMajor());
        }
        if (teacherSummaryLabel != null) {
            teacherSummaryLabel.setText(
                    safe(currentTeacher.getNationality()) + " · "
                            + safe(currentTeacher.getExperience()) + " · "
                            + safe(currentTeacher.getCertificates())
                            + (degreeMajor.isBlank() ? "" : " · " + degreeMajor)
            );
        }
        if (teacherMetaLabel != null) {
            String salary = CatalogData.formatSalary(currentTeacher.getExpectedSalary());
            String availableFrom = safe(currentTeacher.getAvailableFrom()).isBlank() ? "Immediate" : safe(currentTeacher.getAvailableFrom());
            String updatedAt = safe(currentTeacher.getUpdatedAt()).isBlank() ? "UTC+7" : safe(currentTeacher.getUpdatedAt());
            teacherMetaLabel.setText("Expected Salary: " + salary + " / month · Available: " + availableFrom
                    + " · Location: " + safe(currentTeacher.getLocation()) + " · Updated: " + updatedAt);
        }
        if (statusBadgeLabel != null) {
            statusBadgeLabel.setText(CatalogData.formatStatusBadge(currentTeacher.getStatus()));
            statusBadgeLabel.getStyleClass().setAll("badge", CatalogData.badgeStyleForStatus(currentTeacher.getStatus()));
        }
        if (introductionArea != null) {
            introductionArea.setText(safe(currentTeacher.getBio()));
        }
        if (commentsArea != null) {
            commentsArea.setText(safe(currentTeacher.getStaffFeedback()));
        }

        loadingChecklist = true;
        try {
            boolean[] flags = currentTeacher.getChecklistFlags();
            for (int i = 0; i < Math.min(flags.length, checklist.size()); i++) {
                checklist.get(i).setSelected(flags[i]);
            }
        } finally {
            loadingChecklist = false;
        }
        updateChecklistProgressLabel();
        renderDocumentsTable();
    }

    private void renderDocumentsTable() {
        if (documentsTable == null || currentTeacher == null) return;
        Node header = documentsTable.getChildren().stream()
                .filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header"))
                .findFirst().orElse(null);
        documentsTable.getChildren().clear();
        if (header != null) {
            documentsTable.getChildren().add(header);
        }

        List<TeacherDocument> docs = STORE.documents(currentTeacher.getId());
        for (TeacherDocument doc : docs) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");

            Label docTitle = new Label(safe(doc.getCategory()) + " · " + safe(doc.getFileName()));
            docTitle.getStyleClass().add("table-text");
            docTitle.setWrapText(true);
            VBox colDoc = new VBox(docTitle);
            colDoc.setAlignment(Pos.CENTER_LEFT);
            colDoc.setMinWidth(220);
            colDoc.setPrefWidth(280);
            HBox.setHgrow(colDoc, Priority.ALWAYS);

            Label updatedLbl = new Label(safe(doc.getUpdatedAt()));
            updatedLbl.getStyleClass().add("muted");
            VBox colUpdated = new VBox(updatedLbl);
            colUpdated.setAlignment(Pos.CENTER_LEFT);
            colUpdated.setMinWidth(120);
            colUpdated.setPrefWidth(140);

            Button openBtn = new Button(doc.getCategory().toLowerCase().contains("video") ? "Watch" : "Open");
            openBtn.getStyleClass().addAll("button-sm", "secondary");
            openBtn.setOnAction(e -> ProfileCardBuilder.openDocumentWithAuthAndPlayback(STORE, currentActor(), doc, openBtn));

            Button downloadBtn = new Button("Download");
            downloadBtn.getStyleClass().addAll("button-sm", "secondary");
            downloadBtn.setOnAction(e -> ProfileCardBuilder.downloadDocumentToDisk(STORE, currentActor(), doc, downloadBtn));

            HBox actions = new HBox(8, openBtn, downloadBtn);
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.setMinWidth(180);
            actions.setPrefWidth(200);

            row.getChildren().addAll(colDoc, colUpdated, actions);
            documentsTable.getChildren().add(row);
        }

        if (docs.isEmpty()) {
            HBox empty = new HBox(new Label("No documents uploaded yet for " + safe(currentTeacher.getName()) + "."));
            empty.getStyleClass().add("table-row");
            documentsTable.getChildren().add(empty);
        }
    }

    private void requestChanges() {
        if (currentTeacher == null) return;
        String comments = commentsArea == null || commentsArea.getText() == null ? "" : commentsArea.getText().trim();
        if (comments.isBlank()) {
            comments = "Please update your profile details and supporting documents.";
            if (commentsArea != null) {
                commentsArea.setText(comments);
            }
        }
        String intro = introductionArea == null || introductionArea.getText() == null ? currentTeacher.getBio() : introductionArea.getText().trim();
        if (!STORE.saveReviewState(currentActor(), currentTeacher.getId(), "UPDATE_REQUIRED", comments, currentChecklistFlags(), intro)) {
            showError("Update failed", "Your account is not allowed to update this profile status.");
            return;
        }
        showBanner("Update requested! Feedback saved and sent to " + safe(currentTeacher.getName()) + ".");
        refreshProfileView();
        configureTeacherSelector();
        showInfo("Profile update requested", "The teacher profile status was changed to Update Required and feedback was saved for the teacher.");
    }

    private void markPendingQcv() {
        if (currentTeacher == null) return;
        String comments = commentsArea == null || commentsArea.getText() == null ? "" : commentsArea.getText().trim();
        String intro = introductionArea == null || introductionArea.getText() == null ? currentTeacher.getBio() : introductionArea.getText().trim();
        if (!STORE.saveReviewState(currentActor(), currentTeacher.getId(), "PENDING", comments, currentChecklistFlags(), intro)) {
            showError("Update failed", "Your account is not allowed to update this profile status.");
            return;
        }
        showBanner("Checklist and qCV status saved for " + safe(currentTeacher.getName()) + ".");
        refreshProfileView();
        configureTeacherSelector();
        showInfo("Saved as qCV", "Checklist progress and feedback have been saved under status qCV (Pending).");
    }

    private void markReady() {
        if (currentTeacher == null) return;
        for (CheckBox box : checklist) {
            if (box != null) {
                box.setSelected(true);
            }
        }
        String comments = commentsArea == null || commentsArea.getText() == null ? "" : commentsArea.getText().trim();
        String intro = introductionArea == null || introductionArea.getText() == null ? currentTeacher.getBio() : introductionArea.getText().trim();
        if (!STORE.saveReviewState(currentActor(), currentTeacher.getId(), "READY", comments, currentChecklistFlags(), intro)) {
            showError("Update failed", "Your account is not allowed to update this profile status.");
            return;
        }
        showBanner("Profile marked as qEP (Ready) for partner matching.");
        refreshProfileView();
        configureTeacherSelector();
        showInfo("Profile updated", "The profile status is now qEP (Ready).");
    }

    private void showBanner(String message) {
        if (reviewFeedbackBanner == null) return;
        reviewFeedbackBanner.setText(message);
        reviewFeedbackBanner.setManaged(true);
        reviewFeedbackBanner.setVisible(true);
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
