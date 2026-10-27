package com.example.tmver36;

import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** JavaFX controller for editing the current teacher profile (TeacherProfile.fxml). */
public class TeacherProfileController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_TeacherProfile;
    @FXML private Button nav_TeacherDocuments;
    @FXML private Button nav_TeacherSchedule;
    @FXML private Button nav_TeacherHistory;
    @FXML private Button nav_Login;

    @FXML private Label sidebarAvatarInitials;
    @FXML private Label sidebarTeacherName;
    @FXML private Label topAvatarInitials;
    @FXML private StackPane avatarContainer;
    @FXML private Label headerNameLabel;
    @FXML private Label headerSubtitleLabel;
    @FXML private Button uploadAvatarButton;
    @FXML private Label statusBadgeLabel;
    @FXML private Label profileNoteLabel;
    @FXML private Label staffFeedbackBanner;

    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> nationalityCombo;
    @FXML private TextField phoneField;
    @FXML private ComboBox<String> degreeCombo;
    @FXML private ComboBox<String> majorCombo;
    @FXML private ComboBox<String> experienceTierCombo;
    @FXML private TextField experienceCombo; // legacy compatible field
    @FXML private MenuButton certificatesMenuButton;
    @FXML private ComboBox<String> expectedSalaryCombo;
    @FXML private ComboBox<String> aboutArea;
    @FXML private TextField locationCombo;
    @FXML private TextField dateSelect;
    @FXML private TextArea bioArea;
    @FXML private Button saveDraftButton;
    @FXML private Button submitProfileButton;

    private TeacherProfile profile;
    private final Set<String> selectedCertificates = new LinkedHashSet<>();
    private final List<CheckMenuItem> certItems = new ArrayList<>();
    private boolean updatingCountryFilter;

    @FXML
    private void initialize() {
        configureCatalogControls();
        wireNavigation();
        loadProfileIntoForm();
        Parent root = sourceRoot();
        ProfileCardBuilder.decorateScreen(root, STORE, this::loadProfileIntoForm);
        if (uploadAvatarButton != null) uploadAvatarButton.setOnAction(e -> chooseAndUploadAvatar());
        if (saveDraftButton != null) saveDraftButton.setOnAction(e -> saveDraft(true));
        if (submitProfileButton != null) submitProfileButton.setOnAction(e -> submitForReview());
    }

    private void configureCatalogControls() {
        if (nationalityCombo != null) {
            nationalityCombo.getItems().setAll(CatalogData.WORLD_COUNTRIES_AZ);
            nationalityCombo.setEditable(true);
            if (nationalityCombo.getEditor() != null) {
                nationalityCombo.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                    if (updatingCountryFilter) return;
                    updatingCountryFilter = true;
                    try {
                        List<String> filtered = CatalogData.filterCountries(newText, false);
                        nationalityCombo.getItems().setAll(filtered);
                        nationalityCombo.getEditor().setText(newText);
                    } finally {
                        updatingCountryFilter = false;
                    }
                });
            }
        }
        if (degreeCombo != null) {
            degreeCombo.getItems().setAll(CatalogData.DEGREE_OPTIONS);
        }
        if (majorCombo != null) {
            majorCombo.getItems().setAll(CatalogData.MAJOR_OPTIONS);
            majorCombo.setEditable(true);
        }
        if (experienceTierCombo != null) {
            experienceTierCombo.getItems().setAll(CatalogData.EXPERIENCE_OPTIONS);
            experienceTierCombo.setOnAction(e -> {
                if (experienceCombo != null && experienceTierCombo.getValue() != null) {
                    experienceCombo.setText(experienceTierCombo.getValue());
                }
            });
        }
        if (expectedSalaryCombo != null) {
            expectedSalaryCombo.getItems().setAll(CatalogData.EXPECTED_SALARY_OPTIONS);
            expectedSalaryCombo.setEditable(true);
        }
        if (certificatesMenuButton != null) {
            certificatesMenuButton.getItems().clear();
            certItems.clear();
            for (String cert : CatalogData.CERTIFICATE_OPTIONS) {
                CheckMenuItem item = new CheckMenuItem(cert);
                item.selectedProperty().addListener((obs, oldV, isSelected) -> {
                    if (isSelected) selectedCertificates.add(cert);
                    else selectedCertificates.remove(cert);
                    updateCertificateButtonText();
                });
                certItems.add(item);
                certificatesMenuButton.getItems().add(item);
            }
        }
    }

    private void updateCertificateButtonText() {
        if (certificatesMenuButton == null) return;
        if (selectedCertificates.isEmpty()) {
            certificatesMenuButton.setText("Select certificates");
        } else {
            certificatesMenuButton.setText(CatalogData.joinCertificates(selectedCertificates));
        }
    }

    private void wireNavigation() {
        if (nav_TeacherProfile != null) nav_TeacherProfile.setOnAction(e -> loadProfileIntoForm());
        if (nav_TeacherDocuments != null) nav_TeacherDocuments.setOnAction(e -> navigate("TeacherDocuments.fxml", nav_TeacherDocuments));
        if (nav_TeacherSchedule != null) nav_TeacherSchedule.setOnAction(e -> navigate("TeacherSchedule.fxml", nav_TeacherSchedule));
        if (nav_TeacherHistory != null) nav_TeacherHistory.setOnAction(e -> navigate("TeacherHistory.fxml", nav_TeacherHistory));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private TeacherProfile resolveCurrentProfile() {
        String username = currentActor();
        TeacherProfile linked = STORE.teacherForUser(username);
        return linked != null ? linked : STORE.teacher(1L);
    }

    private void loadProfileIntoForm() {
        profile = resolveCurrentProfile();
        if (profile == null) {
            setFeedback("No teacher profile is linked to this account.");
            return;
        }
        if (fullNameField != null) fullNameField.setText(safe(profile.getName()));
        if (emailField != null) emailField.setText(safe(profile.getEmail()));
        if (nationalityCombo != null) {
            updatingCountryFilter = true;
            try {
                nationalityCombo.getItems().setAll(CatalogData.WORLD_COUNTRIES_AZ);
                selectComboValue(nationalityCombo, nonBlank(profile.getNationality(), "United Kingdom"));
                if (nationalityCombo.getEditor() != null) {
                    nationalityCombo.getEditor().setText(nonBlank(profile.getNationality(), "United Kingdom"));
                }
            } finally {
                updatingCountryFilter = false;
            }
        }
        if (phoneField != null) phoneField.setText(safe(profile.getPhone()));
        if (degreeCombo != null) selectComboValue(degreeCombo, nonBlank(profile.getDegree(), "Bachelor's Degree"));
        if (majorCombo != null) selectComboValue(majorCombo, nonBlank(profile.getMajor(), nonBlank(profile.getSubject(), "English Education")));

        String normalizedExp = CatalogData.normalizeExperienceBand(profile.getExperience());
        if (experienceTierCombo != null) selectComboValue(experienceTierCombo, normalizedExp);
        if (experienceCombo != null) experienceCombo.setText(normalizedExp);

        selectedCertificates.clear();
        selectedCertificates.addAll(CatalogData.parseCertificates(profile.getCertificates()));
        for (CheckMenuItem item : certItems) {
            item.setSelected(selectedCertificates.contains(item.getText()));
        }
        updateCertificateButtonText();

        if (expectedSalaryCombo != null) {
            selectComboValue(expectedSalaryCombo, nonBlank(profile.getExpectedSalary(), "$1,500 - $2,000"));
        }
        if (aboutArea != null) selectComboValue(aboutArea, nonBlank(profile.getTargetAgeGroup(), "Elementary School"));
        if (locationCombo != null) locationCombo.setText(safe(profile.getLocation()));
        if (dateSelect != null) dateSelect.setText(safe(profile.getAvailableStartDate()));
        if (bioArea != null) bioArea.setText(safe(profile.getBio()));

        updateHeader();
    }

    private void chooseAndUploadAvatar() {
        if (profile == null) profile = resolveCurrentProfile();
        if (profile == null) return;
        Window window = uploadAvatarButton == null || uploadAvatarButton.getScene() == null ? null : uploadAvatarButton.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Upload Profile Avatar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File file = chooser.showOpenDialog(window);
        if (file == null) return;
        if (STORE.updateTeacherAvatar(currentActor(), profile.getId(), file.toPath()) != null) {
            profile = STORE.teacher(profile.getId());
            updateHeader();
            setFeedback("Avatar updated for " + safe(profile.getName()) + ".");
            LoginController.showAlert(Alert.AlertType.INFORMATION, "Avatar Updated", "Your profile photo has been updated and synced.");
        } else {
            LoginController.showAlert(Alert.AlertType.ERROR, "Upload Failed", "Could not update avatar image.");
        }
    }

    private boolean saveDraft(boolean notify) {
        if (!validateRequiredFields()) return false;
        if (profile == null) profile = resolveCurrentProfile();
        if (profile == null) {
            setFeedback("No profile is available to save.");
            return false;
        }

        profile.setName(value(fullNameField));
        profile.setEmail(value(emailField));
        profile.setNationality(comboOrEditorValue(nationalityCombo, "Other"));
        profile.setPhone(value(phoneField));
        profile.setDegree(comboOrEditorValue(degreeCombo, "Bachelor's Degree"));
        String majorVal = comboOrEditorValue(majorCombo, "English Education");
        profile.setMajor(majorVal);
        profile.setSubject(majorVal);

        String expVal = experienceTierCombo != null && experienceTierCombo.getValue() != null
                ? experienceTierCombo.getValue()
                : value(experienceCombo);
        profile.setExperience(CatalogData.normalizeExperienceBand(expVal));

        if (!selectedCertificates.isEmpty()) {
            profile.setCertificates(CatalogData.joinCertificates(selectedCertificates));
        }
        profile.setExpectedSalary(comboOrEditorValue(expectedSalaryCombo, "$1,500 - $2,000"));
        profile.setTargetAgeGroup(comboOrEditorValue(aboutArea, "Elementary School"));
        profile.setLocation(value(locationCombo));
        profile.setAvailableStartDate(value(dateSelect));
        if (bioArea != null && bioArea.getText() != null) {
            profile.setBio(bioArea.getText().trim());
        }

        STORE.saveTeacherProfile(currentActor(), profile);
        profile = STORE.teacher(profile.getId());
        updateHeader();
        setFeedback("Draft saved.");
        if (notify) {
            LoginController.showAlert(Alert.AlertType.INFORMATION, "Draft Saved", "Your teacher profile has been saved.");
        }
        return true;
    }

    private void submitForReview() {
        if (!saveDraft(false)) return;
        if (profile == null) return;
        STORE.status(currentActor(), profile.getId(), "PENDING");
        STORE.log(currentActor(), "Submitted teacher profile #" + profile.getId() + " for review");
        profile = STORE.teacher(profile.getId());
        updateHeader();
        setFeedback("Profile submitted for review (qCV).");
        LoginController.showAlert(Alert.AlertType.INFORMATION, "Submitted for Review", "Your profile status is now qCV (Pending Review).");
    }

    private boolean validateRequiredFields() {
        String name = value(fullNameField);
        String email = value(emailField);
        if (name.isBlank() || email.isBlank()) {
            setFeedback("Full name and email are required.");
            LoginController.showAlert(Alert.AlertType.ERROR, "Validation Error", "Full name and email are required.");
            return false;
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            setFeedback("Enter a valid email address.");
            LoginController.showAlert(Alert.AlertType.ERROR, "Validation Error", "Enter a valid email address.");
            return false;
        }
        String startDate = value(dateSelect);
        if (!startDate.isBlank() && !startDate.matches("^(0?[1-9]|1[0-2])/\\d{4}$")) {
            try {
                LocalDate.parse(startDate, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            } catch (DateTimeParseException ex) {
                setFeedback("Enter the available start date as dd/MM/yyyy.");
                LoginController.showAlert(Alert.AlertType.ERROR, "Validation Error", "Enter the available start date as dd/MM/yyyy.");
                return false;
            }
        }
        return true;
    }

    private void updateHeader() {
        if (profile == null) return;
        String initials = ProfileCardBuilder.initialsOf(profile.getName());
        if (sidebarAvatarInitials != null) sidebarAvatarInitials.setText(initials);
        if (topAvatarInitials != null) topAvatarInitials.setText(initials);
        if (sidebarTeacherName != null) sidebarTeacherName.setText(safe(profile.getName()));
        if (headerNameLabel != null) headerNameLabel.setText(safe(profile.getName()));
        if (headerSubtitleLabel != null) {
            String deg = nonBlank(profile.getDegree(), "Bachelor's Degree");
            String maj = nonBlank(profile.getMajor(), nonBlank(profile.getSubject(), "English Education"));
            String sal = nonBlank(profile.getExpectedSalary(), "$1,500 - $2,000");
            headerSubtitleLabel.setText(deg + " in " + maj + " · " + nonBlank(profile.getNationality(), "International") + " · " + sal + "/mo");
        }
        if (avatarContainer != null) {
            avatarContainer.getChildren().setAll(ProfileCardBuilder.createAvatarNode(profile, 60));
        }
        ProfileCardBuilder.updateAvatarSlot(sourceRoot(), profile, 50);
        if (statusBadgeLabel != null) {
            statusBadgeLabel.setText(CatalogData.formatStatusBadge(profile.getStatus()));
            statusBadgeLabel.getStyleClass().setAll("badge", CatalogData.badgeStyleForStatus(profile.getStatus()));
        }
        if (staffFeedbackBanner != null) {
            String fb = safe(profile.getStaffFeedback());
            if (!fb.isBlank()) {
                staffFeedbackBanner.setText("Matching Staff Feedback: " + fb);
                staffFeedbackBanner.setManaged(true);
                staffFeedbackBanner.setVisible(true);
            } else {
                staffFeedbackBanner.setManaged(false);
                staffFeedbackBanner.setVisible(false);
            }
        }
    }

    private void selectComboValue(ComboBox<String> combo, String value) {
        if (combo == null || value == null || value.isBlank()) return;
        if (!combo.getItems().contains(value)) {
            combo.getItems().add(value);
        }
        combo.setValue(value);
        if (combo.isEditable() && combo.getEditor() != null) {
            combo.getEditor().setText(value);
        }
    }

    private String comboOrEditorValue(ComboBox<String> combo, String fallback) {
        if (combo == null) return fallback;
        if (combo.isEditable() && combo.getEditor() != null) {
            String txt = combo.getEditor().getText();
            if (txt != null && !txt.isBlank()) {
                return txt.trim();
            }
        }
        String val = combo.getValue();
        return val == null || val.isBlank() ? fallback : val.trim();
    }

    private String value(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private void setFeedback(String message) {
        if (profileNoteLabel != null) {
            profileNoteLabel.setText(message);
            return;
        }
        Parent root = sourceRoot();
        if (root != null) {
            Label note = FxNodes.findFirstByStyleClass(root, "note", Label.class);
            if (note != null) {
                note.setText(message);
            }
        }
    }

    private Parent sourceRoot() {
        return FxNodes.rootOf(nav_TeacherProfile, saveDraftButton, submitProfileButton, fullNameField);
    }

    private String currentActor() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "teacher" : u;
    }

    private void navigate(String fxml, Node source) {
        LoginController.navigateTo(source, fxml);
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
