package com.example.tmver36;

import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class ProfileCardBuilder {

    private ProfileCardBuilder() {
    }

    public static Image loadAppLogo() {
        for (String res : List.of("/com/example/tmver36/logo.png", "/com/example/tmver36/photo/logo.png")) {
            try (InputStream in = ProfileCardBuilder.class.getResourceAsStream(res)) {
                if (in != null) {
                    Image img = new Image(in);
                    if (!img.isError()) {
                        return img;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static void applyAppIcon(Stage stage) {
        if (stage == null) return;
        Image logo = loadAppLogo();
        if (logo != null && stage.getIcons().isEmpty()) {
            stage.getIcons().add(logo);
        }
    }

    public static Node createLogoNode(double size) {
        Image logo = loadAppLogo();
        if (logo != null) {
            ImageView iv = new ImageView(logo);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            return iv;
        }
        Label fallback = new Label("TM");
        fallback.getStyleClass().add("avatar-small");
        fallback.setMinSize(size, size);
        fallback.setPrefSize(size, size);
        fallback.setMaxSize(size, size);
        fallback.setAlignment(Pos.CENTER);
        return fallback;
    }

    /**
     * Injects the TeacherMatch brand logo into the sidebar and wires a Reload button with loading/error states
     * into the topbar of any screen.
     */
    public static void decorateScreen(Parent root, PortalStore store, Runnable onReload) {
        if (root == null) return;

        // 1. Brand logo in sidebar
        for (VBox brandBox : FxNodes.findAllByStyleClass(root, "brand", VBox.class)) {
            boolean alreadyHasLogo = brandBox.getChildren().stream()
                    .anyMatch(n -> "tm-brand-logo-row".equals(n.getId()));
            if (!alreadyHasLogo && !brandBox.getChildren().isEmpty()) {
                Node first = brandBox.getChildren().get(0);
                if (first instanceof Label titleLabel) {
                    brandBox.getChildren().remove(titleLabel);
                    HBox logoRow = new HBox(10, createLogoNode(28), titleLabel);
                    logoRow.setId("tm-brand-logo-row");
                    logoRow.setAlignment(Pos.CENTER_LEFT);
                    brandBox.getChildren().add(0, logoRow);
                }
            }
        }

        // 2. Reload button + status indicator in topbar
        HBox topbar = FxNodes.findFirstByStyleClass(root, "topbar", HBox.class);
        if (topbar != null && store != null) {
            Label statusBadge = null;
            for (Node child : topbar.getChildren()) {
                if (child instanceof Label lbl && "reloadStatusLabel".equals(lbl.getId())) {
                    statusBadge = lbl;
                    break;
                }
            }
            if (statusBadge == null) {
                statusBadge = new Label("");
                statusBadge.setId("reloadStatusLabel");
                statusBadge.getStyleClass().addAll("badge", "badge-gray");
                statusBadge.setVisible(false);
                statusBadge.setManaged(false);
            }

            Button reloadBtn = null;
            for (Button btn : FxNodes.findAll(topbar, Button.class)) {
                if ("syncCloudButton".equals(btn.getId()) || "reloadDataButton".equals(btn.getId())) {
                    reloadBtn = btn;
                    break;
                }
            }
            if (reloadBtn == null) {
                reloadBtn = new Button("↻ Reload");
                reloadBtn.setId("reloadDataButton");
                reloadBtn.getStyleClass().addAll("secondary", "button-sm");
                topbar.getChildren().addAll(statusBadge, reloadBtn);
            } else if (!topbar.getChildren().contains(statusBadge)) {
                int idx = topbar.getChildren().indexOf(reloadBtn);
                topbar.getChildren().add(Math.max(0, idx), statusBadge);
            }

            final Button finalBtn = reloadBtn;
            final Label finalStatus = statusBadge;
            finalBtn.setOnAction(e -> triggerReloadWithState(store, finalBtn, finalStatus, onReload));
        }
    }

    public static void triggerReloadWithState(PortalStore store, Button reloadBtn, Label statusLabel, Runnable onReload) {
        if (store == null) return;
        String originalText = reloadBtn != null ? reloadBtn.getText() : "↻ Reload";
        if (reloadBtn != null) {
            reloadBtn.setDisable(true);
            reloadBtn.setText("Reloading...");
        }
        if (statusLabel != null) {
            statusLabel.setText("Reloading...");
            statusLabel.getStyleClass().setAll("badge", "badge-amber");
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        }

        if (Boolean.getBoolean("tm.test.syncReload") || !Platform.isFxApplicationThread()) {
            PortalStore.ReloadResult res = store.reloadLatestData();
            applyReloadResultToUi(res, reloadBtn, originalText, statusLabel, onReload);
            return;
        }

        CompletableFuture.supplyAsync(store::reloadLatestData)
                .thenAccept(res -> Platform.runLater(() ->
                        applyReloadResultToUi(res, reloadBtn, originalText, statusLabel, onReload)))
                .exceptionally(ex -> {
                    Platform.runLater(() -> {
                        PortalStore.ReloadResult err = new PortalStore.ReloadResult(
                                false, "ERROR", "Reload failed: " + ex.getMessage(), "");
                        applyReloadResultToUi(err, reloadBtn, originalText, statusLabel, onReload);
                    });
                    return null;
                });
    }

    private static void applyReloadResultToUi(
            PortalStore.ReloadResult res,
            Button reloadBtn,
            String originalText,
            Label statusLabel,
            Runnable onReload
    ) {
        if (reloadBtn != null) {
            reloadBtn.setDisable(false);
            reloadBtn.setText(originalText == null || originalText.isBlank() || "Reloading...".equals(originalText)
                    ? "↻ Reload" : originalText);
        }
        if (onReload != null) {
            onReload.run();
        }
        if (statusLabel != null && res != null) {
            if (!res.success()) {
                statusLabel.setText("Reload error: " + res.message());
                statusLabel.getStyleClass().setAll("badge", "badge-amber");
            } else {
                statusLabel.setText("Updated · " + res.timestamp());
                statusLabel.getStyleClass().setAll("badge", "badge-green");
            }
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        }
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

    /**
     * Replaces a static Label avatar (e.g. in sidebar or topbar) with a live image/initials node for the teacher.
     */
    public static void updateAvatarSlot(Parent root, TeacherProfile teacher, double size) {
        if (root == null || teacher == null) return;
        for (Label avatar : FxNodes.findAllByStyleClass(root, "avatar", Label.class)) {
            updateAvatarSlot(avatar, teacher, size);
        }
        for (Label avatarSmall : FxNodes.findAllByStyleClass(root, "avatar-small", Label.class)) {
            updateAvatarSlot(avatarSmall, teacher, 36);
        }
    }

    public static void updateAvatarSlot(Label avatarLabel, TeacherProfile teacher, double size) {
        if (avatarLabel == null || teacher == null) return;
        avatarLabel.setText(initialsOf(teacher.getName()));
        Image img = loadTeacherImage(teacher);
        if (img != null && !img.isError()) {
            ImageView iv = new ImageView(img);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setClip(new Circle(size / 2.0, size / 2.0, size / 2.0));
            avatarLabel.setGraphic(iv);
            avatarLabel.setText("");
        } else {
            avatarLabel.setGraphic(null);
        }
    }

    public static Image loadTeacherImage(TeacherProfile teacher) {
        if (teacher == null) {
            return null;
        }
        // First resolve through PortalStore (handles local disk, base64 sync fallback, and CloudSyncService download)
        try {
            if (LoginController.STORE != null) {
                Path resolved = LoginController.STORE.resolveTeacherAvatarFile(teacher);
                if (resolved != null && Files.isRegularFile(resolved)) {
                    try (InputStream in = Files.newInputStream(resolved)) {
                        Image img = new Image(in);
                        if (!img.isError()) {
                            return img;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
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

        // Second row: Expected Salary highlight + Major/Subject (Task 5: redundant degree text omitted from card; preserved in detail view)
        HBox priceAndMajor = new HBox(8);
        priceAndMajor.setAlignment(Pos.CENTER_LEFT);
        Label salaryTag = new Label("Expected: " + nn(teacher.getExpectedSalary(), "$1,500 - $2,000") + "/mo");
        salaryTag.getStyleClass().add("salary-tag");

        String majorOnly = nn(teacher.getMajor(), nn(teacher.getSubject(), "English Education"));
        Label majorLabel = new Label(majorOnly);
        majorLabel.getStyleClass().add("meta-chip");
        majorLabel.setWrapText(true);
        priceAndMajor.getChildren().addAll(salaryTag, majorLabel);

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
        card.getChildren().addAll(header, priceAndMajor, certFlow, infoRow, actions);
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
        return buildPartnerTeacherCard(teacher, shortlistDecision, onViewProfile, onOpenCv, onWatchVideo, onBookInterview, null);
    }

    public static VBox buildPartnerTeacherCard(
            TeacherProfile teacher,
            String shortlistDecision,
            Runnable onViewProfile,
            Runnable onOpenCv,
            Runnable onWatchVideo,
            Runnable onBookInterview,
            Runnable onToggleShortlist
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

        boolean isShortlisted = shortlistDecision != null && shortlistDecision.toLowerCase(Locale.ROOT).contains("shortlist");
        String badgeText = (shortlistDecision != null && !shortlistDecision.isBlank())
                ? shortlistDecision
                : CatalogData.formatShortStatusCode(teacher.getStatus());
        Label statusBadge = new Label(badgeText);
        statusBadge.getStyleClass().addAll("badge", isShortlisted ? "badge-blue" : "badge-green");

        header.getChildren().addAll(avatar, titleBox, statusBadge);

        // Task 5: Do not repeat redundant degree text on the card; show salary + major/subject cleanly
        HBox priceAndMajor = new HBox(8);
        priceAndMajor.setAlignment(Pos.CENTER_LEFT);
        Label salaryTag = new Label("Expected: " + nn(teacher.getExpectedSalary(), "$1,500 - $2,000") + "/mo");
        salaryTag.getStyleClass().add("salary-tag");

        String majorOnly = nn(teacher.getMajor(), nn(teacher.getSubject(), "English Education"));
        Label majorLabel = new Label(majorOnly);
        majorLabel.getStyleClass().add("meta-chip");
        majorLabel.setWrapText(true);
        priceAndMajor.getChildren().addAll(salaryTag, majorLabel);

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
        if (onToggleShortlist != null) {
            Button shortlistBtn = new Button(isShortlisted ? "★" : "☆");
            shortlistBtn.getStyleClass().addAll(isShortlisted ? "primary" : "secondary", "button-sm");
            shortlistBtn.setOnAction(e -> onToggleShortlist.run());
            actions.getChildren().add(shortlistBtn);
        }
        card.getChildren().addAll(header, priceAndMajor, certFlow, availLabel, actions);
        return card;
    }

    /**
     * Task 10: Finds the matching document for the requested category.
     * For video categories, uses PortalStore.findBestVideoDocument and never falls back to a non-video document!
     */
    public static TeacherDocument findDocumentByCategory(PortalStore store, long teacherId, String categoryKeyword) {
        if (store == null) return null;
        String targetKey = PortalStore.normalizeDocCategoryKey(categoryKeyword);
        if ("INTRO_VIDEO".equals(targetKey) || "DEMO_VIDEO".equals(targetKey)
                || (categoryKeyword != null && categoryKeyword.toLowerCase(Locale.ROOT).contains("video"))) {
            return store.findBestVideoDocument(teacherId, categoryKeyword);
        }
        List<TeacherDocument> docs = store.documents(teacherId);
        for (TeacherDocument d : docs) {
            if (PortalStore.normalizeDocCategoryKey(d.type()).equals(targetKey)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Task 10: Opens or plays a teacher document/video with strict authentication and role authorization,
     * plus an in-app video playback/inspection dialog when viewing videos on desktop.
     */
    public static boolean openDocumentWithAuthAndPlayback(PortalStore store, String actor, TeacherDocument doc, Node sourceNode) {
        if (store == null || doc == null) {
            LoginController.showAlert(Alert.AlertType.INFORMATION, "Document Preview", "No document or video is attached for this item yet.");
            return false;
        }
        if (!store.canUserAccessDocument(actor, doc)) {
            LoginController.showAlert(Alert.AlertType.ERROR, "Access Denied", "Your account does not have permission to open this file.");
            return false;
        }
        boolean opened = store.openDocument(actor, doc);
        if (!opened) {
            LoginController.showAlert(Alert.AlertType.WARNING, "Media Unavailable",
                    store.getLastValidationError().isBlank()
                            ? "Could not locate or open '" + doc.fileName() + "'. Please click Reload and try again."
                            : store.getLastValidationError());
            return false;
        }
        if (PortalStore.isVideoDocument(doc) && !Boolean.getBoolean("tm.test.suppressAlerts")) {
            Path localFile = store.resolveDocumentLocalFile(doc);
            Alert dialog = new Alert(Alert.AlertType.INFORMATION);
            dialog.setTitle("Video Player — " + doc.fileName());
            dialog.setHeaderText(doc.type() + " · " + doc.fileName());
            String sourceDesc = localFile != null
                    ? "Verified local/synced video file:\n" + localFile.toAbsolutePath()
                    : "Verified video stream:\n" + doc.location();
            dialog.setContentText(sourceDesc + "\n\nUpdated: " + doc.updatedAt() + " (UTC+7)\nAuthorized for @" + actor);
            ButtonType downloadBtn = new ButtonType("Download Video");
            dialog.getButtonTypes().setAll(downloadBtn, ButtonType.CLOSE);
            dialog.showAndWait().ifPresent(choice -> {
                if (choice == downloadBtn) {
                    downloadDocumentToDisk(store, actor, doc, sourceNode);
                }
            });
        }
        return true;
    }

    public static void downloadDocumentToDisk(PortalStore store, String actor, TeacherDocument doc, Node sourceNode) {
        Window win = (sourceNode != null && sourceNode.getScene() != null) ? sourceNode.getScene().getWindow() : null;
        if (store != null && doc != null) {
            if (!store.canUserAccessDocument(actor, doc)) {
                LoginController.showAlert(Alert.AlertType.ERROR, "Access Denied", "Your account does not have permission to download this file.");
                return;
            }
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
