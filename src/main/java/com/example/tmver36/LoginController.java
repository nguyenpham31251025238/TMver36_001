package com.example.tmver36;

import com.example.tmver36.model.AppUser;
import com.example.tmver36.service.PortalStore;
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

import java.io.IOException;
import java.net.URL;
import java.util.Map;

/** JavaFX login controller. Uses the project's existing PortalStore authentication. */
public class LoginController {
    /** Shared in-memory store so navigation between JavaFX screens keeps the same data. */
    public static final PortalStore STORE = AdminAccountsController.STORE;

    private static String currentUsername;
    private static String currentRole;

    @FXML private ComboBox<String> demoRole;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;

    @FXML
    private void initialize() {
        if (demoRole != null && demoRole.getValue() == null && !demoRole.getItems().isEmpty()) {
            demoRole.getSelectionModel().selectFirst();
        }
        if (loginButton != null) {
            loginButton.setDefaultButton(true);
        }
    }

    @FXML
    private void handleLoginAction() {
        String username = emailField == null ? "" : emailField.getText().trim();
        String password = passwordField == null ? "" : passwordField.getText();
        String selectedRoleLabel = demoRole == null ? null : demoRole.getValue();

        if (username.isBlank() || password.isBlank()) {
            showError("Missing credentials", "Enter your username and password.");
            return;
        }

        AppUser user = STORE.authenticate(username, password);
        if (user == null) {
            STORE.log(username, "Failed login attempt");
            showError("Login failed", "The username or password is incorrect.");
            return;
        }

        String selectedRole = roleFromLabel(selectedRoleLabel);
        if (selectedRole == null || !selectedRole.equalsIgnoreCase(user.getRole())) {
            showError("Role mismatch", "The selected preview role does not match this account's role.");
            return;
        }

        currentUsername = user.getUsername();
        currentRole = user.getRole();
        STORE.log(currentUsername, "Logged in as " + currentRole);

        String destination = destinationForRole(currentRole);
        if (!navigateTo(loginButton, destination)) {
            showError("Navigation failed", "Could not load " + destination + ". Check that the FXML file is in the application's resource path.");
        }
    }

    private String roleFromLabel(String label) {
        if (label == null) return null;
        return switch (label.trim().toLowerCase()) {
            case "matching staff", "staff" -> "STAFF";
            case "teacher" -> "TEACHER";
            case "partner" -> "PARTNER";
            case "system admin", "admin", "administrator" -> "ADMIN";
            default -> null;
        };
    }

    private String destinationForRole(String role) {
        return switch (role) {
            case "ADMIN" -> "AdminAccounts.fxml";
            case "PARTNER" -> "PartnerDashboard.fxml";
            case "TEACHER" -> "TeacherProfile.fxml";
            case "STAFF" -> "StaffDashboard.fxml";
            default -> "Login.fxml";
        };
    }

    /** Loads a real FXML screen into the Stage currently displaying the source node. */
    public static boolean navigateTo(Node source, String fxmlFile) {
        try {
            URL resource = LoginController.class.getResource("/com/example/tmver36/" + fxmlFile);
            if (resource == null) {
                // Also support projects that place FXML files at the resource root.
                resource = LoginController.class.getResource("/" + fxmlFile);
            }
            if (resource == null || source == null || source.getScene() == null) return false;
            Parent root = FXMLLoader.load(resource);
            Stage stage = (Stage) source.getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();
            return true;
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    public static String getCurrentUsername() {
        return currentUsername;
    }

    public static String getCurrentRole() {
        return currentRole;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
