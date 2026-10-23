package com.example.tmver36;

import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Teacher document screen. PortalStore has no document CRUD API, so documents are held in a session list. */
public class TeacherDocumentsController {
    private static final PortalStore STORE = LoginController.STORE;
    private static final ObservableList<DocumentRecord> DOCUMENTS = FXCollections.observableArrayList();
    private static boolean seeded;

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

    private VBox documentsTable;
    private File chosenFile;

    @FXML
    private void initialize() {
        seedDemoDocuments();
        Parent root = rootNode();
        if (root != null) findDocumentsTable(root);
        bindNavigation();
        if (chooseFileButton != null) chooseFileButton.setOnAction(e -> chooseFile());
        if (addDocumentButton != null) addDocumentButton.setOnAction(e -> addDocument());
        if (viewCvButton != null) viewCvButton.setOnAction(e -> openByType("CV"));
        if (deleteCvButton != null) deleteCvButton.setOnAction(e -> deleteByType("CV"));
        if (viewCertificateButton != null) viewCertificateButton.setOnAction(e -> openByType("Certificates"));
        if (viewIntroButton != null) viewIntroButton.setOnAction(e -> openByType("Intro video"));
        if (viewDemoButton != null) viewDemoButton.setOnAction(e -> openByType("Teaching video"));
        renderDocuments();
    }

    private Parent rootNode() {
        Node source = nav_TeacherProfile != null ? nav_TeacherProfile : addDocumentButton;
        if (source == null) return null;
        Node n = source;
        while (n.getParent() != null) n = n.getParent();
        return n instanceof Parent p ? p : null;
    }

    private void findDocumentsTable(Parent root) {
        for (Node node : root.lookupAll(".table")) {
            if (node instanceof VBox box && box.getChildren().stream().anyMatch(child ->
                    child instanceof HBox h && h.getStyleClass().contains("table-header"))) {
                // Documents table is the one with four columns; choose the last .table in this screen.
                documentsTable = box;
            }
        }
    }

    private void seedDemoDocuments() {
        if (seeded) return;
        seeded = true;
        DOCUMENTS.addAll(
                new DocumentRecord("Emily_Martin_CV.pdf", "CV", "14/10/2026", "https://example.com/emily-cv"),
                new DocumentRecord("CELTA_Certificate.pdf", "Certificates", "14/10/2026", ""),
                new DocumentRecord("Introduction_Emily.mp4", "Intro video", "14/10/2026", "https://example.com/emily-demo"),
                new DocumentRecord("Teaching_Demo.mp4", "Teaching video", "14/10/2026", "https://example.com/emily-demo")
        );
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
                new FileChooser.ExtensionFilter("Documents and media", "*.pdf", "*.doc", "*.docx", "*.mp4", "*.mov", "*.webm"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        chosenFile = chooser.showOpenDialog(window);
        if (chosenFile != null) {
            if (documentName != null && (documentName.getText() == null || documentName.getText().isBlank())) documentName.setText(chosenFile.getName());
            if (documentUrl != null) documentUrl.setText(chosenFile.toURI().toString());
        }
    }

    private void addDocument() {
        String type = documentType == null || documentType.getValue() == null ? "" : documentType.getValue().trim();
        String name = documentName == null || documentName.getText() == null ? "" : documentName.getText().trim();
        String url = documentUrl == null || documentUrl.getText() == null ? "" : documentUrl.getText().trim();
        if (type.isBlank() || name.isBlank() || (url.isBlank() && chosenFile == null)) {
            showError("Missing document details", "Select a document type, enter a name, and choose a file or provide a URL.");
            return;
        }
        if (!url.isBlank() && !url.startsWith("https://") && !url.startsWith("http://") && !url.startsWith("file:")) {
            showError("Invalid document URL", "Use an http(s) URL or select a local file.");
            return;
        }
        if (DOCUMENTS.stream().anyMatch(d -> d.name.equalsIgnoreCase(name) && d.type.equalsIgnoreCase(type))) {
            showError("Duplicate document", "A document with this name and type already exists.");
            return;
        }
        String finalUrl = url.isBlank() && chosenFile != null ? chosenFile.toURI().toString() : url;
        DOCUMENTS.add(new DocumentRecord(name, type, LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), finalUrl));
        STORE.log(currentActor("teacher"), "Added document " + name + " (" + type + ")");
        if (documentName != null) documentName.clear();
        if (documentUrl != null) documentUrl.clear();
        chosenFile = null;
        renderDocuments();
    }

    private void deleteByType(String type) {
        DocumentRecord target = DOCUMENTS.stream().filter(d -> d.type.equalsIgnoreCase(type)).findFirst().orElse(null);
        if (target == null) {
            showError("No document", "There is no " + type + " document to delete.");
            return;
        }
        DOCUMENTS.remove(target);
        STORE.log(currentActor("teacher"), "Deleted document " + target.name);
        renderDocuments();
    }

    private void openByType(String type) {
        DocumentRecord document = DOCUMENTS.stream().filter(d -> d.type.equalsIgnoreCase(type)).findFirst().orElse(null);
        if (document == null || document.url.isBlank()) {
            showError("Document unavailable", "No file or URL is available for " + type + ".");
            return;
        }
        try {
            URI uri = URI.create(document.url);
            if ("file".equalsIgnoreCase(uri.getScheme())) {
                if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop file opening is not supported.");
                Desktop.getDesktop().open(Path.of(uri).toFile());
            } else {
                if (!Desktop.isDesktopSupported()) throw new IllegalStateException("Desktop browsing is not supported.");
                Desktop.getDesktop().browse(uri);
            }
            STORE.log(currentActor("teacher"), "Opened document " + document.name);
        } catch (Exception ex) {
            showError("Could not open document", "Unable to open the document: " + ex.getMessage());
        }
    }

    private void renderDocuments() {
        if (documentsTable == null) return;
        Node header = documentsTable.getChildren().stream().filter(n -> n instanceof HBox h && h.getStyleClass().contains("table-header")).findFirst().orElse(null);
        documentsTable.getChildren().clear();
        if (header != null) documentsTable.getChildren().add(header);
        for (DocumentRecord doc : DOCUMENTS) {
            HBox row = new HBox(0);
            row.getStyleClass().add("table-row");
            row.getChildren().add(cell(doc.name, 350));
            row.getChildren().add(cell(doc.type, 200));
            row.getChildren().add(cell(doc.updated, 160));
            VBox actionCell = new VBox(6);
            actionCell.setMinWidth(270); actionCell.setPrefWidth(270); actionCell.setMaxWidth(270);
            Button view = new Button("View"); view.getStyleClass().add("secondary"); view.setOnAction(e -> open(doc));
            actionCell.getChildren().add(view);
            if (doc.type.equalsIgnoreCase("CV")) {
                Button delete = new Button("Delete"); delete.getStyleClass().add("danger"); delete.setOnAction(e -> { DOCUMENTS.remove(doc); STORE.log(currentActor("teacher"), "Deleted document " + doc.name); renderDocuments(); });
                actionCell.getChildren().add(delete);
            }
            row.getChildren().add(actionCell);
            documentsTable.getChildren().add(row);
        }
        if (DOCUMENTS.isEmpty()) {
            HBox empty = new HBox(new Label("No documents have been added."));
            empty.getStyleClass().add("table-row");
            documentsTable.getChildren().add(empty);
        }
    }

    private void open(DocumentRecord document) {
        if (document.url.isBlank()) {
            showError("Document unavailable", "No URL or local file is stored for this document.");
            return;
        }
        try {
            URI uri = URI.create(document.url);
            if ("file".equalsIgnoreCase(uri.getScheme())) Desktop.getDesktop().open(Path.of(uri).toFile());
            else Desktop.getDesktop().browse(uri);
            STORE.log(currentActor("teacher"), "Opened document " + document.name);
        } catch (Exception ex) { showError("Could not open document", ex.getMessage()); }
    }

    private VBox cell(String text, double width) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("table-text"); label.setWrapText(true); label.setMaxWidth(width - 16);
        VBox box = new VBox(label); box.setMinWidth(width); box.setPrefWidth(width); box.setMaxWidth(width);
        return box;
    }

    private String currentActor(String fallback) {
        String username = LoginController.getCurrentUsername();
        return username == null || username.isBlank() ? fallback : username;
    }

    private void navigate(String fxml, Node source) {
        if (!LoginController.navigateTo(source, fxml)) showError("Navigation failed", "Could not load " + fxml + ".");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(message); alert.showAndWait();
    }

    private static final class DocumentRecord {
        private final String name;
        private final String type;
        private final String updated;
        private final String url;
        private DocumentRecord(String name, String type, String updated, String url) {
            this.name = name; this.type = type; this.updated = updated; this.url = url == null ? "" : url;
        }
    }
}
