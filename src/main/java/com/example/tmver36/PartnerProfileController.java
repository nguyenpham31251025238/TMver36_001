package com.example.tmver36;

import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Partner candidate profile screen backed by PortalStore (PartnerProfile.fxml). */
public class PartnerProfileController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_PartnerDashboard;
    @FXML private Button nav_PartnerProfile;
    @FXML private Button nav_PartnerInterview;
    @FXML private Button nav_Login;

    @FXML private ComboBox<String> candidateSelector;
    @FXML private StackPane avatarContainer;
    @FXML private Label pageSubtitleLabel;
    @FXML private Label candidateNameLabel;
    @FXML private Label candidateSummaryLabel;
    @FXML private Label candidateSalaryLabel;
    @FXML private Label accessBadgeLabel;
    @FXML private Label videoInitialsLabel;
    @FXML private Label candidateBioLabel;
    @FXML private VBox partnerDocsTable;

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
        loadSelectedCandidate();
        configureCandidateSelector();
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

    private void loadSelectedCandidate() {
        String username = currentPartner();
        List<TeacherProfile> visible = STORE.visibleTeachers(username, "PARTNER");
        currentTeacher = visible.stream().filter(teacher -> teacher.getId() == selectedTeacherId).findFirst()
                .orElse(visible.isEmpty() ? null : visible.get(0));
        if (currentTeacher != null) {
            selectedTeacherId = currentTeacher.getId();
        }
    }

    private void configureCandidateSelector() {
        if (candidateSelector == null) return;
        List<TeacherProfile> visible = STORE.visibleTeachers(currentPartner(), "PARTNER");
        List<String> items = new ArrayList<>();
        String currentLabel = null;
        for (TeacherProfile t : visible) {
            String item = "#" + t.getId() + " · " + safe(t.getName());
            items.add(item);
            if (currentTeacher != null && t.getId() == currentTeacher.getId()) {
                currentLabel = item;
            }
        }
        candidateSelector.getItems().setAll(items);
        if (currentLabel != null) {
            candidateSelector.setValue(currentLabel);
        }
        candidateSelector.setOnAction(e -> {
            String val = candidateSelector.getValue();
            if (val != null && val.startsWith("#")) {
                int dot = val.indexOf('·');
                if (dot > 1) {
                    try {
                        selectedTeacherId = Long.parseLong(val.substring(1, dot).trim());
                        loadSelectedCandidate();
                        refreshProfileText();
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        });
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
        if (playIntroButton != null) playIntroButton.setOnAction(e -> openCategoryDoc("Intro video"));
        if (partnerDemoButton != null) partnerDemoButton.setOnAction(e -> openCategoryDoc("Teaching video"));
        if (partnerCvButton != null) partnerCvButton.setOnAction(e -> openCategoryDoc("CV"));
        if (partnerCertButton != null) partnerCertButton.setOnAction(e -> openCategoryDoc("Certificate"));
    }

    private void openCategoryDoc(String category) {
        if (currentTeacher == null) return;
        TeacherDocument doc = ProfileCardBuilder.findDocumentByCategory(STORE, currentTeacher.getId(), category);
        if (doc != null && STORE.openDocument(currentPartner(), doc)) {
            return;
        }
        showError("Document unavailable", "No " + category + " file is available for " + safe(currentTeacher.getName()) + ".");
    }

    private void refreshProfileText() {
        if (currentTeacher == null) {
            if (candidateNameLabel != null) candidateNameLabel.setText("No shared teacher profile available");
            return;
        }
        String username = currentPartner();
        String name = safe(currentTeacher.getName());
        if (avatarContainer != null) {
            avatarContainer.getChildren().setAll(ProfileCardBuilder.createAvatarNode(currentTeacher, 60));
        }
        if (videoInitialsLabel != null) {
            videoInitialsLabel.setText(ProfileCardBuilder.initialsOf(name));
        }
        if (candidateNameLabel != null) {
            candidateNameLabel.setText(name);
        }
        if (pageSubtitleLabel != null) {
            pageSubtitleLabel.setText(name + " · Profile shared by Matching for " + safePartnerName() + ".");
        }
        String degMaj = nonBlank(currentTeacher.getDegree(), "Bachelor's Degree") + " in "
                + nonBlank(currentTeacher.getMajor(), nonBlank(currentTeacher.getSubject(), "English Education"));
        if (candidateSummaryLabel != null) {
            candidateSummaryLabel.setText(
                    nonBlank(currentTeacher.getNationality(), "International") + " · "
                            + nonBlank(currentTeacher.getExperience(), "1–2 years") + " · "
                            + nonBlank(currentTeacher.getCertificates(), "TESOL") + " · "
                            + degMaj
            );
        }
        if (candidateSalaryLabel != null) {
            candidateSalaryLabel.setText(
                    "Expected Salary: " + nonBlank(currentTeacher.getExpectedSalary(), "$1,500 - $2,000")
                            + " / month · Available: " + nonBlank(currentTeacher.getAvailableFrom(), "Immediate")
                            + " · Location: " + nonBlank(currentTeacher.getLocation(), "Vietnam")
            );
        }
        if (accessBadgeLabel != null) {
            boolean granted = STORE.canPartnerAccessTeacher(username, currentTeacher.getId());
            accessBadgeLabel.setText(granted ? "Access Granted (qEP)" : "Access Revoked");
        }
        if (candidateBioLabel != null) {
            candidateBioLabel.setText(nonBlank(currentTeacher.getBio(), "Verified candidate profile."));
        }

        String[] savedEval = STORE.partnerEvaluation(username, currentTeacher.getId());
        if (savedEval != null && savedEval.length >= 2) {
            if (shortlistDecision != null && !savedEval[0].isBlank()) {
                shortlistDecision.setValue(savedEval[0]);
            }
            if (shortlistComment != null) {
                shortlistComment.setText(savedEval[1]);
            }
        } else {
            if (shortlistDecision != null) shortlistDecision.setValue("Pending");
            if (shortlistComment != null) shortlistComment.clear();
        }

        renderDocsTable();
    }

    private void renderDocsTable() {
        if (partnerDocsTable == null || currentTeacher == null) return;
        Node header = partnerDocsTable.getChildren().stream()
                .filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header"))
                .findFirst().orElse(null);
        partnerDocsTable.getChildren().clear();
        if (header != null) partnerDocsTable.getChildren().add(header);

        List<TeacherDocument> docs = STORE.documents(currentTeacher.getId());
        for (TeacherDocument doc : docs) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");

            Label lbl = new Label(safe(doc.getCategory()) + " · " + safe(doc.getFileName()));
            lbl.getStyleClass().add("table-text");
            lbl.setWrapText(true);
            VBox colDoc = new VBox(lbl);
            colDoc.setAlignment(Pos.CENTER_LEFT);
            colDoc.setMinWidth(200);
            colDoc.setPrefWidth(250);
            HBox.setHgrow(colDoc, Priority.ALWAYS);

            Button openBtn = new Button(doc.getCategory().toLowerCase().contains("video") ? "Watch" : "Open");
            openBtn.getStyleClass().addAll("button-sm", "secondary");
            openBtn.setOnAction(e -> STORE.openDocument(currentPartner(), doc));

            Button dlBtn = new Button("Download");
            dlBtn.getStyleClass().addAll("button-sm", "secondary");
            dlBtn.setOnAction(e -> ProfileCardBuilder.downloadDocumentToDisk(STORE, currentPartner(), doc, dlBtn));

            HBox actions = new HBox(8, openBtn, dlBtn);
            actions.setAlignment(Pos.CENTER_LEFT);
            actions.setMinWidth(160);
            actions.setPrefWidth(180);

            row.getChildren().addAll(colDoc, actions);
            partnerDocsTable.getChildren().add(row);
        }
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

    private String safePartnerName() {
        return STORE.users().stream()
                .filter(u -> u.getUsername().equals(currentPartner()))
                .map(u -> nonBlank(u.getFullName(), u.getUsername()))
                .findFirst().orElse("Partner");
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
