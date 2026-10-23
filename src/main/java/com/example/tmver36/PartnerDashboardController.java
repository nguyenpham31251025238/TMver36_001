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

/** Partner shared-profiles screen backed by PortalStore. */
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
        Parent root = nav_PartnerDashboard == null ? null : nav_PartnerDashboard.getParent();
        while (root != null && !(root instanceof javafx.scene.layout.BorderPane)) root = root.getParent();
        if (root == null && nav_PartnerDashboard != null) root = nav_PartnerDashboard.getScene() == null ? null : nav_PartnerDashboard.getScene().getRoot();
        if (root == null) return;

        for (Node node : root.lookupAll(".card")) {
            if (node instanceof VBox card && containsTeacherName(card)) profileCards.add(card);
        }
        for (Node node : root.lookupAll("ComboBox")) {
            if (node instanceof ComboBox<?> combo && combo.getItems().stream().anyMatch(v -> String.valueOf(v).contains("years") || String.valueOf(v).contains("Experience"))) {
                experienceFilter = combo;
                break;
            }
        }
        for (Node node : root.lookupAll(".note")) {
            if (node instanceof Label label && label.getText() != null && label.getText().contains("profiles granted")) {
                countNote = label;
                break;
            }
        }

        if (searchGrantedButton != null) searchGrantedButton.setOnAction(e -> applySearch());
        if (grantedSearch != null) grantedSearch.setOnAction(e -> applySearch());
        if (experienceFilter != null) experienceFilter.setOnAction(e -> applySearch());

        if (nav_PartnerDashboard != null) nav_PartnerDashboard.setOnAction(e -> applySearch());
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

    private void populateCards() {
        List<TeacherProfile> teachers = STORE.visibleTeachers(LoginController.getCurrentUsername(), "PARTNER");
        for (VBox card : profileCards) {
            String name = cardTeacherName(card);
            TeacherProfile teacher = teachers.stream()
                    .filter(t -> safe(property(t, "getName", "getFullName")).equalsIgnoreCase(name))
                    .findFirst().orElse(null);
            if (teacher == null) continue;
            setLabelContaining(card, name, safe(property(teacher, "getName", "getFullName")));
            setLabelByStyleAndIndex(card, "muted", 0, safe(property(teacher, "getNationality", "getCountry")));
            setLabelByStyleAndIndex(card, "badge,badge-green", 0, safe(property(teacher, "getCertificates", "getQualification")));
            setLabelByStyleAndIndex(card, "badge,badge-gray", 0, safe(property(teacher, "getExperience", "getYearsExperience")));
            setLabelContainingPrefix(card, "Location", "Location: " + safe(property(teacher, "getLocation", "getCity")));
            Button view = findButton(card, "View profile & video");
            if (view != null) view.setOnAction(e -> navigate("PartnerProfile.fxml"));
        }
    }

    private void applySearch() {
        String query = grantedSearch == null || grantedSearch.getText() == null ? "" : grantedSearch.getText().trim().toLowerCase(Locale.ROOT);
        String experience = experienceFilter == null || experienceFilter.getValue() == null ? "All" : String.valueOf(experienceFilter.getValue());
        List<TeacherProfile> visible = STORE.visibleTeachers(LoginController.getCurrentUsername(), "PARTNER");
        int shown = 0;
        for (VBox card : profileCards) {
            String name = cardTeacherName(card);
            TeacherProfile teacher = visible.stream().filter(t -> safe(property(t, "getName", "getFullName")).equalsIgnoreCase(name)).findFirst().orElse(null);
            boolean matches = teacher != null;
            if (matches && !query.isBlank()) {
                String haystack = (safe(property(teacher, "getName", "getFullName")) + " "
                        + safe(property(teacher, "getCertificates", "getQualification")) + " "
                        + safe(property(teacher, "getLocation", "getCity")) + " "
                        + safe(property(teacher, "getNationality", "getCountry"))).toLowerCase(Locale.ROOT);
                matches = haystack.contains(query);
            }
            if (matches && experience != null && !experience.equalsIgnoreCase("All") && !experience.isBlank()) {
                String exp = safe(property(teacher, "getExperience", "getYearsExperience")).toLowerCase(Locale.ROOT);
                String requested = experience.toLowerCase(Locale.ROOT).replace(" years", "").replace(" year", "").trim();
                matches = exp.contains(requested) || requested.contains(exp);
            }
            card.setVisible(matches);
            card.setManaged(matches);
            if (matches) shown++;
        }
        if (countNote != null) countNote.setText("Showing " + shown + " shared teacher profile(s) available to this partner.");
    }

    private void navigate(String fxml) {
        Node source = nav_PartnerDashboard;
        if (source != null && !LoginController.navigateTo(source, fxml)) showError("Navigation failed", "Could not load " + fxml + ". Check the FXML resource path.");
    }

    private void logout() {
        if (nav_Login != null && !LoginController.navigateTo(nav_Login, "Login.fxml")) showError("Navigation failed", "Could not load Login.fxml.");
    }

    private boolean containsTeacherName(VBox card) {
        return cardTeacherName(card) != null;
    }

    private String cardTeacherName(VBox card) {
        for (Node node : card.lookupAll(".section-title")) {
            if (node instanceof Label label && List.of("Emily Martin", "Daniel Walker", "Sarah Thompson").contains(label.getText())) return label.getText();
        }
        return null;
    }

    private void setLabelContaining(VBox card, String old, String value) {
        for (Node node : card.lookupAll("Label")) if (node instanceof Label l && old.equals(l.getText())) { l.setText(value); return; }
    }
    private void setLabelContainingPrefix(VBox card, String prefix, String value) {
        for (Node node : card.lookupAll("Label")) if (node instanceof Label l && l.getText() != null && l.getText().startsWith(prefix)) { l.setText(value); return; }
    }
    private void setLabelByStyleAndIndex(VBox card, String style, int ignored, String value) {
        for (Node node : card.lookupAll("Label")) if (node instanceof Label l && l.getStyleClass().containsAll(List.of(style.split(",")))) {
            l.setText(value.isBlank() ? "Not provided" : value); return;
        }
    }
    private Button findButton(VBox card, String text) {
        for (Node node : card.lookupAll("Button")) if (node instanceof Button b && text.equals(b.getText())) return b;
        return null;
    }
    private Object property(Object target, String... getters) {
        if (target == null) return null;
        for (String getter : getters) try { Method m = target.getClass().getMethod(getter); return m.invoke(target); } catch (ReflectiveOperationException ignored) { }
        return null;
    }
    private String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }
}
