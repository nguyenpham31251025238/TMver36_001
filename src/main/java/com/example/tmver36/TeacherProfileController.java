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
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** JavaFX controller for editing the current teacher profile (TeacherProfile.fxml). */
public class TeacherProfileController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_TeacherProfile;
    @FXML private Button nav_TeacherDocuments;
    @FXML private Button nav_TeacherSchedule;
    @FXML private Button nav_TeacherHistory;
    @FXML private Button nav_Login;

    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> nationalityCombo;
    @FXML private TextField phoneField;
    @FXML private TextField experienceCombo;
    @FXML private ComboBox<String> aboutArea;
    @FXML private TextField locationCombo;
    @FXML private TextField dateSelect;
    @FXML private TextArea bioArea;
    @FXML private Button saveDraftButton;
    @FXML private Button submitProfileButton;

    private TeacherProfile profile;

    @FXML
    private void initialize() {
        wireNavigation();
        loadProfileIntoForm();
        if (saveDraftButton != null) saveDraftButton.setOnAction(e -> saveDraft(true));
        if (submitProfileButton != null) submitProfileButton.setOnAction(e -> submitForReview());
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
        if (nationalityCombo != null) selectComboValue(nationalityCombo, profile.getNationality());
        if (phoneField != null) phoneField.setText(safe(profile.getPhone()));
        if (experienceCombo != null) experienceCombo.setText(safe(profile.getExperience()));
        if (aboutArea != null) selectComboValue(aboutArea, profile.getTargetAgeGroup());
        if (locationCombo != null) locationCombo.setText(safe(profile.getLocation()));
        if (dateSelect != null) dateSelect.setText(safe(profile.getAvailableStartDate()));
        if (bioArea != null) bioArea.setText(safe(profile.getBio()));

        Parent root = sourceRoot();
        if (root != null) {
            setLabelByText(root, "Emily Martin", safe(profile.getName()));
            setLabelByText(root, "English Teacher · United Kingdom",
                    safe(profile.getSubject()) + " Teacher · " + nonBlank(profile.getNationality(), "International"));
            setLabelByText(root, "Pending review", nonBlank(profile.getStatus(), "DRAFT"));
            setLabelByText(root, "Profile submitted on 14/10/2026. Update information when Matching requests.",
                    "Profile status: " + nonBlank(profile.getStatus(), "DRAFT") + ". Update your information and save draft or submit for review.");
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
        profile.setNationality(value(nationalityCombo));
        profile.setPhone(value(phoneField));
        profile.setExperience(value(experienceCombo));
        profile.setTargetAgeGroup(value(aboutArea));
        profile.setLocation(value(locationCombo));
        profile.setAvailableStartDate(value(dateSelect));
        if (bioArea != null && bioArea.getText() != null) {
            profile.setBio(bioArea.getText().trim());
        }

        STORE.saveTeacherProfile(currentActor(), profile);
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
        updateHeader();
        setFeedback("Profile submitted for review.");
        LoginController.showAlert(Alert.AlertType.INFORMATION, "Submitted for Review", "Your profile status is now PENDING.");
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
        Parent root = sourceRoot();
        if (root != null && profile != null) {
            for (Label label : FxNodes.findAllByStyleClass(root, "sidebar-name", Label.class)) {
                label.setText(profile.getName());
            }
            setLabelByText(root, "Pending review", nonBlank(profile.getStatus(), "DRAFT"));
        }
    }

    private void selectComboValue(ComboBox<String> combo, String value) {
        if (combo == null || value == null || value.isBlank()) return;
        if (!combo.getItems().contains(value)) {
            combo.getItems().add(value);
        }
        combo.setValue(value);
    }

    private String value(ComboBox<String> combo) {
        return combo == null || combo.getValue() == null ? "" : combo.getValue().trim();
    }

    private String value(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private void setLabelByText(Parent root, String oldText, String newText) {
        if (root == null || newText == null || newText.isBlank()) return;
        for (Label label : FxNodes.findAll(root, Label.class)) {
            if (oldText.equals(label.getText())) {
                label.setText(newText);
                return;
            }
        }
    }

    private void setFeedback(String message) {
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
