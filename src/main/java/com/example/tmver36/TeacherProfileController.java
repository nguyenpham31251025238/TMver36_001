package com.example.tmver36;

import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.lang.reflect.Method;
import java.util.Locale;

/** JavaFX controller for editing the current teacher profile. */
public class TeacherProfileController {
    private static final PortalStore STORE = LoginController.STORE;
    // PortalStore's demo TEACHER account currently maps to profile id 1 (see visibleInterviews()).
    private static final long TEACHER_ID = 1L;

    @FXML private Button nav_TeacherProfile;
    @FXML private Button nav_TeacherDocuments;
    @FXML private Button nav_TeacherSchedule;
    @FXML private Button nav_TeacherHistory;
    @FXML private Button nav_Login;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private Button saveDraftButton;
    @FXML private Button submitProfileButton;

    private TeacherProfile profile;

    @FXML
    private void initialize() {
        wireNavigation();
        loadProfileIntoForm();
        if (saveDraftButton != null) saveDraftButton.setOnAction(e -> saveDraft());
        if (submitProfileButton != null) submitProfileButton.setOnAction(e -> submitForReview());
    }

    private void wireNavigation() {
        if (nav_TeacherProfile != null) nav_TeacherProfile.setOnAction(e -> loadProfileIntoForm());
        if (nav_TeacherDocuments != null) nav_TeacherDocuments.setOnAction(e -> navigate("TeacherDocuments.fxml", nav_TeacherDocuments));
        if (nav_TeacherSchedule != null) nav_TeacherSchedule.setOnAction(e -> navigate("TeacherSchedule.fxml", nav_TeacherSchedule));
        if (nav_TeacherHistory != null) nav_TeacherHistory.setOnAction(e -> navigate("TeacherHistory.fxml", nav_TeacherHistory));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void loadProfileIntoForm() {
        profile = STORE.teacher(TEACHER_ID);
        if (profile == null) {
            setFeedback("No teacher profile is linked to this account.");
            return;
        }
        if (fullNameField != null) fullNameField.setText(property(profile, "getName", "getFullName"));
        if (emailField != null) emailField.setText(property(profile, "getEmail", "getEmailAddress"));

        Parent root = sourceRoot();
        if (root != null) {
            setLabelByText(root, "Emily Martin", property(profile, "getName", "getFullName"));
            setLabelByText(root, "English Teacher · United Kingdom", "English Teacher · " + nonBlank(property(profile, "getNationality", "getCountry"), "Nationality not provided"));
            setLabelByText(root, "Pending review", nonBlank(property(profile, "getStatus"), "Draft"));
            setLabelByText(root, "Profile submitted on 14/10/2026. Update information when Matching requests.",
                    "Profile status: " + nonBlank(property(profile, "getStatus"), "DRAFT") + ". Update your information and save the draft or submit for review.");
        }
        fillUnidentifiedFields(root);
    }

    private void fillUnidentifiedFields(Parent root) {
        if (root == null || profile == null) return;
        // These FXML fields do not have fx:id; locate their TextField/TextArea by the label in the same field VBox.
        setFieldNextToLabel(root, "Nationality", property(profile, "getNationality", "getCountry"));
        setFieldNextToLabel(root, "Phone number", property(profile, "getPhone", "getPhoneNumber"));
        setFieldNextToLabel(root, "Teaching experience", property(profile, "getExperience", "getYearsExperience"));
        setFieldNextToLabel(root, "Preferred location", property(profile, "getLocation", "getCity"));
        setFieldNextToLabel(root, "About me", property(profile, "getAbout", "getBio", "getDescription"));
    }

    private void saveDraft() {
        if (!validateRequiredFields()) return;
        if (profile == null) profile = STORE.teacher(TEACHER_ID);
        if (profile == null) { setFeedback("No profile is available to save."); return; }

        boolean nameSaved = setProperty(profile, fullNameField == null ? "" : fullNameField.getText().trim(), "setName", "setFullName");
        boolean emailSaved = setProperty(profile, emailField == null ? "" : emailField.getText().trim(), "setEmail", "setEmailAddress");
        if (!nameSaved || !emailSaved) {
            setFeedback("The profile model does not expose setters for all edited fields. Name/email changes may not be retained.");
            return;
        }
        STORE.log(currentActor(), "Saved teacher profile draft for " + fullNameField.getText().trim());
        updateHeader();
        setFeedback("Draft saved in the current application session.");
    }

    private void submitForReview() {
        if (!validateRequiredFields()) return;
        saveDraft();
        if (profile == null) return;
        STORE.status(profile.getId(), "PENDING");
        STORE.log(currentActor(), "Submitted teacher profile " + profile.getId() + " for review");
        updateHeader();
        setFeedback("Profile submitted for review.");
    }

    private boolean validateRequiredFields() {
        String name = fullNameField == null || fullNameField.getText() == null ? "" : fullNameField.getText().trim();
        String email = emailField == null || emailField.getText() == null ? "" : emailField.getText().trim();
        if (name.isBlank() || email.isBlank()) {
            setFeedback("Full name and email are required.");
            return false;
        }
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            setFeedback("Enter a valid email address.");
            return false;
        }
        return true;
    }

    private void updateHeader() {
        Parent root = sourceRoot();
        if (root != null) {
            setLabelByText(root, "Emily Martin", property(profile, "getName", "getFullName"));
            setLabelByText(root, "Pending review", nonBlank(property(profile, "getStatus"), "DRAFT"));
        }
    }

    private void setFieldNextToLabel(Parent root, String labelText, String value) {
        if (value == null || value.isBlank()) return;
        for (Node node : root.lookupAll(".field-label")) {
            if (node instanceof Label label && labelText.equalsIgnoreCase(label.getText()) && label.getParent() instanceof VBox box) {
                for (Node child : box.getChildren()) {
                    if (child instanceof TextField field) { field.setText(value); return; }
                    if (child instanceof TextArea area) { area.setText(value); return; }
                    if (child instanceof ComboBox<?> combo) {
                        @SuppressWarnings("unchecked") ComboBox<Object> typed = (ComboBox<Object>) combo;
                        for (Object item : typed.getItems()) if (item != null && item.toString().equalsIgnoreCase(value)) { typed.setValue(item); return; }
                        return;
                    }
                }
            }
        }
    }

    private void setLabelByText(Parent root, String oldText, String newText) {
        if (newText == null || newText.isBlank()) return;
        for (Node node : root.lookupAll("Label")) {
            if (node instanceof Label label && oldText.equals(label.getText())) { label.setText(newText); return; }
        }
    }

    private void setFeedback(String message) {
        Parent root = sourceRoot();
        if (root != null) {
            for (Node node : root.lookupAll(".note")) {
                if (node instanceof Label label) { label.setText(message); return; }
            }
        }
        System.out.println(message);
    }

    private Parent sourceRoot() {
        Button source = nav_TeacherProfile != null ? nav_TeacherProfile : saveDraftButton;
        return source == null || source.getScene() == null ? null : source.getScene().getRoot();
    }

    private String currentActor() {
        String username = LoginController.getCurrentUsername();
        return username == null || username.isBlank() ? "teacher" : username;
    }

    private void navigate(String fxml, Node source) { LoginController.navigateTo(source, fxml); }
    private static String nonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }

    private static String property(Object target, String... getters) {
        if (target == null) return "";
        for (String getter : getters) {
            try {
                Method method = target.getClass().getMethod(getter);
                Object value = method.invoke(target);
                if (value != null) return value.toString();
            } catch (ReflectiveOperationException ignored) { }
        }
        return "";
    }

    private static boolean setProperty(Object target, String value, String... setters) {
        if (target == null) return false;
        for (String setter : setters) {
            try {
                Method method = target.getClass().getMethod(setter, String.class);
                method.invoke(target, value);
                return true;
            } catch (ReflectiveOperationException ignored) { }
        }
        return false;
    }
}
