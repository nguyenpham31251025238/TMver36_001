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
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Staff teacher-pool dashboard using the shared PortalStore data. */
public class StaffDashboardController {
    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;
    @FXML private Button nav_StaffReview__2;
    @FXML private TextField profileSearch;
    @FXML private Button filterProfilesButton;
    @FXML private Button nav_StaffReview__3;
    @FXML private Button nav_StaffAccess__2;
    @FXML private Button nav_PartnerProfile;
    @FXML private Button nav_StaffReview__4;

    private ComboBox<?> nationalityFilter;
    private ComboBox<?> statusFilter;
    private ComboBox<?> experienceFilter;
    private ComboBox<?> certificateFilter;
    private ComboBox<?> locationFilter;
    private ComboBox<?> salaryFilter;
    private VBox tableContainer;
    private Label countLabel;
    private List<TeacherProfile> filteredTeachers = new ArrayList<>();

    @FXML
    private void initialize() {
        Parent root = nav_StaffDashboard == null ? null : nav_StaffDashboard.getScene() == null
                ? null : nav_StaffDashboard.getScene().getRoot();
        if (root == null && nav_StaffDashboard != null) {
            Node node = nav_StaffDashboard;
            while (node.getParent() != null) node = node.getParent();
            if (node instanceof Parent parent) root = parent;
        }
        if (root == null) return;

        findFilterControls(root);
        findTable(root);
        bindNavigation();
        if (filterProfilesButton != null) filterProfilesButton.setOnAction(e -> applyFilters());
        if (profileSearch != null) profileSearch.setOnAction(e -> applyFilters());
        populateTable(STORE.teachers());
        applyFilters();
    }

    private void findFilterControls(Parent root) {
        List<ComboBox<?>> combos = root.lookupAll("ComboBox").stream()
                .filter(ComboBox.class::isInstance).map(n -> (ComboBox<?>) n).collect(Collectors.toList());
        for (ComboBox<?> combo : combos) {
            String label = labelFor(combo).toLowerCase(Locale.ROOT);
            if (label.contains("nationality")) nationalityFilter = combo;
            else if (label.equals("status")) statusFilter = combo;
            else if (label.contains("experience")) experienceFilter = combo;
            else if (label.contains("certificates")) certificateFilter = combo;
            else if (label.contains("location")) locationFilter = combo;
            else if (label.contains("expected salary")) salaryFilter = combo;
        }
    }

    private String labelFor(Node node) {
        if (node.getParent() instanceof VBox box) {
            for (Node child : box.getChildren()) {
                if (child instanceof Label label) return label.getText() == null ? "" : label.getText();
            }
        }
        return "";
    }

    private void findTable(Parent root) {
        for (Node node : root.lookupAll(".table")) {
            if (node instanceof VBox box && box.getChildren().stream().anyMatch(c -> c instanceof HBox h && h.getStyleClass().contains("table-header"))) {
                tableContainer = box;
                break;
            }
        }
        for (Node node : root.lookupAll("Label")) {
            if (node instanceof Label label && label.getText() != null && label.getText().contains("mock profiles")) {
                countLabel = label;
                break;
            }
        }
    }

    private void bindNavigation() {
        if (nav_StaffDashboard != null) nav_StaffDashboard.setOnAction(e -> applyFilters());
        if (nav_StaffReview != null) nav_StaffReview.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview));
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> navigate("InterviewWorkspace.fxml", nav_InterviewWorkspace));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
        if (nav_StaffReview__2 != null) nav_StaffReview__2.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview__2));
    }

    private void applyFilters() {
        String query = profileSearch == null || profileSearch.getText() == null ? "" : profileSearch.getText().trim().toLowerCase(Locale.ROOT);
        String nationality = selected(nationalityFilter);
        String status = selected(statusFilter);
        String experience = selected(experienceFilter);
        String certificate = selected(certificateFilter);
        String location = selected(locationFilter);
        String salary = selected(salaryFilter);

        filteredTeachers = STORE.teachers().stream().filter(t -> {
            String name = property(t, "getName", "getFullName");
            String nation = property(t, "getNationality", "getCountry");
            String exp = property(t, "getExperience", "getYearsExperience");
            String certs = property(t, "getCertificates", "getQualification");
            String city = property(t, "getLocation", "getCity");
            String state = property(t, "getStatus");
            String haystack = (name + " " + nation + " " + exp + " " + certs + " " + city + " " + state).toLowerCase(Locale.ROOT);
            if (!query.isBlank() && !haystack.contains(query)) return false;
            if (!isAll(nationality) && !nation.toLowerCase(Locale.ROOT).contains(nationality.toLowerCase(Locale.ROOT))) return false;
            if (!isAll(status) && !statusMatches(state, status)) return false;
            if (!isAll(experience) && !experienceMatches(exp, experience)) return false;
            if (!isAll(certificate) && !certs.toLowerCase(Locale.ROOT).contains(certificate.toLowerCase(Locale.ROOT))) return false;
            if (!isAll(location) && !normalize(city).contains(normalize(location))) return false;
            // The current TeacherProfile model has no salary field; don't falsely filter records by salary.
            return true;
        }).collect(Collectors.toList());
        populateTable(filteredTeachers);
        if (countLabel != null) countLabel.setText("Showing " + filteredTeachers.size() + " teacher profile(s)");
    }

    private void populateTable(List<TeacherProfile> teachers) {
        if (tableContainer == null || tableContainer.getChildren().isEmpty()) return;
        Node header = tableContainer.getChildren().stream()
                .filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header"))
                .findFirst().orElse(null);
        tableContainer.getChildren().clear();
        if (header != null) tableContainer.getChildren().add(header);
        for (TeacherProfile teacher : teachers) tableContainer.getChildren().add(makeRow(teacher));
        if (teachers.isEmpty()) {
            HBox empty = new HBox(new Label("No teacher profiles match the selected filters."));
            empty.getStyleClass().add("table-row");
            tableContainer.getChildren().add(empty);
        }
    }

    private HBox makeRow(TeacherProfile teacher) {
        HBox row = new HBox(0);
        row.getStyleClass().add("table-row");
        String name = property(teacher, "getName", "getFullName");
        String certs = property(teacher, "getCertificates", "getQualification");
        String city = property(teacher, "getLocation", "getCity");
        String nationality = property(teacher, "getNationality", "getCountry");
        String exp = property(teacher, "getExperience", "getYearsExperience");
        String status = property(teacher, "getStatus");
        row.getChildren().add(cell(name + (certs.isBlank() ? "" : " · " + certs) + (city.isBlank() ? "" : " · " + city), "260"));
        row.getChildren().add(cell(nationality, "190"));
        row.getChildren().add(cell(exp, "155"));
        VBox statusCell = new VBox(new Label(status.isBlank() ? "Unknown" : status));
        statusCell.setMinWidth(185); statusCell.setPrefWidth(185); statusCell.setMaxWidth(185);
        statusCell.getStyleClass().add("table-cell");
        row.getChildren().add(statusCell);
        VBox actions = new VBox(6);
        actions.setMinWidth(190); actions.setPrefWidth(190); actions.setMaxWidth(190);
        Button review = new Button("Review");
        review.getStyleClass().add("primary");
        review.setOnAction(e -> navigate("StaffReview.fxml", review));
        Button grant = new Button("Grant access");
        grant.getStyleClass().add("secondary");
        grant.setOnAction(e -> grantAccess(teacher));
        actions.getChildren().addAll(review, grant);
        row.getChildren().add(actions);
        return row;
    }

    private VBox cell(String value, String width) {
        double w = Double.parseDouble(width);
        Label label = new Label(value == null || value.isBlank() ? "—" : value);
        label.getStyleClass().add("table-text");
        label.setWrapText(true);
        VBox cell = new VBox(label);
        cell.setMinWidth(w); cell.setPrefWidth(w); cell.setMaxWidth(w);
        return cell;
    }

    private void grantAccess(TeacherProfile teacher) {
        String partnerUsername = STORE.userExists("partner") ? "partner" : STORE.users().stream()
                .filter(user -> "PARTNER".equalsIgnoreCase(user.getRole()))
                .map(user -> user.getUsername()).findFirst().orElse(null);
        if (partnerUsername == null) {
            showError("No partner account", "Create a PARTNER account before granting profile access.");
            return;
        }
        STORE.grant(partnerUsername, teacher.getId());
        if (nav_StaffDashboard != null) {
            nav_StaffDashboard.setText("Teacher Profile Pool · Access granted");
        }
        if (countLabel != null) countLabel.setText("Granted " + property(teacher, "getName", "getFullName") + " to partner account " + partnerUsername + ".");
    }

    private String selected(ComboBox<?> combo) { return combo == null || combo.getValue() == null ? "All" : String.valueOf(combo.getValue()); }
    private boolean isAll(String value) { return value == null || value.isBlank() || value.equalsIgnoreCase("All"); }
    private boolean statusMatches(String actual, String requested) {
        String a = normalize(actual), r = normalize(requested);
        if (r.equals("ready")) return a.equals("ready");
        if (r.equals("pending")) return a.equals("pending");
        if (r.equals("update required")) return a.contains("update") || a.contains("unqualified");
        return a.contains(r);
    }
    private boolean experienceMatches(String actual, String requested) {
        String digits = actual == null ? "" : actual.replaceAll("[^0-9]", " ").trim();
        int years = 0;
        try { if (!digits.isBlank()) years = Integer.parseInt(digits.split("\\s+")[0]); } catch (NumberFormatException ignored) { }
        return switch (requested.toLowerCase(Locale.ROOT)) {
            case "0 - 2 years" -> years <= 2;
            case "3 - 5 years" -> years >= 3 && years <= 5;
            case "over 5 years" -> years > 5;
            default -> true;
        };
    }
    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(" ", "").replace("city", ""); }
    private String property(Object target, String... getters) {
        if (target == null) return "";
        for (String getter : getters) try {
            Method method = target.getClass().getMethod(getter);
            Object value = method.invoke(target);
            if (value != null) return String.valueOf(value);
        } catch (ReflectiveOperationException ignored) { }
        return "";
    }
    private void navigate(String fxml, Node source) {
        if (!LoginController.navigateTo(source, fxml)) showError("Navigation failed", "Could not load " + fxml + ". Check the FXML resource path.");
    }
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }
}
