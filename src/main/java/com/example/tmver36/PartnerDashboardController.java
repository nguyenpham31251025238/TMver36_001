package com.example.tmver36;

import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Partner shared-profiles screen backed by PortalStore and Shopee-inspired Profile Cards (PartnerDashboard.fxml). */
public class PartnerDashboardController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_PartnerDashboard;
    @FXML private Button nav_PartnerProfile;
    @FXML private Button nav_PartnerInterview;
    @FXML private Button nav_Login;
    @FXML private Button nav_PartnerProfile__2;
    @FXML private Button nav_PartnerProfile__3;
    @FXML private Button nav_PartnerProfile__4;
    @FXML private Button nav_PartnerInterview__2;

    @FXML private TextField grantedSearch;
    @FXML private ComboBox<String> nationalityFilter;
    @FXML private ComboBox<String> experienceFilter;
    @FXML private MenuButton certificateMenuButton;
    @FXML private ComboBox<String> subjectFilter;
    @FXML private ComboBox<String> salaryFilter;
    @FXML private Button searchGrantedButton;
    @FXML private Button clearPartnerFiltersButton;
    @FXML private Label countNote;
    @FXML private FlowPane partnerCardGrid;

    private final Set<String> selectedCertificates = new LinkedHashSet<>();
    private final List<CheckMenuItem> certItems = new ArrayList<>();
    private boolean updatingNationality;

    @FXML
    private void initialize() {
        configureFilters();
        bindNavigation();

        if (searchGrantedButton != null) searchGrantedButton.setOnAction(e -> applySearch());
        if (clearPartnerFiltersButton != null) clearPartnerFiltersButton.setOnAction(e -> resetFilters());
        if (grantedSearch != null) {
            grantedSearch.setOnAction(e -> applySearch());
            grantedSearch.textProperty().addListener((obs, oldV, newV) -> applySearch());
        }
        if (experienceFilter != null) experienceFilter.setOnAction(e -> applySearch());
        if (subjectFilter != null) subjectFilter.setOnAction(e -> applySearch());
        if (salaryFilter != null) salaryFilter.setOnAction(e -> applySearch());

        applySearch();
    }

    private void configureFilters() {
        if (nationalityFilter != null) {
            nationalityFilter.getItems().setAll(CatalogData.NATIONALITY_FILTER_OPTIONS);
            nationalityFilter.setValue("All");
            nationalityFilter.setEditable(true);
            if (nationalityFilter.getEditor() != null) {
                nationalityFilter.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                    if (updatingNationality) return;
                    updatingNationality = true;
                    try {
                        nationalityFilter.getItems().setAll(CatalogData.filterCountries(newText, true));
                        nationalityFilter.getEditor().setText(newText);
                    } finally {
                        updatingNationality = false;
                    }
                    applySearch();
                });
            }
            nationalityFilter.setOnAction(e -> applySearch());
        }
        if (experienceFilter != null) {
            experienceFilter.getItems().setAll(CatalogData.EXPERIENCE_FILTER_OPTIONS);
            experienceFilter.setValue("All");
        }
        if (subjectFilter != null) {
            subjectFilter.getItems().setAll(CatalogData.MAJOR_FILTER_OPTIONS);
            subjectFilter.setValue("All");
        }
        if (salaryFilter != null) {
            salaryFilter.getItems().setAll(CatalogData.EXPECTED_SALARY_FILTER_OPTIONS);
            salaryFilter.setValue("All");
        }
        if (certificateMenuButton != null) {
            certificateMenuButton.getItems().clear();
            certItems.clear();
            for (String cert : CatalogData.CERTIFICATE_OPTIONS) {
                CheckMenuItem item = new CheckMenuItem(cert);
                item.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    if (isSelected) selectedCertificates.add(cert);
                    else selectedCertificates.remove(cert);
                    certificateMenuButton.setText(selectedCertificates.isEmpty()
                            ? "All certificates"
                            : String.join(", ", selectedCertificates));
                    applySearch();
                });
                certItems.add(item);
                certificateMenuButton.getItems().add(item);
            }
        }
    }

    private void resetFilters() {
        if (grantedSearch != null) grantedSearch.clear();
        if (nationalityFilter != null) {
            updatingNationality = true;
            try {
                nationalityFilter.getItems().setAll(CatalogData.NATIONALITY_FILTER_OPTIONS);
                nationalityFilter.setValue("All");
                if (nationalityFilter.getEditor() != null) nationalityFilter.getEditor().setText("All");
            } finally {
                updatingNationality = false;
            }
        }
        if (experienceFilter != null) experienceFilter.setValue("All");
        if (subjectFilter != null) subjectFilter.setValue("All");
        if (salaryFilter != null) salaryFilter.setValue("All");
        selectedCertificates.clear();
        for (CheckMenuItem item : certItems) item.setSelected(false);
        if (certificateMenuButton != null) certificateMenuButton.setText("All certificates");
        applySearch();
    }

    private void bindNavigation() {
        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> applySearch());
        if (nav_PartnerProfile != null) nav_PartnerProfile.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerInterview != null) nav_PartnerInterview.setOnAction(e -> navigate("PartnerInterview.fxml"));
        if (nav_PartnerInterview__2 != null) nav_PartnerInterview__2.setOnAction(e -> navigate("PartnerInterview.fxml"));
        if (nav_Login != null) nav_Login.setOnAction(e -> logout());
        if (nav_PartnerProfile__2 != null) nav_PartnerProfile__2.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerProfile__3 != null) nav_PartnerProfile__3.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerProfile__4 != null) nav_PartnerProfile__4.setOnAction(e -> navigate("PartnerProfile.fxml"));
    }

    private String currentPartner() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "partner" : u;
    }

    private void applySearch() {
        String query = grantedSearch == null || grantedSearch.getText() == null
                ? ""
                : grantedSearch.getText().trim().toLowerCase(Locale.ROOT);
        String nationality = selectedNationality();
        String experience = selected(experienceFilter);
        String majorOrDegree = selected(subjectFilter);
        String salary = selected(salaryFilter);

        List<TeacherProfile> visible = STORE.visibleTeachers(currentPartner(), "PARTNER");
        List<TeacherProfile> matched = visible.stream().filter(t -> {
            String haystack = (safe(t.getName()) + " " + safe(t.getCertificates()) + " "
                    + safe(t.getLocation()) + " " + safe(t.getNationality()) + " "
                    + safe(t.getDegree()) + " " + safe(t.getMajor()) + " " + safe(t.getExpectedSalary()))
                    .toLowerCase(Locale.ROOT);
            if (!query.isBlank() && !haystack.contains(query)) return false;
            if (!isAll(nationality) && !safe(t.getNationality()).toLowerCase(Locale.ROOT).contains(nationality.toLowerCase(Locale.ROOT))) return false;
            if (!CatalogData.matchesExperienceFilter(t.getExperience(), experience)) return false;
            if (!selectedCertificates.isEmpty() && !CatalogData.matchesCertificatesFilter(t.getCertificates(), selectedCertificates)) return false;
            if (!CatalogData.matchesMajorOrDegreeFilter(t.getDegree(), safe(t.getMajor()).isBlank() ? t.getSubject() : t.getMajor(), majorOrDegree)) return false;
            if (!CatalogData.matchesSalaryFilter(t.getExpectedSalary(), salary)) return false;
            return true;
        }).collect(Collectors.toList());

        renderPartnerCards(matched);
        if (countNote != null) {
            countNote.setText("Showing " + matched.size() + " of " + visible.size() + " shared teacher profile(s) available to this partner.");
        }
    }

    private void renderPartnerCards(List<TeacherProfile> teachers) {
        if (partnerCardGrid == null) return;
        partnerCardGrid.getChildren().clear();

        String partner = currentPartner();
        for (TeacherProfile teacher : teachers) {
            String[] eval = STORE.partnerEvaluation(partner, teacher.getId());
            String evalBadge = (eval != null && eval.length > 0 && eval[0] != null) ? eval[0] : "";
            VBox card = ProfileCardBuilder.buildPartnerTeacherCard(
                    teacher,
                    evalBadge,
                    () -> {
                        PartnerProfileController.setSelectedTeacherId(teacher.getId());
                        navigate("PartnerProfile.fxml");
                    },
                    () -> openDoc(teacher, "CV"),
                    () -> openDoc(teacher, "Intro video"),
                    () -> {
                        PartnerInterviewController.setPreselectedTeacherId(teacher.getId());
                        navigate("PartnerInterview.fxml");
                    }
            );
            partnerCardGrid.getChildren().add(card);
        }

        if (teachers.isEmpty()) {
            Label empty = new Label("No shared teacher profiles match the selected filters.");
            empty.getStyleClass().add("muted");
            VBox box = new VBox(empty);
            box.getStyleClass().add("table-row");
            partnerCardGrid.getChildren().add(box);
        }
    }

    private void openDoc(TeacherProfile teacher, String category) {
        TeacherDocument doc = ProfileCardBuilder.findDocumentByCategory(STORE, teacher.getId(), category);
        if (doc == null || !STORE.openDocument(currentPartner(), doc)) {
            LoginController.showAlert(Alert.AlertType.INFORMATION, "Document Preview",
                    "No " + category + " file is currently available for " + teacher.getName() + ".");
        }
    }

    private String selectedNationality() {
        if (nationalityFilter == null) return "All";
        if (nationalityFilter.isEditable() && nationalityFilter.getEditor() != null) {
            String txt = nationalityFilter.getEditor().getText();
            if (txt != null && !txt.isBlank()) return txt.trim();
        }
        Object v = nationalityFilter.getValue();
        return v == null ? "All" : String.valueOf(v).trim();
    }

    private String selected(ComboBox<String> combo) {
        return combo == null || combo.getValue() == null ? "All" : combo.getValue().trim();
    }

    private boolean isAll(String value) {
        return value == null || value.isBlank() || value.equalsIgnoreCase("All");
    }

    private void navigate(String fxml) {
        Node source = nav_PartnerDashboard;
        if (source != null && !LoginController.navigateTo(source, fxml)) {
            showError("Navigation failed", "Could not load " + fxml + ".");
        }
    }

    private void logout() {
        if (nav_Login != null && !LoginController.navigateTo(nav_Login, "Login.fxml")) {
            showError("Navigation failed", "Could not load Login.fxml.");
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showError(String title, String message) {
        LoginController.showAlert(Alert.AlertType.ERROR, title, message);
    }
}
