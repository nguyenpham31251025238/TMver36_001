package com.example.tmver36;

import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Staff teacher-pool dashboard using live PortalStore data and Shopee-inspired Profile Cards (StaffDashboard.fxml). */
public class StaffDashboardController {

    private static final PortalStore STORE = LoginController.STORE;
    private static final int PAGE_SIZE = 6;

    @FXML private Button nav_StaffDashboard;
    @FXML private Button nav_StaffReview;
    @FXML private Button nav_InterviewWorkspace;
    @FXML private Button nav_Login;
    @FXML private Button nav_StaffReview__2;
    @FXML private Button syncCloudButton;

    @FXML private Label todayDateLabel;
    @FXML private Label readyMetricLabel;
    @FXML private Label readySubLabel;
    @FXML private Label pendingMetricLabel;
    @FXML private Label pendingSubLabel;
    @FXML private Label interviewMetricLabel;
    @FXML private Label interviewSubLabel;

    @FXML private TextField profileSearch;
    @FXML private ComboBox<String> nationalityFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> experienceFilter;
    @FXML private MenuButton certificateMenuButton;
    @FXML private ComboBox<String> certificateFilter;
    @FXML private ComboBox<String> locationFilter; // Major / Degree filter (backward-compatible fx:id)
    @FXML private ComboBox<String> salaryFilter;
    @FXML private ComboBox<String> shortlistFilter;
    @FXML private ComboBox<String> partnerAccessSelector;
    @FXML private Button filterProfilesButton;
    @FXML private Button clearFiltersButton;
    @FXML private Label countLabel;
    @FXML private VBox tableContainer;
    @FXML private FlowPane cardGridPane;
    @FXML private Button prevPageButton;
    @FXML private Button nextPageButton;
    @FXML private Label pageInfoLabel;

    private final Set<String> selectedCertificates = new LinkedHashSet<>();
    private final List<CheckMenuItem> certMenuItems = new ArrayList<>();
    private List<TeacherProfile> filteredTeachers = new ArrayList<>();
    private boolean updatingNationalityItems;
    private int currentPage = 1;

    @FXML
    private void initialize() {
        Parent root = FxNodes.rootOf(nav_StaffDashboard, filterProfilesButton, tableContainer, profileSearch);
        if (todayDateLabel != null) {
            todayDateLabel.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }

        configureFilterControls();
        configurePartnerSelector();
        bindNavigation();
        ProfileCardBuilder.decorateScreen(root, STORE, () -> {
            configurePartnerSelector();
            applyFilters();
        });

        if (filterProfilesButton != null) filterProfilesButton.setOnAction(e -> { currentPage = 1; applyFilters(); });
        if (clearFiltersButton != null) clearFiltersButton.setOnAction(e -> resetFilters());
        if (profileSearch != null) {
            profileSearch.setOnAction(e -> { currentPage = 1; applyFilters(); });
            profileSearch.textProperty().addListener((obs, oldV, newV) -> { currentPage = 1; applyFilters(); });
        }
        if (statusFilter != null) statusFilter.setOnAction(e -> { currentPage = 1; applyFilters(); });
        if (experienceFilter != null) experienceFilter.setOnAction(e -> { currentPage = 1; applyFilters(); });
        if (locationFilter != null) locationFilter.setOnAction(e -> { currentPage = 1; applyFilters(); });
        if (salaryFilter != null) salaryFilter.setOnAction(e -> { currentPage = 1; applyFilters(); });
        if (shortlistFilter != null) {
            shortlistFilter.setOnAction(e -> { currentPage = 1; applyFilters(); });
            shortlistFilter.valueProperty().addListener((obs, o, n) -> { currentPage = 1; applyFilters(); });
        }
        if (certificateFilter != null) {
            certificateFilter.setOnAction(e -> {
                String val = certificateFilter.getValue();
                selectedCertificates.clear();
                if (val != null && !val.isBlank() && !"All".equalsIgnoreCase(val)) {
                    selectedCertificates.add(val.trim());
                }
                syncCertificateMenuText();
                currentPage = 1;
                applyFilters();
            });
        }
        if (partnerAccessSelector != null) {
            partnerAccessSelector.valueProperty().addListener((obs, oldValue, newValue) -> {
                currentPage = 1;
                applyFilters();
            });
        }
        if (prevPageButton != null) {
            prevPageButton.setOnAction(e -> {
                if (currentPage > 1) {
                    currentPage--;
                    populateCards(filteredTeachers);
                }
            });
        }
        if (nextPageButton != null) {
            nextPageButton.setOnAction(e -> {
                int totalPages = Math.max(1, (int) Math.ceil((double) filteredTeachers.size() / PAGE_SIZE));
                if (currentPage < totalPages) {
                    currentPage++;
                    populateCards(filteredTeachers);
                }
            });
        }

        refreshMetrics();
        applyFilters();
    }

    private void configureFilterControls() {
        if (nationalityFilter != null) {
            nationalityFilter.getItems().setAll(CatalogData.NATIONALITY_FILTER_OPTIONS);
            nationalityFilter.setValue("All");
            nationalityFilter.setEditable(true);
            if (nationalityFilter.getEditor() != null) {
                nationalityFilter.getEditor().textProperty().addListener((obs, oldText, newText) -> {
                    if (updatingNationalityItems) return;
                    updatingNationalityItems = true;
                    try {
                        List<String> matches = CatalogData.filterCountries(newText, true);
                        nationalityFilter.getItems().setAll(matches);
                        nationalityFilter.getEditor().setText(newText);
                    } finally {
                        updatingNationalityItems = false;
                    }
                    currentPage = 1;
                    applyFilters();
                });
            }
            nationalityFilter.setOnAction(e -> { currentPage = 1; applyFilters(); });
        }

        if (statusFilter != null) {
            statusFilter.getItems().setAll(CatalogData.STAFF_STATUS_FILTER_OPTIONS);
            statusFilter.setValue("All");
        }

        if (experienceFilter != null) {
            experienceFilter.getItems().setAll(CatalogData.EXPERIENCE_FILTER_OPTIONS);
            experienceFilter.setValue("All");
        }

        if (locationFilter != null) {
            locationFilter.getItems().setAll(CatalogData.MAJOR_FILTER_OPTIONS);
            locationFilter.setValue("All");
        }

        if (salaryFilter != null) {
            salaryFilter.getItems().setAll(CatalogData.EXPECTED_SALARY_FILTER_OPTIONS);
            salaryFilter.setValue("All");
        }

        if (shortlistFilter != null) {
            shortlistFilter.getItems().setAll(CatalogData.SHORTLIST_FILTER_OPTIONS);
            shortlistFilter.setValue("All");
        }

        if (certificateFilter != null) {
            certificateFilter.getItems().setAll(CatalogData.CERTIFICATE_FILTER_OPTIONS);
            certificateFilter.setValue("All");
        }

        if (certificateMenuButton != null) {
            certificateMenuButton.getItems().clear();
            certMenuItems.clear();
            for (String cert : CatalogData.CERTIFICATE_OPTIONS) {
                CheckMenuItem item = new CheckMenuItem(cert);
                item.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    if (isSelected) {
                        selectedCertificates.add(cert);
                    } else {
                        selectedCertificates.remove(cert);
                    }
                    syncCertificateMenuText();
                    currentPage = 1;
                    applyFilters();
                });
                certMenuItems.add(item);
                certificateMenuButton.getItems().add(item);
            }
            syncCertificateMenuText();
        }
    }

    private void syncCertificateMenuText() {
        if (certificateMenuButton == null) return;
        if (selectedCertificates.isEmpty()) {
            certificateMenuButton.setText("All certificates");
        } else {
            certificateMenuButton.setText(String.join(", ", selectedCertificates));
        }
    }

    private void resetFilters() {
        if (profileSearch != null) profileSearch.clear();
        if (nationalityFilter != null) {
            updatingNationalityItems = true;
            try {
                nationalityFilter.getItems().setAll(CatalogData.NATIONALITY_FILTER_OPTIONS);
                nationalityFilter.setValue("All");
                if (nationalityFilter.getEditor() != null) {
                    nationalityFilter.getEditor().setText("All");
                }
            } finally {
                updatingNationalityItems = false;
            }
        }
        if (statusFilter != null) statusFilter.setValue("All");
        if (experienceFilter != null) experienceFilter.setValue("All");
        if (locationFilter != null) locationFilter.setValue("All");
        if (salaryFilter != null) salaryFilter.setValue("All");
        if (shortlistFilter != null) shortlistFilter.setValue("All");
        if (certificateFilter != null) certificateFilter.setValue("All");
        selectedCertificates.clear();
        for (CheckMenuItem item : certMenuItems) {
            item.setSelected(false);
        }
        syncCertificateMenuText();
        currentPage = 1;
        applyFilters();
    }

    private void configurePartnerSelector() {
        if (partnerAccessSelector == null) return;
        String current = partnerAccessSelector.getValue();
        List<String> partners = STORE.users().stream()
                .filter(user -> "PARTNER".equalsIgnoreCase(user.getRole()))
                .map(user -> user.getUsername())
                .toList();
        partnerAccessSelector.getItems().setAll(partners);
        if (current != null && partners.contains(current)) {
            partnerAccessSelector.setValue(current);
        } else if (!partners.isEmpty()) {
            partnerAccessSelector.getSelectionModel().selectFirst();
        }
    }

    private void bindNavigation() {
        if (nav_StaffDashboard != null) nav_StaffDashboard.setOnAction(e -> applyFilters());
        if (nav_StaffReview != null) nav_StaffReview.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview));
        if (nav_InterviewWorkspace != null) nav_InterviewWorkspace.setOnAction(e -> navigate("InterviewWorkspace.fxml", nav_InterviewWorkspace));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
        if (nav_StaffReview__2 != null) nav_StaffReview__2.setOnAction(e -> navigate("StaffReview.fxml", nav_StaffReview__2));
    }

    public void refreshMetrics() {
        List<TeacherProfile> allTeachers = STORE.teachers();
        long readyCount = allTeachers.stream()
                .filter(t -> "READY".equals(CatalogData.normalizeStatusCode(t.getStatus())))
                .count();
        long pendingCount = allTeachers.stream()
                .filter(t -> {
                    String s = CatalogData.normalizeStatusCode(t.getStatus());
                    return "PENDING".equals(s) || "UPDATE_REQUIRED".equals(s);
                })
                .count();
        List<Interview> allInterviews = STORE.interviews();
        long interviewCount = allInterviews.size();
        long pendingInterviews = allInterviews.stream()
                .filter(iv -> "PENDING".equalsIgnoreCase(iv.getStatus()))
                .count();

        if (readyMetricLabel != null) {
            readyMetricLabel.setText(String.format("%02d", readyCount));
        }
        if (readySubLabel != null) {
            readySubLabel.setText(readyCount + " of " + allTeachers.size() + " total profiles verified (qEP)");
        }
        if (pendingMetricLabel != null) {
            pendingMetricLabel.setText(String.format("%02d", pendingCount));
        }
        if (pendingSubLabel != null) {
            pendingSubLabel.setText("qCV / Update Required profiles awaiting review");
        }
        if (interviewMetricLabel != null) {
            interviewMetricLabel.setText(String.format("%02d", interviewCount));
        }
        if (interviewSubLabel != null) {
            interviewSubLabel.setText(pendingInterviews + " pending confirmation · " + (interviewCount - pendingInterviews) + " processed");
        }
    }

    private void applyFilters() {
        refreshMetrics();

        String query = profileSearch == null || profileSearch.getText() == null
                ? ""
                : profileSearch.getText().trim().toLowerCase(Locale.ROOT);
        String nationality = selectedNationality();
        String status = selected(statusFilter);
        String experience = selected(experienceFilter);
        String majorOrDegree = selected(locationFilter);
        String salary = selected(salaryFilter);
        String shortlistSel = selected(shortlistFilter);
        String targetPartner = selectedPartnerUsername();

        filteredTeachers = STORE.teachers().stream().filter(t -> {
            String name = safe(t.getName());
            String nation = safe(t.getNationality());
            String exp = safe(t.getExperience());
            String certs = safe(t.getCertificates());
            String degree = safe(t.getDegree());
            String major = safe(t.getMajor());
            String subject = safe(t.getSubject());
            String city = safe(t.getLocation());
            String state = safe(t.getStatus());
            String expectedSal = safe(t.getExpectedSalary());

            String haystack = (name + " " + nation + " " + exp + " " + certs + " " + degree + " " + major + " " + subject + " " + city + " " + state + " " + expectedSal)
                    .toLowerCase(Locale.ROOT);
            if (!query.isBlank() && !haystack.contains(query)) return false;
            if (!isAll(nationality) && !nation.toLowerCase(Locale.ROOT).contains(nationality.toLowerCase(Locale.ROOT))) return false;
            if (!CatalogData.matchesStatusFilter(state, status)) return false;
            if (!CatalogData.matchesExperienceFilter(exp, experience)) return false;
            if (!selectedCertificates.isEmpty() && !CatalogData.matchesCertificatesFilter(certs, selectedCertificates)) return false;
            if (!CatalogData.matchesMajorOrDegreeFilter(degree, major.isBlank() ? subject : major, majorOrDegree)) return false;
            if (!CatalogData.matchesSalaryFilter(expectedSal, salary)) return false;
            boolean shortlisted = STORE.isTeacherShortlistedByPartner(targetPartner, t.getId());
            if (!CatalogData.matchesShortlistFilter(shortlisted, shortlistSel)) return false;
            return true;
        }).collect(Collectors.toList());

        populateCards(filteredTeachers);
    }

    private String selectedPartnerUsername() {
        String partnerUsername = partnerAccessSelector == null ? "partner" : partnerAccessSelector.getValue();
        return (partnerUsername == null || partnerUsername.isBlank()) ? "partner" : partnerUsername;
    }

    private String selectedNationality() {
        if (nationalityFilter == null) return "All";
        if (nationalityFilter.isEditable() && nationalityFilter.getEditor() != null) {
            String editorText = nationalityFilter.getEditor().getText();
            if (editorText != null && !editorText.isBlank()) {
                return editorText.trim();
            }
        }
        Object val = nationalityFilter.getValue();
        return val == null ? "All" : String.valueOf(val).trim();
    }

    private void populateCards(List<TeacherProfile> teachers) {
        if (tableContainer == null) return;
        if (cardGridPane == null) {
            cardGridPane = new FlowPane(16, 16);
            cardGridPane.getStyleClass().add("profile-card-grid");
        }
        cardGridPane.getChildren().clear();
        tableContainer.getChildren().setAll(cardGridPane);

        int total = teachers.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        if (currentPage > totalPages) currentPage = totalPages;
        if (currentPage < 1) currentPage = 1;
        int fromIdx = (currentPage - 1) * PAGE_SIZE;
        int toIdx = Math.min(total, fromIdx + PAGE_SIZE);
        List<TeacherProfile> pageSlice = total == 0 ? List.of() : teachers.subList(fromIdx, toIdx);

        if (prevPageButton != null) prevPageButton.setDisable(currentPage <= 1);
        if (nextPageButton != null) nextPageButton.setDisable(currentPage >= totalPages);
        if (pageInfoLabel != null) pageInfoLabel.setText("Page " + currentPage + " / " + totalPages);
        if (countLabel != null) {
            countLabel.setText("Showing " + pageSlice.size() + " (of " + total + " matched / " + STORE.teachers().size() + " total) teacher profile(s)");
        }

        final String targetPartner = selectedPartnerUsername();

        for (TeacherProfile teacher : pageSlice) {
            boolean granted = STORE.canPartnerAccessTeacher(targetPartner, teacher.getId());
            int totalGranted = STORE.countPartnersGrantedForTeacher(teacher.getId());
            VBox card = ProfileCardBuilder.buildStaffTeacherCard(
                    teacher,
                    targetPartner,
                    granted,
                    totalGranted,
                    () -> {
                        StaffReviewController.setSelectedTeacherId(teacher.getId());
                        navigate("StaffReview.fxml", tableContainer);
                    },
                    () -> openTeacherDoc(teacher, "CV"),
                    () -> openTeacherDoc(teacher, "Intro video"),
                    () -> toggleAccess(teacher, targetPartner)
            );
            cardGridPane.getChildren().add(card);
        }

        if (pageSlice.isEmpty()) {
            Label emptyLabel = new Label("No teacher profiles match the current filters.");
            emptyLabel.getStyleClass().add("muted");
            VBox emptyBox = new VBox(emptyLabel);
            emptyBox.getStyleClass().add("table-row");
            tableContainer.getChildren().add(emptyBox);
        }
    }

    private void openTeacherDoc(TeacherProfile teacher, String category) {
        TeacherDocument doc = ProfileCardBuilder.findDocumentByCategory(STORE, teacher.getId(), category);
        if (!ProfileCardBuilder.openDocumentWithAuthAndPlayback(STORE, currentActor(), doc, tableContainer)) {
            if (doc == null) {
                LoginController.showAlert(Alert.AlertType.INFORMATION, "Document Preview",
                        "No " + category + " file is currently available for " + teacher.getName() + ".");
            }
        }
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
