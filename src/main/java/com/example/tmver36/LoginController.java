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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Login controller and shared session/navigation entry point.
 */
public class LoginController {

    public static final PortalStore STORE = new PortalStore();

    private static final String REMEMBER_SESSION_FILE = "remember-session.properties";
    private static final long REMEMBER_TTL_DAYS = 14L;
    private static final String REMEMBER_PEPPER = "TMver36-RememberSession-v2";

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
    @FXML private CheckBox rememberMeCheck;
    @FXML private Label sessionHintLabel;
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
        applyRememberedSessionToForm();
    }

    private void applyRememberedSessionToForm() {
        RememberedSession session = loadValidRememberedSession(STORE);
        TextField userInput = activeUserField();
        ComboBox<String> roleInput = activeRoleCombo();
        if (session != null) {
            if (userInput != null) {
                userInput.setText(session.username());
            }
            if (roleInput != null) {
                roleInput.setValue(roleDisplayLabel(session.role()));
            }
            if (rememberMeCheck != null) {
                rememberMeCheck.setSelected(true);
            }
            if (sessionHintLabel != null) {
                sessionHintLabel.setText("Saved session active for @" + session.username() + " (" + session.role() + "). Click 'Enter interface' to continue or type another password.");
                sessionHintLabel.setVisible(true);
                sessionHintLabel.setManaged(true);
            }
        } else {
            if (rememberMeCheck != null) {
                rememberMeCheck.setSelected(false);
            }
            if (sessionHintLabel != null) {
                sessionHintLabel.setVisible(false);
                sessionHintLabel.setManaged(false);
            }
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

        String expectedRole = mapSelectedRole(selectedRole);
        if (expectedRole == null) {
            showError("Invalid role", "Please choose a valid role before entering the workspace.");
            return;
        }

        // Pull latest shared state from cloud before authenticating
        STORE.syncFromCloud();

        AppUser authenticatedUser = null;
        RememberedSession remembered = loadValidRememberedSession(STORE);
        if (!username.isEmpty() && password.isEmpty()
                && remembered != null
                && remembered.username().equalsIgnoreCase(username)
                && expectedRole.equals(remembered.role())) {
            AppUser candidate = STORE.findUser(remembered.username());
            if (candidate != null && candidate.isActive() && expectedRole.equals(candidate.getRole())) {
                authenticatedUser = candidate;
            }
        }

        if (authenticatedUser == null) {
            if (username.isEmpty() || password.isEmpty()) {
                showError("Missing login details", "Please enter both your username and password.");
                return;
            }
            authenticatedUser = STORE.authenticate(username, password);
            if (authenticatedUser == null) {
                STORE.log(username, "Failed login attempt");
                showError("Sign-in failed", "Incorrect username or password, or account is disabled.");
                return;
            }
        }

        if (!expectedRole.equals(authenticatedUser.getRole())) {
            showError("Role mismatch", "This account does not have permission for the selected role.");
            return;
        }

        boolean remember = rememberMeCheck != null && rememberMeCheck.isSelected();
        if (remember) {
            saveRememberedSession(STORE, authenticatedUser);
        } else {
            clearRememberedSession(STORE);
        }

        currentUsername = authenticatedUser.getUsername();
        currentRole = authenticatedUser.getRole();
        STORE.log(currentUsername, "Logged into " + currentRole + " workspace");

        Node source = event != null && event.getSource() instanceof Node node ? node : loginButton;
        if (!navigateTo(source, homeScreenForRole(currentRole))) {
            showError("Navigation error", "Unable to open the workspace screen for " + selectedRole + ".");
        }
    }

    public record RememberedSession(String username, String role, long expiresAtEpochSec, String token) {}

    public static void saveRememberedSession(PortalStore store, AppUser user) {
        if (store == null || user == null || !user.isActive()) {
            return;
        }
        try {
            Path dataDir = store.getDataDir();
            Files.createDirectories(dataDir);
            Path file = dataDir.resolve(REMEMBER_SESSION_FILE);
            long expiresAt = Instant.now().plus(REMEMBER_TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();
            String token = computeRememberToken(user, expiresAt);
            Properties props = new Properties();
            props.setProperty("username", user.getUsername());
            props.setProperty("role", user.getRole());
            props.setProperty("expiresAt", Long.toString(expiresAt));
            props.setProperty("token", token);
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "TeacherMatch Remember Me session token (never stores plaintext password)");
            }
        } catch (IOException ignored) {
        }
    }

    public static void clearRememberedSession(PortalStore store) {
        if (store == null) return;
        try {
            Files.deleteIfExists(store.getDataDir().resolve(REMEMBER_SESSION_FILE));
        } catch (IOException ignored) {
        }
    }

    public static RememberedSession loadValidRememberedSession(PortalStore store) {
        if (store == null) return null;
        Path file = store.getDataDir().resolve(REMEMBER_SESSION_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        } catch (IOException ex) {
            return null;
        }
        String username = props.getProperty("username", "").trim();
        String role = props.getProperty("role", "").trim();
        String token = props.getProperty("token", "").trim();
        long expiresAt;
        try {
            expiresAt = Long.parseLong(props.getProperty("expiresAt", "0").trim());
        } catch (NumberFormatException ex) {
            clearRememberedSession(store);
            return null;
        }
        if (username.isEmpty() || role.isEmpty() || token.isEmpty() || Instant.now().getEpochSecond() >= expiresAt) {
            clearRememberedSession(store);
            return null;
        }
        AppUser user = store.findUser(username);
        if (user == null || !user.isActive() || !role.equals(user.getRole())) {
            clearRememberedSession(store);
            return null;
        }
        String expectedToken = computeRememberToken(user, expiresAt);
        if (!MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8))) {
            clearRememberedSession(store);
            return null;
        }
        return new RememberedSession(username, role, expiresAt, token);
    }

    private static String computeRememberToken(AppUser user, long expiresAtEpochSec) {
        String payload = user.getUsername() + "|" + user.getRole() + "|" + user.getPasswordHash() + "|" + expiresAtEpochSec + "|" + REMEMBER_PEPPER;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(payload.hashCode());
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

    public static String roleDisplayLabel(String roleCode) {
        if (roleCode == null) return "Matching Staff";
        return switch (roleCode.trim().toUpperCase()) {
            case "ADMIN" -> "System Admin";
            case "TEACHER" -> "Teacher";
            case "PARTNER" -> "Partner";
            default -> "Matching Staff";
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
