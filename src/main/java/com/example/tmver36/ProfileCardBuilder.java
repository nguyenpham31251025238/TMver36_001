package com.example.tmver36;

import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public final class ProfileCardBuilder {

    private ProfileCardBuilder() {
    }

    public static Node createAvatarNode(TeacherProfile teacher, double size) {
        Image img = loadTeacherImage(teacher);
        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            Circle clip = new Circle(size / 2.0, size / 2.0, size / 2.0);
            iv.setClip(clip);
            StackPane wrapper = new StackPane(iv);
            wrapper.setMinSize(size, size);
            wrapper.setPrefSize(size, size);
            wrapper.setMaxSize(size, size);
            return wrapper;
        }
        Label initials = new Label(initialsOf(teacher == null ? "" : teacher.getName()));
        initials.getStyleClass().add(size >= 44 ? "avatar" : "avatar-small");
        initials.setMinSize(size, size);
        initials.setPrefSize(size, size);
        initials.setMaxSize(size, size);
        initials.setAlignment(Pos.CENTER);
        return initials;
    }

    public static Image loadTeacherImage(TeacherProfile teacher) {
        if (teacher == null) {
            return null;
        }
        String raw = teacher.getAvatarUrl();
        if (raw == null || raw.isBlank()) {
            raw = PortalStore.defaultAvatarForId(teacher.getId());
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                Path p = trimmed.startsWith("file:") ? Paths.get(java.net.URI.create(trimmed)) : Paths.get(trimmed);
                if (Files.isRegularFile(p)) {
                    try (InputStream in = Files.newInputStream(p)) {
                        return new Image(in);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        String resPath = trimmed.startsWith("/") ? trimmed : "/com/example/tmver36/" + trimmed;
        try (InputStream in = ProfileCardBuilder.class.getResourceAsStream(resPath)) {
            if (in != null) {
                return new Image(in);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "TM";
        }
        String[] parts = fullName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    public static String badgeClassForStatus(String status) {
        String normalized = CatalogData.normalizeStatusCode(status);
        return switch (normalized) {
            case "READY" -> "badge-green";
            case "UPDATE_REQUIRED" -> "badge-amber";
            case "DRAFT" -> "badge-gray";
            default -> "badge-blue";
        };
    }

    public static VBox buildStaffTeacherCard(
            TeacherProfile teacher,
            String selectedPartnerUsername,
            boolean isGrantedToSelectedPartner,
            int totalPartnersGranted,
            Runnable onReview,
            Runnable onOpenCv,
            Runnable onWatchVideo,
            Runnable onToggleGrant
    ) {
        VBox card = new VBox(11);
        card.getStyleClass().addAll("teacher-card", "table-row");
        card.setPrefWidth(345);
        card.setMinWidth(305);
        card.setMaxWidth(420);

        // Top row: Avatar + Name/Nationality + Status Badge (qEP / qCV / Update Required)
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Node avatar = createAvatarNode(teacher, 54);

        VBox titleBox = new VBox(3);
        Label nameLabel = new Label(nn(teacher.getName(), "Unnamed Teacher"));
        nameLabel.getStyleClass().add("teacher-card-title");
        nameLabel.setWrapText(true);

        String nationText = nn(teacher.getNationality(), "International")
                + " · " + CatalogData.normalizeExperienceBand(teacher.getExperience());
        Label subLabel = new Label(nationText);
        subLabel.getStyleClass().add("teacher-card-subtitle");
        titleBox.getChildren().addAll(nameLabel, subLabel);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        String shortStatus = CatalogData.formatShortStatusCode(teacher.getStatus());
        Label statusBadge = new Label(shortStatus);
        statusBadge.getStyleClass().addAll("badge", badgeClassForStatus(teacher.getStatus()));

        header.getChildren().addAll(avatar, titleBox, statusBadge);

        // Second row: Expected Salary highlight + Degree & Major
        HBox priceAndDegree = new HBox(8);
        priceAndDegree.setAlignment(Pos.CENTER_LEFT);
        Label salaryTag = new Label("Expected: " + nn(teacher.getExpectedSalary(), "$1,500 - $2,000") + "/mo");
        salaryTag.getStyleClass().add("salary-tag");

        String degMajor = nn(teacher.getDegree(), "Bachelor's Degree") + " · " + nn(teacher.getMajor(), nn(teacher.getSubject(), "English Education"));
        Label degreeLabel = new Label(degMajor);
        degreeLabel.getStyleClass().add("meta-chip");
        degreeLabel.setWrapText(true);
        priceAndDegree.getChildren().addAll(salaryTag, degreeLabel);

        // Third row: Certificates chips
        FlowPane certFlow = new FlowPane(6, 6);
        List<String> certs = CatalogData.parseCertificates(teacher.getCertificates());
        if (certs.isEmpty()) {
            Label chip = new Label("No certificate listed");
            chip.getStyleClass().add("meta-chip");
            certFlow.getChildren().add(chip);
        } else {
            for (String c : certs) {
                Label chip = new Label(c);
                chip.getStyleClass().add("cert-chip");
                certFlow.getChildren().add(chip);
            }
        }

        // Fourth row: Availability & Partner Share status
        HBox infoRow = new HBox(10);
        infoRow.setAlignment(Pos.CENTER_LEFT);
        Label availLabel = new Label("Available: " + nn(teacher.getAvailableStartDate(), nn(teacher.getAvailability(), "Immediate")));
        availLabel.getStyleClass().add("small");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        String shareInfo = isGrantedToSelectedPartner
                ? "Shared with @" + selectedPartnerUsername
                : (totalPartnersGranted > 0 ? "Shared (" + totalPartnersGranted + ")" : "Not shared");
        Label shareBadge = new Label(shareInfo);
        shareBadge.getStyleClass().addAll("badge", isGrantedToSelectedPartner ? "badge-green" : "badge-gray");
        infoRow.getChildren().addAll(availLabel, spacer, shareBadge);

        // Fifth row: Role actions
        HBox actions = new HBox(7);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(4, 0, 0, 0));

        Button reviewBtn = new Button("Review Profile");
        reviewBtn.getStyleClass().addAll("primary", "button-sm");
        reviewBtn.setOnAction(e -> {
            if (onReview != null) onReview.run();
        });

        Button cvBtn = new Button("CV");
        cvBtn.getStyleClass().addAll("secondary", "button-sm");
        cvBtn.setOnAction(e -> {
            if (onOpenCv != null) onOpenCv.run();
        });

        Button videoBtn = new Button("Video");
        videoBtn.getStyleClass().addAll("secondary", "button-sm");
        videoBtn.setOnAction(e -> {
            if (onWatchVideo != null) onWatchVideo.run();
        });

        Button grantBtn = new Button(isGrantedToSelectedPartner ? "Revoke" : "Grant");
        grantBtn.getStyleClass().addAll(isGrantedToSelectedPartner ? "danger" : "secondary", "button-sm");
        grantBtn.setOnAction(e -> {
            if (onToggleGrant != null) onToggleGrant.run();
        });

        actions.getChildren().addAll(reviewBtn, cvBtn, videoBtn, grantBtn);
        card.getChildren().addAll(header, priceAndDegree, certFlow, infoRow, actions);
        return card;
    }

    public static VBox buildPartnerTeacherCard(
            TeacherProfile teacher,
            String shortlistDecision,
            Runnable onViewProfile,
            Runnable onOpenCv,
            Runnable onWatchVideo,
            Runnable onBookInterview
    ) {
        VBox card = new VBox(11);
        card.getStyleClass().addAll("teacher-card", "table-row");
        card.setPrefWidth(345);
        card.setMinWidth(305);
        card.setMaxWidth(420);

        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        Node avatar = createAvatarNode(teacher, 54);

        VBox titleBox = new VBox(3);
        Label nameLabel = new Label(nn(teacher.getName(), "Unnamed Teacher"));
        nameLabel.getStyleClass().add("teacher-card-title");
        nameLabel.setWrapText(true);

        String nationText = nn(teacher.getNationality(), "International")
                + " · " + CatalogData.normalizeExperienceBand(teacher.getExperience());
        Label subLabel = new Label(nationText);
        subLabel.getStyleClass().add("teacher-card-subtitle");
        titleBox.getChildren().addAll(nameLabel, subLabel);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        String badgeText = (shortlistDecision != null && !shortlistDecision.isBlank())
                ? shortlistDecision
                : CatalogData.formatShortStatusCode(teacher.getStatus());
        Label statusBadge = new Label(badgeText);
        statusBadge.getStyleClass().addAll("badge", "badge-green");

        header.getChildren().addAll(avatar, titleBox, statusBadge);

        HBox priceAndDegree = new HBox(8);
        priceAndDegree.setAlignment(Pos.CENTER_LEFT);
        Label salaryTag = new Label("Expected: " + nn(teacher.getExpectedSalary(), "$1,500 - $2,000") + "/mo");
        salaryTag.getStyleClass().add("salary-tag");

        String degMajor = nn(teacher.getDegree(), "Bachelor's Degree") + " · " + nn(teacher.getMajor(), nn(teacher.getSubject(), "English Education"));
        Label degreeLabel = new Label(degMajor);
        degreeLabel.getStyleClass().add("meta-chip");
        degreeLabel.setWrapText(true);
        priceAndDegree.getChildren().addAll(salaryTag, degreeLabel);

        FlowPane certFlow = new FlowPane(6, 6);
        List<String> certs = CatalogData.parseCertificates(teacher.getCertificates());
        if (certs.isEmpty()) {
            Label chip = new Label("Verified ESL Teacher");
            chip.getStyleClass().add("meta-chip");
            certFlow.getChildren().add(chip);
        } else {
            for (String c : certs) {
                Label chip = new Label(c);
                chip.getStyleClass().add("cert-chip");
                certFlow.getChildren().add(chip);
            }
        }

        Label availLabel = new Label("Target age: " + nn(teacher.getTargetAgeGroup(), "Primary / Secondary")
                + " · Available: " + nn(teacher.getAvailableStartDate(), nn(teacher.getAvailability(), "Immediate")));
        availLabel.getStyleClass().add("small");

        HBox actions = new HBox(7);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(4, 0, 0, 0));

        Button viewBtn = new Button("View Profile");
        viewBtn.getStyleClass().addAll("primary", "button-sm");
        viewBtn.setOnAction(e -> {
            if (onViewProfile != null) onViewProfile.run();
        });

        Button cvBtn = new Button("CV");
        cvBtn.getStyleClass().addAll("secondary", "button-sm");
        cvBtn.setOnAction(e -> {
            if (onOpenCv != null) onOpenCv.run();
        });

        Button videoBtn = new Button("Video");
        videoBtn.getStyleClass().addAll("secondary", "button-sm");
        videoBtn.setOnAction(e -> {
            if (onWatchVideo != null) onWatchVideo.run();
        });

        Button bookBtn = new Button("Book Interview");
        bookBtn.getStyleClass().addAll("secondary", "button-sm");
        bookBtn.setOnAction(e -> {
            if (onBookInterview != null) onBookInterview.run();
        });

        actions.getChildren().addAll(viewBtn, cvBtn, videoBtn, bookBtn);
        card.getChildren().addAll(header, priceAndDegree, certFlow, availLabel, actions);
        return card;
    }

    public static TeacherDocument findDocumentByCategory(PortalStore store, long teacherId, String categoryKeyword) {
        if (store == null) return null;
        String targetKey = PortalStore.normalizeDocCategoryKey(categoryKeyword);
        List<TeacherDocument> docs = store.documents(teacherId);
        for (TeacherDocument d : docs) {
            if (PortalStore.normalizeDocCategoryKey(d.type()).equals(targetKey)) {
                return d;
            }
        }
        return docs.isEmpty() ? null : docs.get(0);
    }

    public static void downloadDocumentToDisk(PortalStore store, String actor, TeacherDocument doc, Node sourceNode) {
        Window win = (sourceNode != null && sourceNode.getScene() != null) ? sourceNode.getScene().getWindow() : null;
        if (store != null && doc != null) {
            store.log(actor == null || actor.isBlank() ? "user" : actor, "Downloaded document " + doc.fileName());
        }
        downloadDocumentToDisk(store, doc, win);
    }

    public static void downloadDocumentToDisk(PortalStore store, TeacherDocument doc, Window ownerWindow) {
        if (store == null || doc == null) {
            LoginController.showAlert(javafx.scene.control.Alert.AlertType.WARNING, "Document unavailable", "No document is attached for this item yet.");
            return;
        }
        Path sourceFile = store.resolveDocumentLocalFile(doc);
        if (sourceFile == null || !Files.isRegularFile(sourceFile)) {
            if (doc.location() != null && (doc.location().startsWith("http://") || doc.location().startsWith("https://"))) {
                PortalStore.openInBrowser(doc.location());
                return;
            }
            LoginController.showAlert(javafx.scene.control.Alert.AlertType.WARNING, "File not found", "Could not locate local or cloud copy of " + doc.fileName());
            return;
        }
        if (Boolean.getBoolean("tm.test.suppressAlerts")) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Download " + doc.fileName());
        chooser.setInitialFileName(doc.fileName());
        File dest = chooser.showSaveDialog(ownerWindow);
        if (dest != null) {
            try {
                Files.copy(sourceFile, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                LoginController.showAlert(javafx.scene.control.Alert.AlertType.INFORMATION, "Download complete", "Saved to: " + dest.getAbsolutePath());
            } catch (Exception ex) {
                LoginController.showAlert(javafx.scene.control.Alert.AlertType.ERROR, "Download failed", "Could not save file: " + ex.getMessage());
            }
        }
    }

    private static String nn(String val, String fallback) {
        return (val == null || val.isBlank()) ? fallback : val.trim();
    }
}
