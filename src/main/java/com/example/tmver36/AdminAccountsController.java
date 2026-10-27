package com.example.tmver36;

import com.example.tmver36.model.AppUser;
import com.example.tmver36.service.PortalStore;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.util.List;
import java.util.Locale;

/** Admin account management controller. */
public class AdminAccountsController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_AdminAccounts;
    @FXML private Button nav_AdminLogs;
    @FXML private Button nav_Login;

    @FXML private javafx.scene.layout.VBox searchField;
    @FXML private TextField accountSearch;
    @FXML private TextField searchAccountField;
    @FXML private ComboBox<String> filterStatusCombo;
    @FXML private Button searchAccountsButton;
    @FXML private Button searchAccountButton;

    @FXML private TableView<AccountRow> accountTable;
    @FXML private TableColumn<AccountRow, String> colUsername;
    @FXML private TableColumn<AccountRow, String> colEmail;
    @FXML private TableColumn<AccountRow, String> colRole;
    @FXML private TableColumn<AccountRow, String> colStatus;

    @FXML private TextField accountName;
    @FXML private TextField accountEmail;
    @FXML private ComboBox<String> accountRole;
    @FXML private ComboBox<String> accountStatus;
    @FXML private PasswordField accountPassword;
    @FXML private Button deleteAccountButton;
    @FXML private Button saveAccountButton;
    @FXML private Button assignRoleButton;
    @FXML private Button updateRoleButton;

    private ComboBox<String> filterRoleCombo;
    private ComboBox<String> assignAccountCombo;
    private ComboBox<String> assignNewRoleCombo;

    private final ObservableList<AccountRow> masterRows = FXCollections.observableArrayList();
    private final ObservableList<AccountRow> filteredRows = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        resolveUnidentifiedCombos();
        configureTableColumns();
        Parent root = FxNodes.rootOf(nav_AdminAccounts, nav_AdminLogs, accountTable);
        ProfileCardBuilder.decorateScreen(root, STORE, () -> {
            loadAccountsFromStore();
            populateAssignAccountCombo();
        });
        loadAccountsFromStore();
        populateAssignAccountCombo();

        if (accountTable != null) {
            accountTable.setItems(filteredRows);
            accountTable.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> {
                if (newRow != null) {
                    populateForm(newRow);
                }
            });
            if (!filteredRows.isEmpty()) {
                accountTable.getSelectionModel().selectFirst();
            }
        }

        TextField searchInput = activeSearchField();
        Button searchBtn = searchAccountsButton != null ? searchAccountsButton : searchAccountButton;
        Button roleBtn = assignRoleButton != null ? assignRoleButton : updateRoleButton;

        if (searchBtn != null) searchBtn.setOnAction(e -> applyFilters());
        if (searchInput != null) {
            searchInput.setOnAction(e -> applyFilters());
            searchInput.textProperty().addListener((obs, o, n) -> applyFilters());
        }
        if (filterStatusCombo != null) filterStatusCombo.setOnAction(e -> applyFilters());
        if (filterRoleCombo != null) filterRoleCombo.setOnAction(e -> applyFilters());

        if (saveAccountButton != null) saveAccountButton.setOnAction(e -> handleSaveAccount());
        if (deleteAccountButton != null) deleteAccountButton.setOnAction(e -> handleDeleteAccount());
        if (roleBtn != null) roleBtn.setOnAction(e -> handleUpdateRole());

        if (nav_AdminAccounts != null) nav_AdminAccounts.setOnAction(e -> {
            loadAccountsFromStore();
            populateAssignAccountCombo();
        });
        if (nav_AdminLogs != null) nav_AdminLogs.setOnAction(e -> LoginController.navigateTo(nav_AdminLogs, "AdminLogs.fxml"));
        if (nav_Login != null) nav_Login.setOnAction(e -> handleLogout());
    }

    private TextField activeSearchField() {
        return accountSearch != null ? accountSearch : searchAccountField;
    }

    @FXML
    private void handleSearch() {
        applyFilters();
    }

    @FXML
    private void handleLogout() {
        LoginController.navigateTo(nav_Login != null ? nav_Login : nav_AdminAccounts, "Login.fxml");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void resolveUnidentifiedCombos() {
        Parent root = FxNodes.rootOf(nav_AdminAccounts, saveAccountButton, assignRoleButton, updateRoleButton, accountTable);
        if (root == null) return;
        for (ComboBox combo : FxNodes.findAll(root, ComboBox.class)) {
            if (combo == filterStatusCombo || combo == accountRole || combo == accountStatus) {
                continue;
            }
            List<?> items = combo.getItems();
            String joined = String.valueOf(items);
            if (joined.contains("All") && (joined.contains("Admin") || joined.contains("System Admin")) && filterRoleCombo == null) {
                filterRoleCombo = combo;
            } else if ((joined.contains("admin ·") || joined.contains("Emily Martin") || joined.contains("Linh Anh")) && assignAccountCombo == null) {
                assignAccountCombo = combo;
            } else if ((joined.contains("Admin") || joined.contains("System Admin")) && joined.contains("Matching Staff") && assignNewRoleCombo == null) {
                assignNewRoleCombo = combo;
            }
        }
    }

    private void configureTableColumns() {
        if (colUsername != null) colUsername.setCellValueFactory(data -> data.getValue().usernameProperty());
        if (colEmail != null) colEmail.setCellValueFactory(data -> data.getValue().emailProperty());
        if (colRole != null) colRole.setCellValueFactory(data -> data.getValue().roleProperty());
        if (colStatus != null) colStatus.setCellValueFactory(data -> data.getValue().statusProperty());
    }

    private void loadAccountsFromStore() {
        masterRows.clear();
        for (AppUser user : STORE.users()) {
            String displayName = user.getFullName() == null || user.getFullName().isBlank()
                    ? user.getUsername() : user.getFullName();
            String status = user.isActive() ? "Active" : "Disabled";
            masterRows.add(new AccountRow(user.getUsername(), displayName, toDisplayRole(user.getRole()), status));
        }
        applyFilters();
    }

    private void populateAssignAccountCombo() {
        if (assignAccountCombo == null) return;
        String current = assignAccountCombo.getValue();
        List<String> items = STORE.users().stream()
                .map(u -> u.getUsername() + " · " + toDisplayRole(u.getRole()))
                .toList();
        assignAccountCombo.getItems().setAll(items);
        if (current != null && items.contains(current)) {
            assignAccountCombo.setValue(current);
        } else if (!items.isEmpty()) {
            assignAccountCombo.getSelectionModel().selectFirst();
        }
    }

    private void applyFilters() {
        TextField searchInput = activeSearchField();
        String keyword = searchInput == null || searchInput.getText() == null
                ? ""
                : searchInput.getText().trim().toLowerCase(Locale.ROOT);
        String statusFilter = filterStatusCombo == null ? "All" : filterStatusCombo.getValue();
        String roleFilter = filterRoleCombo == null ? "All" : filterRoleCombo.getValue();

        filteredRows.setAll(masterRows.stream().filter(row -> {
            boolean matchesKeyword = keyword.isEmpty()
                    || row.getUsername().toLowerCase(Locale.ROOT).contains(keyword)
                    || row.getEmail().toLowerCase(Locale.ROOT).contains(keyword)
                    || row.getRole().toLowerCase(Locale.ROOT).contains(keyword);
            boolean matchesStatus = statusFilter == null
                    || "All".equalsIgnoreCase(statusFilter)
                    || row.getStatus().equalsIgnoreCase(statusFilter);
            boolean matchesRole = roleFilter == null
                    || "All".equalsIgnoreCase(roleFilter)
                    || toInternalRole(row.getRole()).equalsIgnoreCase(toInternalRole(roleFilter));
            return matchesKeyword && matchesStatus && matchesRole;
        }).toList());
    }

    private void populateForm(AccountRow row) {
        if (accountName != null) accountName.setText(row.getUsername());
        if (accountEmail != null) accountEmail.setText(row.getEmail());
        if (accountRole != null) accountRole.setValue(row.getRole());
        if (accountStatus != null) accountStatus.setValue(row.getStatus());
        if (accountPassword != null) accountPassword.clear();
    }

    private void handleSaveAccount() {
        String username = accountName == null || accountName.getText() == null ? "" : accountName.getText().trim();
        String displayName = accountEmail == null || accountEmail.getText() == null ? "" : accountEmail.getText().trim();
        String password = accountPassword == null || accountPassword.getText() == null ? "" : accountPassword.getText();
        String roleLabel = accountRole == null ? null : accountRole.getValue();
        String statusLabel = accountStatus == null || accountStatus.getValue() == null ? "Active" : accountStatus.getValue();

        if (username.isEmpty() || displayName.isEmpty() || roleLabel == null) {
            showError("Incomplete Account Info", "Username, display name, and role are required.");
            return;
        }

        boolean exists = STORE.userExists(username);
        if (!exists && password.trim().length() < 6) {
            showError("Weak Password", "Please set an initial password of at least 6 characters for new accounts.");
            return;
        }
        if (exists && !password.isEmpty() && password.trim().length() < 6) {
            showError("Weak Password", "When changing a password, use at least 6 characters.");
            return;
        }

        String internalRole = toInternalRole(roleLabel);
        boolean active = !"Disabled".equalsIgnoreCase(statusLabel);
        String actor = currentAdminUsername();

        if (!STORE.saveOrUpdateUser(actor, username, displayName, internalRole, password, active)) {
            showError("Save Rejected", "Could not save account changes (primary admin cannot be demoted or disabled).");
            return;
        }

        if (accountPassword != null) accountPassword.clear();
        loadAccountsFromStore();
        populateAssignAccountCombo();
        showInfo("Account Saved", (exists ? "Updated" : "Created") + " account '" + username + "'.");
    }

    private void handleDeleteAccount() {
        AccountRow selected = accountTable == null ? null : accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("No Selection", "Please select an account in the table before deleting.");
            return;
        }
        if ("admin".equalsIgnoreCase(selected.getUsername())) {
            showError("Protected Account", "The primary admin account cannot be deleted.");
            return;
        }
        if (!STORE.deleteUser(currentAdminUsername(), selected.getUsername())) {
            showError("Delete Denied", "You do not have permission to delete this account.");
            return;
        }
        loadAccountsFromStore();
        populateAssignAccountCombo();
        showInfo("Account Deleted", "Removed account '" + selected.getUsername() + "'.");
    }

    private void handleUpdateRole() {
        String targetUsername = null;
        if (assignAccountCombo != null && assignAccountCombo.getValue() != null) {
            String raw = assignAccountCombo.getValue();
            targetUsername = raw.contains("·") ? raw.split("·")[0].trim() : raw.trim();
        }
        AccountRow selected = accountTable == null ? null : accountTable.getSelectionModel().getSelectedItem();
        if ((targetUsername == null || targetUsername.isEmpty()) && selected != null) {
            targetUsername = selected.getUsername();
        }
        String newRoleLabel = assignNewRoleCombo != null && assignNewRoleCombo.getValue() != null
                ? assignNewRoleCombo.getValue()
                : (accountRole == null ? null : accountRole.getValue());

        if (targetUsername == null || targetUsername.isEmpty() || newRoleLabel == null) {
            showError("Cannot Update Role", "Select an account and a target role first.");
            return;
        }
        String internalRole = toInternalRole(newRoleLabel);
        if (!STORE.updateUserRole(currentAdminUsername(), targetUsername, internalRole)) {
            showError("Role Update Denied", "Could not change role for '" + targetUsername + "'.");
            return;
        }
        loadAccountsFromStore();
        populateAssignAccountCombo();
        showInfo("Role Updated", "Updated role for '" + targetUsername + "' to " + newRoleLabel + ".");
    }

    private String currentAdminUsername() {
        String username = LoginController.getCurrentUsername();
        return (username == null || username.isBlank()) ? "admin" : username;
    }

    private String toDisplayRole(String internalRole) {
        if (internalRole == null) return "Teacher";
        return switch (internalRole) {
            case "ADMIN" -> "System Admin";
            case "STAFF" -> "Matching Staff";
            case "PARTNER" -> "Partner";
            case "TEACHER" -> "Teacher";
            default -> internalRole;
        };
    }

    private String toInternalRole(String roleLabel) {
        if (roleLabel == null) return "TEACHER";
        return switch (roleLabel.trim()) {
            case "System Admin", "Admin", "ADMIN" -> "ADMIN";
            case "Matching Staff", "STAFF" -> "STAFF";
            case "Partner", "PARTNER" -> "PARTNER";
            default -> "TEACHER";
        };
    }

    private void showError(String title, String message) {
        LoginController.showAlert(Alert.AlertType.ERROR, title, message);
    }

    private void showInfo(String title, String message) {
        LoginController.showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    public static class AccountRow {
        private final SimpleStringProperty username;
        private final SimpleStringProperty email;
        private final SimpleStringProperty role;
        private final SimpleStringProperty status;

        public AccountRow(String username, String email, String role, String status) {
            this.username = new SimpleStringProperty(username);
            this.email = new SimpleStringProperty(email);
            this.role = new SimpleStringProperty(role);
            this.status = new SimpleStringProperty(status);
        }

        public String getUsername() { return username.get(); }
        public void setUsername(String value) { username.set(value); }
        public SimpleStringProperty usernameProperty() { return username; }

        public String getEmail() { return email.get(); }
        public void setEmail(String value) { email.set(value); }
        public SimpleStringProperty emailProperty() { return email; }

        public String getRole() { return role.get(); }
        public void setRole(String value) { role.set(value); }
        public SimpleStringProperty roleProperty() { return role; }

        public String getStatus() { return status.get(); }
        public void setStatus(String value) { status.set(value); }
        public SimpleStringProperty statusProperty() { return status; }
    }
}
