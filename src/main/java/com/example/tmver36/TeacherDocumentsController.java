package com.example.tmver36;

import com.example.tmver36.model.TeacherDocument;
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
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Teacher document management screen backed by PortalStore (TeacherDocuments.fxml). */
public class TeacherDocumentsController {

    private static final PortalStore STORE = LoginController.STORE;

    @FXML private Button nav_TeacherProfile;
    @FXML private Button nav_TeacherDocuments;
    @FXML private Button nav_TeacherSchedule;
    @FXML private Button nav_TeacherHistory;
    @FXML private Button nav_Login;

    @FXML private ComboBox<String> documentType;
    @FXML private TextField documentName;
    @FXML private Button chooseFileButton;
    @FXML private TextField documentUrl;
    @FXML private Button addDocumentButton;
    @FXML private Button viewCvButton;
    @FXML private Button deleteCvButton;
    @FXML private Button viewCertificateButton;
    @FXML private Button viewIntroButton;
    @FXML private Button viewDemoButton;
    @FXML private VBox documentsTable;

    private File chosenFile;

    @FXML
    private void initialize() {
        Parent root = FxNodes.rootOf(nav_TeacherDocuments, addDocumentButton, chooseFileButton, documentsTable);
        if (documentsTable == null && root != null) {
            for (VBox box : FxNodes.findAllByStyleClass(root, "table", VBox.class)) {
                if (box.getChildren().stream().anyMatch(c -> c instanceof HBox h && h.getStyleClass().contains("table-header"))) {
                    documentsTable = box;
                }
            }
        }
        if (documentType != null && documentType.getValue() == null && !documentType.getItems().isEmpty()) {
            documentType.getSelectionModel().selectFirst();
        }
        bindNavigation();
        if (chooseFileButton != null) chooseFileButton.setOnAction(e -> chooseFile());
        if (addDocumentButton != null) addDocumentButton.setOnAction(e -> addDocument());
        if (viewCvButton != null) viewCvButton.setOnAction(e -> openByType("CV"));
        if (deleteCvButton != null) deleteCvButton.setOnAction(e -> deleteByType("CV"));
        if (viewCertificateButton != null) viewCertificateButton.setOnAction(e -> openByType("Certificates"));
        if (viewIntroButton != null) viewIntroButton.setOnAction(e -> openByType("Intro video"));
        if (viewDemoButton != null) viewDemoButton.setOnAction(e -> openByType("Teaching video"));

        updateSidebarHeader(root);
        renderDocuments();
    }

    private void updateSidebarHeader(Parent root) {
        if (root == null) return;
        TeacherProfile profile = STORE.teacher(currentTeacherId());
        if (profile == null) return;
        for (Label label : FxNodes.findAllByStyleClass(root, "sidebar-name", Label.class)) {
            label.setText(profile.getName());
        }
    }

    private void bindNavigation() {
        if (nav_TeacherProfile != null) nav_TeacherProfile.setOnAction(e -> navigate("TeacherProfile.fxml", nav_TeacherProfile));
        if (nav_TeacherDocuments != null) nav_TeacherDocuments.setOnAction(e -> renderDocuments());
        if (nav_TeacherSchedule != null) nav_TeacherSchedule.setOnAction(e -> navigate("TeacherSchedule.fxml", nav_TeacherSchedule));
        if (nav_TeacherHistory != null) nav_TeacherHistory.setOnAction(e -> navigate("TeacherHistory.fxml", nav_TeacherHistory));
        if (nav_Login != null) nav_Login.setOnAction(e -> navigate("Login.fxml", nav_Login));
    }

    private void chooseFile() {
        Window window = chooseFileButton == null || chooseFileButton.getScene() == null ? null : chooseFileButton.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a document");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Documents & Media", "*.pdf", "*.doc", "*.docx", "*.jpg", "*.jpeg", "*.png", "*.mp4", "*.mov", "*.webm"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        chosenFile = chooser.showOpenDialog(window);
        if (chosenFile != null) {
            if (documentName != null && (documentName.getText() == null || documentName.getText().isBlank())) {
                documentName.setText(chosenFile.getName());
            }
            if (documentUrl != null) {
                documentUrl.setText(chosenFile.getAbsolutePath());
            }
        }
    }

    private void addDocument() {
        String type = documentType == null || documentType.getValue() == null ? "CV" : documentType.getValue().trim();
        String name = documentName == null || documentName.getText() == null ? "" : documentName.getText().trim();
        String urlOrPath = documentUrl == null || documentUrl.getText() == null ? "" : documentUrl.getText().trim();
        if (urlOrPath.isBlank() && chosenFile != null) {
            urlOrPath = chosenFile.getAbsolutePath();
        }
        if (name.isBlank() && chosenFile != null) {
            name = chosenFile.getName();
        }
        if (type.isBlank() || (name.isBlank() && urlOrPath.isBlank())) {
            showError("Missing document details", "Select a document type, enter a name, and choose a file or provide a URL.");
            return;
        }
        boolean isHttp = urlOrPath.startsWith("https://") || urlOrPath.startsWith("http://");
        boolean isLocal = urlOrPath.startsWith("file:") || (!urlOrPath.isBlank() && Files.exists(Path.of(urlOrPath)));
        if (!urlOrPath.isBlank() && !isHttp && !isLocal) {
            showError("Invalid document URL", "Use an http(s) URL or select an existing local file.");
            return;
        }
        String finalName = name.isBlank() ? urlOrPath : name;
        String finalLocation = urlOrPath.isBlank() ? finalName : urlOrPath;
        TeacherDocument saved = STORE.addOrReplaceDocument(currentActor(), currentTeacherId(), finalName, type, finalLocation);
        if (saved == null) {
            showError("Save failed", "Could not save document to PortalStore.");
            return;
        }
        if (documentName != null) documentName.clear();
        if (documentUrl != null) documentUrl.clear();
        chosenFile = null;
        renderDocuments();
        showInfo("Document saved", "Saved '" + saved.fileName() + "' (" + saved.type() + ").");
    }

    private void deleteByType(String type) {
        List<TeacherDocument> docs = STORE.documents(currentTeacherId());
        TeacherDocument target = docs.stream()
                .filter(d -> d.type() != null && d.type().toLowerCase(Locale.ROOT).contains(type.toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
        if (target == null) {
            showError("No document", "There is no " + type + " document to delete.");
            return;
        }
        STORE.removeDocument(currentActor(), currentTeacherId(), target.id());
        renderDocuments();
    }

    private void openByType(String type) {
        List<TeacherDocument> docs = STORE.documents(currentTeacherId());
        TeacherDocument doc = docs.stream()
                .filter(d -> d.type() != null && d.type().toLowerCase(Locale.ROOT).contains(type.toLowerCase(Locale.ROOT)))
                .findFirst().orElse(null);
        if (doc == null) {
            showError("Document unavailable", "No file or URL is available for " + type + ".");
            return;
        }
        open(doc);
    }

    private void renderDocuments() {
        if (documentsTable == null) return;
        Node header = documentsTable.getChildren().stream()
                .filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header"))
                .findFirst().orElse(null);
        documentsTable.getChildren().clear();
        if (header != null) documentsTable.getChildren().add(header);

        List<TeacherDocument> docs = STORE.documents(currentTeacherId());
        if (docs.isEmpty()) {
            HBox empty = new HBox(new Label("No documents have been added."));
            empty.getStyleClass().add("table-row");
            documentsTable.getChildren().add(empty);
            return;
        }
        for (TeacherDocument doc : docs) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(doc.fileName(), 350));
            row.getChildren().add(cell(doc.type(), 200));
            row.getChildren().add(cell(doc.updatedAt(), 160));
            HBox actionCell = new HBox(8);
            actionCell.setAlignment(Pos.CENTER_LEFT);
            actionCell.setMinWidth(270);
            actionCell.setPrefWidth(270);
            actionCell.setMaxWidth(270);

            Button view = new Button("View");
            view.getStyleClass().add("secondary");
            view.setOnAction(e -> open(doc));

            Button delete = new Button("Delete");
            delete.getStyleClass().add("danger");
            delete.setOnAction(e -> {
                STORE.removeDocument(currentActor(), currentTeacherId(), doc.id());
                renderDocuments();
            });
            actionCell.getChildren().addAll(view, delete);
            row.getChildren().add(actionCell);
            documentsTable.getChildren().add(row);
        }
    }

    private void open(TeacherDocument document) {
        String loc = document == null || document.location() == null ? "" : document.location().trim();
        if (loc.isBlank()) {
            showError("Document unavailable", "No URL or local file is stored for this document.");
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                if (loc.startsWith("http://") || loc.startsWith("https://")) {
                    Desktop.getDesktop().browse(URI.create(loc));
                } else if (loc.startsWith("file:")) {
                    Desktop.getDesktop().open(Path.of(URI.create(loc)).toFile());
                } else {
                    File file = new File(loc);
                    if (file.exists()) {
                        Desktop.getDesktop().open(file);
                    }
                }
            }
            STORE.log(currentActor(), "Opened document " + document.fileName());
        } catch (Exception ex) {
            showError("Could not open document", ex.getMessage());
        }
    }

    private VBox cell(String text, double width) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("table-text");
        label.setWrapText(true);
        label.setMaxWidth(width - 16);
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        return box;
    }

    private long currentTeacherId() {
        long id = STORE.teacherIdForUser(currentActor());
        return id > 0 ? id : 1L;
    }

    private String currentActor() {
        String username = LoginController.getCurrentUsername();
        return username == null || username.isBlank() ? "teacher" : username;
    }

    private void navigate(String fxml, Node source) {
        if (!LoginController.navigateTo(source, fxml)) {
            showError("Navigation failed", "Could not load " + fxml + ".");
        }
    }

    private void showError(String title, String message) {
        LoginController.showAlert(Alert.AlertType.ERROR, title, message);
    }

    private void showInfo(String title, String message) {
        LoginController.showAlert(Alert.AlertType.INFORMATION, title, message);
    }
}
