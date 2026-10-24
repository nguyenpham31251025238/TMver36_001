package com.example.tmver36;

import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Staff teacher-pool dashboard using the shared PortalStore data (StaffDashboard.fxml). */
public class StaffDashboardController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;
    @FXML private Button nav_StaffReview__2;
    @FXML private Button nav_StaffReview__3;
    @FXML private Button nav_StaffReview__4;
    @FXML private Button nav_StaffAccess__2;
    @FXML private Button nav_PartnerProfile;

    @FXML private TextField profileSearch;
    @FXML private ComboBox<String> nationalityFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> experienceFilter;
    @FXML private ComboBox<String> certificateFilter;
    @FXML private ComboBox<String> locationFilter;
    @FXML private ComboBox<String> salaryFilter;
    @FXML private ComboBox<String> partnerAccessSelector;
    @FXML private Button filterProfilesButton;
    @FXML private VBox tableContainer;

    private Label countLabel;
    private List<TeacherProfile> filteredTeachers = new ArrayList<>();

    @FXML
    private void initialize() {
        Parent root = FxNodes.rootOf(nav_StaffDashboard, filterProfilesButton, tableContainer, profileSearch);
        if (root != null) {
            if (tableContainer == null) {
                for (VBox box : FxNodes.findAllByStyleClass(root, "table", VBox.class)) {
                    if (box.getChildren().stream().anyMatch(c -> c instanceof HBox h && h.getStyleClass().contains("table-header"))) {
                        tableContainer = box;
                        break;
                    }
                }
            }
            for (Label label : FxNodes.findAll(root, Label.class)) {
                if (label.getText() != null && label.getText().contains("mock profiles")) {
                    countLabel = label;
                    break;
                }
            }
        }

        configurePartnerSelector();
        bindNavigation();

        if (filterProfilesButton != null) filterProfilesButton.setOnAction(e -> applyFilters());
        if (profileSearch != null) profileSearch.setOnAction(e -> applyFilters());
        if (nationalityFilter != null) nationalityFilter.setOnAction(e -> applyFilters());
        if (statusFilter != null) statusFilter.setOnAction(e -> applyFilters());
        if (experienceFilter != null) experienceFilter.setOnAction(e -> applyFilters());
        if (certificateFilter != null) certificateFilter.setOnAction(e -> applyFilters());
        if (locationFilter != null) locationFilter.setOnAction(e -> applyFilters());
        if (salaryFilter != null) salaryFilter.setOnAction(e -> applyFilters());
        if (partnerAccessSelector != null) {
            partnerAccessSelector.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        }

        applyFilters();
    }

    private void configurePartnerSelector() {
        if (partnerAccessSelector == null) return;
        List<String> partners = STORE.users().stream()
                .filter(user -> "PARTNER".equalsIgnoreCase(user.getRole()))
                .map(user -> user.getUsername())
                .toList();
        partnerAccessSelector.getItems().setAll(partners);
        if (!partners.isEmpty()) {
            partnerAccessSelector.getSelectionModel().selectFirst();
        }
    }

    private void bindNavigation() {
        if (nav_StaffDashboard != null) nav_StaffDashboard.setOnAction(e -> applyFilters());
        if (nav_StaffReview != null) nav_StaffReview.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview));
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> navigate("InterviewWorkspace.fxml", nav_InterviewWorkspace));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
        if (nav_StaffReview__2 != null) nav_StaffReview__2.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview__2));
        if (nav_StaffReview__3 != null) nav_StaffReview__3.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview__3));
        if (nav_StaffReview__4 != null) nav_StaffReview__4.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview__4));
    }

    private void applyFilters() {
        String query = profileSearch == null || profileSearch.getText() == null ? "" : profileSearch.getText().trim().toLowerCase(Locale.ROOT);
        String nationality = selected(nationalityFilter);
        String status = selected(statusFilter);
        String experience = selected(experienceFilter);
        String certificate = selected(certificateFilter);
        String location = selected(locationFilter);

        filteredTeachers = STORE.teachers().stream().filter(t -> {
            String name = safe(t.getName());
            String nation = safe(t.getNationality());
            String exp = safe(t.getExperience());
            String certs = safe(t.getCertificates());
            String city = safe(t.getLocation());
            String state = safe(t.getStatus());
            String haystack = (name + " " + nation + " " + exp + " " + certs + " " + city + " " + state).toLowerCase(Locale.ROOT);
            if (!query.isBlank() && !haystack.contains(query)) return false;
            if (!isAll(nationality) && !nation.toLowerCase(Locale.ROOT).contains(nationality.toLowerCase(Locale.ROOT))) return false;
            if (!isAll(status) && !statusMatches(state, status)) return false;
            if (!isAll(experience) && !experienceMatches(exp, experience)) return false;
            if (!isAll(certificate) && !certs.toLowerCase(Locale.ROOT).contains(certificate.toLowerCase(Locale.ROOT))) return false;
            if (!isAll(location) && !normalize(city).contains(normalize(location))) return false;
            return true;
        }).collect(Collectors.toList());

        populateTable(filteredTeachers);
        if (countLabel != null) {
            countLabel.setText("Showing " + filteredTeachers.size() + " teacher profile(s)");
        }
    }

    private void populateTable(List<TeacherProfile> teachers) {
        if (tableContainer == null) return;
        Node header = tableContainer.getChildren().stream()
                .filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header"))
                .findFirst().orElse(null);
        tableContainer.getChildren().clear();
        if (header != null) tableContainer.getChildren().add(header);
        for (TeacherProfile teacher : teachers) {
            tableContainer.getChildren().add(makeRow(teacher));
        }
        if (teachers.isEmpty()) {
            HBox empty = new HBox(new Label("No teacher profiles match the selected filters."));
            empty.getStyleClass().add("table-row");
            tableContainer.getChildren().add(empty);
        }
    }

    private HBox makeRow(TeacherProfile teacher) {
        HBox row = new HBox(0);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("table-row");
        String name = safe(teacher.getName());
        String certs = safe(teacher.getCertificates());
        String city = safe(teacher.getLocation());
        String nationality = safe(teacher.getNationality());
        String exp = safe(teacher.getExperience());
        String status = safe(teacher.getStatus());

        row.getChildren().add(cell(name + (certs.isBlank() ? "" : " · " + certs) + (city.isBlank() ? "" : " · " + city), 260));
        row.getChildren().add(cell(nationality, 190));
        row.getChildren().add(cell(exp, 155));

        VBox statusCell = new VBox(new Label(status.isBlank() ? "Unknown" : status));
        statusCell.setAlignment(Pos.CENTER_LEFT);
        statusCell.setMinWidth(185);
        statusCell.setPrefWidth(185);
        statusCell.setMaxWidth(185);
        row.getChildren().add(statusCell);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setMinWidth(190);
        actions.setPrefWidth(190);
        actions.setMaxWidth(190);

        Button review = new Button("Review");
        review.getStyleClass().add("primary");
        review.setOnAction(e -> {
            StaffReviewController.setSelectedTeacherId(teacher.getId());
            navigate("StaffReview.fxml", review);
        });

        String partnerUsername = partnerAccessSelector == null ? "partner" : partnerAccessSelector.getValue();
        if (partnerUsername == null || partnerUsername.isBlank()) partnerUsername = "partner";
        final String targetPartner = partnerUsername;
        boolean accessGranted = STORE.canPartnerAccessTeacher(targetPartner, teacher.getId());
        Button grant = new Button(accessGranted ? "Revoke access" : "Grant access");
        grant.getStyleClass().add("secondary");
        grant.setOnAction(e -> toggleAccess(teacher, targetPartner));
        actions.getChildren().addAll(review, grant);
        row.getChildren().add(actions);
        return row;
    }

    private VBox cell(String value, double width) {
        Label label = new Label(value == null || value.isBlank() ? "—" : value);
        label.getStyleClass().add("table-text");
        label.setWrapText(true);
        VBox cell = new VBox(label);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        return cell;
    }

    private void toggleAccess(TeacherProfile teacher, String partnerUsername) {
        String actor = currentActor();
        if (STORE.canPartnerAccessTeacher(partnerUsername, teacher.getId())) {
            STORE.revoke(actor, partnerUsername, teacher.getId());
        } else if (!STORE.grant(actor, partnerUsername, teacher.getId())) {
            showError("Access update failed", "Only Matching staff or an administrator can change partner access.");
            return;
        }
        applyFilters();
    }

    private String currentActor() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "staff" : u;
    }

    private String selected(ComboBox<?> combo) {
        return combo == null || combo.getValue() == null ? "All" : String.valueOf(combo.getValue());
    }

    private boolean isAll(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("All");
    }

    private boolean statusMatches(String actual, String requested) {
        String a = normalize(actual), r = normalize(requested);
        if (r.equals("ready")) return a.equals("ready");
        if (r.equals("pending")) return a.equals("pending");
        if (r.equals("update required")) return a.contains("update") || a.contains("unqualified") || a.contains("need");
        return a.contains(r);
    }

    private boolean experienceMatches(String actual, String requested) {
        String digits = actual == null ? "" : actual.replaceAll("[^0-9]", " ").trim();
        int years = 0;
        try {
            if (!digits.isBlank()) years = Integer.parseInt(digits.split("\\s+")[0]);
        } catch (NumberFormatException ignored) {}
        return switch (requested.toLowerCase(Locale.ROOT)) {
            case "0 - 2 years" -> years <= 2;
            case "3 - 5 years" -> years >= 3 && years <= 5;
            case "over 5 years" -> years > 5;
            default -> true;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "").replace("city", "");
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
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
