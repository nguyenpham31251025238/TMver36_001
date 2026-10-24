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
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Partner shared-profiles screen backed by PortalStore (PartnerDashboard.fxml). */
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
    @FXML private Button searchGrantedButton;

    private ComboBox<?> experienceFilter;
    private Label countNote;
    private final List<VBox> profileCards = new ArrayList<>();

    @FXML
    private void initialize() {
        Parent root = FxNodes.rootOf(nav_PartnerDashboard, searchGrantedButton, grantedSearch);
        if (root == null) return;

        for (VBox card : FxNodes.findAllByStyleClass(root, "card", VBox.class)) {
            if (containsTeacherName(card)) {
                profileCards.add(card);
            }
        }
        for (ComboBox<?> combo : FxNodes.findAll(root, ComboBox.class)) {
            if (combo.getItems().stream().anyMatch(v -> String.valueOf(v).contains("years") || String.valueOf(v).contains("Experience"))) {
                experienceFilter = combo;
                break;
            }
        }
        for (Label label : FxNodes.findAllByStyleClass(root, "note", Label.class)) {
            if (label.getText() != null && label.getText().contains("profiles granted")) {
                countNote = label;
                break;
            }
        }

        if (searchGrantedButton != null) searchGrantedButton.setOnAction(e -> applySearch());
        if (grantedSearch != null) grantedSearch.setOnAction(e -> applySearch());
        if (experienceFilter != null) experienceFilter.setOnAction(e -> applySearch());

        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> {
            populateCards();
            applySearch();
        });
        if (nav_PartnerProfile != null) nav_PartnerProfile.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerInterview != null) nav_PartnerInterview.setOnAction(e -> navigate("PartnerInterview.fxml"));
        if (nav_PartnerInterview__2 != null) nav_PartnerInterview__2.setOnAction(e -> navigate("PartnerInterview.fxml"));
        if (nav_Login != null) nav_Login.setOnAction(e -> logout());
        if (nav_PartnerProfile__2 != null) nav_PartnerProfile__2.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerProfile__3 != null) nav_PartnerProfile__3.setOnAction(e -> navigate("PartnerProfile.fxml"));
        if (nav_PartnerProfile__4 != null) nav_PartnerProfile__4.setOnAction(e -> navigate("PartnerProfile.fxml"));

        populateCards();
        applySearch();
    }

    private String currentPartner() {
        String u = LoginController.getCurrentUsername();
        return (u == null || u.isBlank()) ? "partner" : u;
    }

    private void populateCards() {
        List<TeacherProfile> teachers = STORE.visibleTeachers(currentPartner(), "PARTNER");
        for (int i = 0; i < profileCards.size(); i++) {
            VBox card = profileCards.get(i);
            TeacherProfile teacher = i < teachers.size() ? teachers.get(i) : null;
            if (teacher == null) {
                card.setVisible(false);
                card.setManaged(false);
                continue;
            }
            card.setUserData(teacher);
            for (Label label : FxNodes.findAllByStyleClass(card, "section-title", Label.class)) {
                label.setText(safe(teacher.getName()));
                break;
            }
            setLabelByStyle(card, "muted", safe(teacher.getNationality()));
            setLabelByStyle(card, "badge-green", safe(teacher.getCertificates()));
            setLabelByStyle(card, "badge-gray", safe(teacher.getExperience()));
            setLabelContainingPrefix(card, "Location", "Location: " + safe(teacher.getLocation()));
            Button view = findButton(card, "View profile & video");
            if (view != null) {
                view.setOnAction(e -> {
                    PartnerProfileController.setSelectedTeacherId(teacher.getId());
                    navigate("PartnerProfile.fxml");
                });
            }
        }
    }

    private void applySearch() {
        String query = grantedSearch == null || grantedSearch.getText() == null ? "" : grantedSearch.getText().trim().toLowerCase(Locale.ROOT);
        String experience = experienceFilter == null || experienceFilter.getValue() == null ? "All" : String.valueOf(experienceFilter.getValue());
        List<TeacherProfile> visible = STORE.visibleTeachers(currentPartner(), "PARTNER");
        int shown = 0;
        for (int i = 0; i < profileCards.size(); i++) {
            VBox card = profileCards.get(i);
            TeacherProfile teacher = i < visible.size() ? visible.get(i) : null;
            boolean matches = teacher != null;
            if (matches && !query.isBlank()) {
                String haystack = (safe(teacher.getName()) + " "
                        + safe(teacher.getCertificates()) + " "
                        + safe(teacher.getLocation()) + " "
                        + safe(teacher.getNationality())).toLowerCase(Locale.ROOT);
                matches = haystack.contains(query);
            }
            if (matches && experience != null && !experience.equalsIgnoreCase("All") && !experience.isBlank()) {
                String exp = safe(teacher.getExperience()).toLowerCase(Locale.ROOT);
                String requested = experience.toLowerCase(Locale.ROOT).replace(" years", "").replace(" year", "").trim();
                matches = exp.contains(requested) || requested.contains(exp);
            }
            card.setVisible(matches);
            card.setManaged(matches);
            if (matches) shown++;
        }
        if (countNote != null) {
            countNote.setText("Showing " + shown + " shared teacher profile(s) available to this partner.");
        }
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

    private boolean containsTeacherName(VBox card) {
        return !FxNodes.findAllByStyleClass(card, "section-title", Label.class).isEmpty()
                && findButton(card, "View profile & video") != null;
    }

    private void setLabelContainingPrefix(VBox card, String prefix, String value) {
        for (Label l : FxNodes.findAll(card, Label.class)) {
            if (l.getText() != null && l.getText().startsWith(prefix)) {
                l.setText(value);
                return;
            }
        }
    }

    private void setLabelByStyle(VBox card, String style, String value) {
        for (Label l : FxNodes.findAllByStyleClass(card, style, Label.class)) {
            l.setText(value.isBlank() ? "Not provided" : value);
            return;
        }
    }

    private Button findButton(VBox card, String text) {
        for (Button b : FxNodes.findAll(card, Button.class)) {
            if (text.equals(b.getText())) return b;
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void showError(String title, String message) {
        LoginController.showAlert(Alert.AlertType.ERROR, title, message);
    }
}
