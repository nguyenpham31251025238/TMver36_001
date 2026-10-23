package com.example.tmver36;

import com.example.tmver36.model.AppUser;
import com.example.tmver36.service.PortalStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Controller for AdminAccounts.fxml. */
public class AdminAccountsController {

    /** Shared in-memory store for the desktop application. */
    public static final PortalStore STORE = new PortalStore();

    @FXML private Button nav_AdminAccounts;
    @FXML private Button nav_AdminLogs;
    @FXML private Button nav_Login;
    // In AdminAccounts.fxml, searchField is a VBox containing accountSearch.
    @FXML private javafx.scene.layout.VBox searchField;
    @FXML private TextField accountSearch;
    @FXML private Button searchAccountsButton;
    @FXML private TableView<AppUser> accountTable;
    @FXML private TableColumn<AppUser, String> colUsername;
    @FXML private TableColumn<AppUser, String> colEmail;
    @FXML private TableColumn<AppUser, String> colRole;
    @FXML private TableColumn<AppUser, String> colStatus;
    @FXML private TextField accountName;
    @FXML private TextField accountEmail;
    @FXML private ComboBox<String> accountRole;
    @FXML private Button deleteAccountButton;
    @FXML private Button saveAccountButton;
    @FXML private Button assignRoleButton;

    private final ObservableList<AppUser> displayedUsers = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        configureTable();
        configureRoleOptions();
        refreshAccounts();

        if (searchAccountsButton != null) {
            searchAccountsButton.setOnAction(event -> handleSearch());
        }
        if (accountSearch != null) {
            accountSearch.textProperty().addListener((observable, oldValue, newValue) -> handleSearch());
        }
        if (accountTable != null) {
            accountTable.getSelectionModel().selectedItemProperty().addListener(
                    (observable, oldUser, selectedUser) -> populateForm(selectedUser));
        }
        if (saveAccountButton != null) {
            saveAccountButton.setOnAction(event -> handleSaveAccount());
        }
        if (deleteAccountButton != null) {
            deleteAccountButton.setOnAction(event -> handleDeleteAccount());
        }
        if (assignRoleButton != null) {
            assignRoleButton.setOnAction(event -> handleAssignRole());
        }
        if (nav_AdminAccounts != null) {
            nav_AdminAccounts.setOnAction(event -> refreshAccounts());
        }
        if (nav_AdminLogs != null) {
            nav_AdminLogs.setOnAction(event -> navigateTo("AdminLogs.fxml"));
        }
    }

    private void configureTable() {
        if (accountTable == null) {
            return;
        }
        if (colUsername != null) {
            colUsername.setCellValueFactory(new PropertyValueFactory<>("username"));
        }
        /* AppUser in PortalStore has username, password, role and fullName; it has no email field.
           The existing FXML labels this column Email, so show fullName rather than inventing an email. */
        if (colEmail != null) {
            colEmail.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        }
        if (colRole != null) {
            colRole.setCellValueFactory(new PropertyValueFactory<>("role"));
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(cell -> javafx.beans.binding.Bindings.createStringBinding(
                    () -> "ACTIVE"));
        }
        accountTable.setItems(displayedUsers);
    }

    private void configureRoleOptions() {
        if (accountRole != null) {
            accountRole.setItems(FXCollections.observableArrayList("ADMIN", "STAFF", "PARTNER", "TEACHER"));
            if (accountRole.getValue() == null) {
                accountRole.setValue("STAFF");
            }
        }
    }

    private void refreshAccounts() {
        displayedUsers.setAll(STORE.users());
        handleSearch();
    }

    @FXML
    private void handleSearch() {
        if (accountTable == null) {
            return;
        }
        String query = accountSearch == null || accountSearch.getText() == null
                ? "" : accountSearch.getText().trim().toLowerCase(Locale.ROOT);
        Collection<AppUser> allUsers = STORE.users();
        List<AppUser> filtered = new ArrayList<>();
        for (AppUser user : allUsers) {
            boolean matches = query.isBlank()
                    || contains(user.getUsername(), query)
                    || contains(user.getFullName(), query)
                    || contains(user.getRole(), query);
            if (matches) {
                filtered.add(user);
            }
        }
        displayedUsers.setAll(filtered);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void populateForm(AppUser user) {
        if (user == null) {
            return;
        }
        if (accountName != null) {
            accountName.setText(user.getUsername());
        }
        if (accountEmail != null) {
            accountEmail.setText(user.getFullName());
        }
        if (accountRole != null) {
            accountRole.setValue(user.getRole());
        }
    }

    private void handleSaveAccount() {
        String username = accountName == null || accountName.getText() == null
                ? "" : accountName.getText().trim();
        String fullName = accountEmail == null || accountEmail.getText() == null
                ? "" : accountEmail.getText().trim();
        String role = accountRole == null ? null : accountRole.getValue();

        if (username.isBlank() || fullName.isBlank() || role == null || role.isBlank()) {
            showMessage(Alert.AlertType.WARNING, "Missing information",
                    "Enter a username, display name, and role before saving.");
            return;
        }
        if (STORE.userExists(username)) {
            showMessage(Alert.AlertType.WARNING, "Account already exists",
                    "This username already exists. Select the account and use Assign role to change its role.");
            return;
        }

        // The current AppUser/PortalStore API has no email field or password input in this FXML.
        // Use an initial demo password; production account creation should add a proper password flow.
        STORE.addUser(username, "ChangeMe123!", role);
        AppUser created = findUser(username);
        if (created != null && !fullName.equals(created.getFullName())) {
            // AppUser has no documented full-name setter in the supplied store API.
            // Keep the actual store model authoritative instead of maintaining a fake row.
        }
        refreshAccounts();
        if (accountTable != null) {
            accountTable.getSelectionModel().select(findUser(username));
        }
        showMessage(Alert.AlertType.INFORMATION, "Account created",
                "The account was added to the in-memory PortalStore with a demo initial password.");
    }

    private void handleDeleteAccount() {
        AppUser selected = accountTable == null ? null : accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showMessage(Alert.AlertType.WARNING, "No account selected", "Select an account in the table first.");
            return;
        }
        if ("admin".equalsIgnoreCase(selected.getUsername())) {
            showMessage(Alert.AlertType.WARNING, "Protected account", "The demo administrator account cannot be deleted.");
            return;
        }
        // PortalStore does not expose deleteUser(); users() returns the live Map.values() collection.
        // Removing from this backed collection removes the corresponding user from the store.
        boolean removed = STORE.users().remove(selected);
        if (removed) {
            STORE.log("admin", "Deleted account " + selected.getUsername());
            refreshAccounts();
            clearForm();
        }
    }

    private void handleAssignRole() {
        AppUser selected = accountTable == null ? null : accountTable.getSelectionModel().getSelectedItem();
        String newRole = accountRole == null ? null : accountRole.getValue();
        if (selected == null) {
            showMessage(Alert.AlertType.WARNING, "No account selected", "Select an account before assigning a role.");
            return;
        }
        if (newRole == null || newRole.isBlank()) {
            showMessage(Alert.AlertType.WARNING, "No role selected", "Choose a role first.");
            return;
        }
        if ("admin".equalsIgnoreCase(selected.getUsername()) && !"ADMIN".equals(newRole)) {
            showMessage(Alert.AlertType.WARNING, "Protected role", "The demo administrator must retain the ADMIN role.");
            return;
        }
        if (Objects.equals(selected.getRole(), newRole)) {
            return;
        }
        String username = selected.getUsername();
        String password = selected.getPassword();
        boolean removed = STORE.users().remove(selected);
        if (removed) {
            STORE.addUser(username, password, newRole);
            STORE.log("admin", "Assigned role " + newRole + " to " + username);
            refreshAccounts();
            if (accountTable != null) {
                accountTable.getSelectionModel().select(findUser(username));
            }
        }
    }

    private AppUser findUser(String username) {
        return STORE.users().stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst().orElse(null);
    }

    private void clearForm() {
        if (accountName != null) accountName.clear();
        if (accountEmail != null) accountEmail.clear();
        if (accountRole != null) accountRole.setValue("STAFF");
    }

    @FXML
    private void handleLogout() {
        navigateTo("Login.fxml");
    }

    private void navigateTo(String fxmlFile) {
        try {
            FXMLLoader loader = createLoader(fxmlFile);
            Parent root = loader.load();
            Node source = nav_Login != null ? nav_Login : nav_AdminAccounts;
            if (source == null || source.getScene() == null) {
                return;
            }
            Scene currentScene = source.getScene();
            currentScene.setRoot(root);
            Stage stage = (Stage) currentScene.getWindow();
            stage.sizeToScene();
        } catch (IOException exception) {
            showMessage(Alert.AlertType.ERROR, "Navigation failed",
                    "Could not load " + fxmlFile + ": " + exception.getMessage());
        }
    }

    private FXMLLoader createLoader(String fxmlFile) throws IOException {
        String[] resourcePaths = {
                "/com/example/tmver36/" + fxmlFile,
                "/" + fxmlFile
        };
        for (String resourcePath : resourcePaths) {
            java.net.URL resource = getClass().getResource(resourcePath);
            if (resource != null) {
                return new FXMLLoader(resource);
            }
        }
        throw new IOException("FXML resource not found in the classpath: " + fxmlFile);
    }

    private void showMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
