package com.example.tmver36;

import com.example.tmver36.model.AppUser;
import com.example.tmver36.service.PortalStore;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

/**
 * Login controller and shared session/navigation entry point.
 */
public class LoginController {

    public static final PortalStore STORE = new PortalStore();

    private static final Map<String, Set<String>> ROLE_SCREENS = Map.of(
            "ADMIN", Set.of("AdminAccounts.fxml", "AdminLogs.fxml"),
            "STAFF", Set.of("StaffDashboard.fxml", "StaffReview.fxml", "InterviewWorkspace.fxml"),
            "PARTNER", Set.of("PartnerDashboard.fxml", "PartnerProfile.fxml", "PartnerInterview.fxml"),
            "TEACHER", Set.of("TeacherProfile.fxml", "TeacherDocuments.fxml", "TeacherSchedule.fxml", "TeacherHistory.fxml")
    );

    private static String currentUsername;
    private static String currentRole;

    @FXML private ComboBox<String> demoRole;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;

    // Optional aliases for compatibility
    @FXML private TextField usernameField;
    @FXML private ComboBox<String> roleCombo;

    @FXML
    private void initialize() {
        ComboBox<String> rc = activeRoleCombo();
        if (rc != null && rc.getValue() == null && !rc.getItems().isEmpty()) {
            rc.getSelectionModel().selectFirst();
        }
        if (loginButton != null) {
            loginButton.setDefaultButton(true);
        }
    }

    private TextField activeUserField() {
        return emailField != null ? emailField : usernameField;
    }

    private ComboBox<String> activeRoleCombo() {
        return demoRole != null ? demoRole : roleCombo;
    }

    @FXML
    private void handleLoginAction(ActionEvent event) {
        TextField userInput = activeUserField();
        ComboBox<String> roleInput = activeRoleCombo();

        String username = userInput == null || userInput.getText() == null
                ? ""
                : userInput.getText().trim();
        String password = passwordField == null || passwordField.getText() == null
                ? ""
                : passwordField.getText();
        String selectedRole = roleInput == null ? null : roleInput.getValue();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Missing login details", "Please enter both your username and password.");
            return;
        }

        String expectedRole = mapSelectedRole(selectedRole);
        if (expectedRole == null) {
            showError("Invalid role", "Please choose a valid role before entering the workspace.");
            return;
        }

        AppUser authenticatedUser = STORE.authenticate(username, password);
        if (authenticatedUser == null) {
            STORE.log(username, "Failed login attempt");
            showError("Sign-in failed", "Incorrect username or password, or account is disabled.");
            return;
        }

        if (!expectedRole.equals(authenticatedUser.getRole())) {
            showError("Role mismatch", "This account does not have permission for the selected role.");
            return;
        }

        currentUsername = authenticatedUser.getUsername();
        currentRole = authenticatedUser.getRole();
        STORE.log(currentUsername, "Logged into " + currentRole + " workspace");

        Node source = event != null && event.getSource() instanceof Node node ? node : loginButton;
        if (!navigateTo(source, homeScreenForRole(currentRole))) {
            showError("Navigation error", "Unable to open the workspace screen for " + selectedRole + ".");
        }
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    public static String getCurrentRole() {
        return currentRole;
    }

    public static void setCurrentSession(String username, String role) {
        currentUsername = username;
        currentRole = role;
    }

    public static void clearSession() {
        currentUsername = null;
        currentRole = null;
    }

    public static boolean navigateTo(Node source, String fxmlFile) {
        try {
            if ("Login.fxml".equals(fxmlFile)) {
                if (currentUsername != null) {
                    STORE.log(currentUsername, "Logged out");
                }
                currentUsername = null;
                currentRole = null;
            } else {
                String effectiveRole = STORE.userRole(currentUsername);
                if (effectiveRole == null) {
                    effectiveRole = currentRole;
                }
                if (!canOpen(fxmlFile, effectiveRole)) {
                    return false;
                }
            }
            URL resource = LoginController.class.getResource(fxmlFile);
            if (resource == null) {
                resource = LoginController.class.getResource("/com/example/tmver36/" + fxmlFile);
            }
            if (resource == null) {
                resource = LoginController.class.getResource("/" + fxmlFile);
            }
            if (resource == null || source == null || source.getScene() == null) {
                return false;
            }
            Parent root = FXMLLoader.load(resource);
            Scene currentScene = source.getScene();
            Window window = currentScene.getWindow();
            if (window instanceof Stage stage) {
                Scene nextScene = new Scene(root, currentScene.getWidth(), currentScene.getHeight());
                stage.setTitle(screenTitle(fxmlFile));
                stage.setScene(nextScene);
                stage.show();
            } else {
                currentScene.setRoot(root);
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    public static boolean canOpen(String fxmlFile, String role) {
        return role != null && ROLE_SCREENS.getOrDefault(role, Set.of()).contains(fxmlFile);
    }

    public static String homeScreenForRole(String role) {
        return switch (role) {
            case "ADMIN" -> "AdminAccounts.fxml";
            case "STAFF" -> "StaffDashboard.fxml";
            case "PARTNER" -> "PartnerDashboard.fxml";
            case "TEACHER" -> "TeacherProfile.fxml";
            default -> "Login.fxml";
        };
    }

    private String mapSelectedRole(String roleLabel) {
        if (roleLabel == null) return null;
        return switch (roleLabel.trim()) {
            case "System Admin", "Admin", "Administrator", "ADMIN" -> "ADMIN";
            case "Matching Staff", "STAFF" -> "STAFF";
            case "Partner", "PARTNER" -> "PARTNER";
            case "Teacher", "TEACHER" -> "TEACHER";
            default -> null;
        };
    }

    private static String screenTitle(String fxmlFile) {
        return "TeacherMatch - " + fxmlFile.replace(".fxml", "");
    }

    public static void showAlert(Alert.AlertType type, String title, String message) {
        if (Boolean.getBoolean("tm.test.suppressAlerts")) {
            return;
        }
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }
}
