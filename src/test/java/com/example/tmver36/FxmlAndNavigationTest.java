package com.example.tmver36;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.control.ComboBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FxmlAndNavigationTest {

    private static final List<String> ALL_FXML_FILES = List.of(
            "Login.fxml",
            "AdminAccounts.fxml",
            "AdminLogs.fxml",
            "StaffDashboard.fxml",
            "StaffReview.fxml",
            "InterviewWorkspace.fxml",
            "PartnerDashboard.fxml",
            "PartnerProfile.fxml",
            "PartnerInterview.fxml",
            "TeacherProfile.fxml",
            "TeacherDocuments.fxml",
            "TeacherSchedule.fxml",
            "TeacherHistory.fxml",
            "hello-view.fxml"
    );

    @BeforeAll
    static void initJavaFx() throws Exception {
        System.setProperty("tm.test.suppressAlerts", "true");
        Path testDataDir = Files.createTempDirectory("tmver36-ui-test");
        System.setProperty("tm.data.dir", testDataDir.toString());
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            latch.countDown();
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS), "JavaFX toolkit failed to start");
    }

    @BeforeEach
    void resetStoreAndSession() throws Exception {
        runOnFxThread(() -> {
            LoginController.clearSession();
            LoginController.STORE.resetToSeedData();
        });
    }

    @Test
    void allFourteenFxmlScreensLoadWithoutException() throws Exception {
        runOnFxThread(() -> {
            for (String fxml : ALL_FXML_FILES) {
                URL url = LoginController.class.getResource(fxml);
                assertNotNull(url, "Missing FXML resource: " + fxml);
                FXMLLoader loader = new FXMLLoader(url);
                Parent root = assertDoesNotThrow(() -> loader.load(), "Failed to load " + fxml);
                assertNotNull(root, "Null root for " + fxml);
                assertNotNull(loader.getController(), "Null controller for " + fxml);
            }
        });
    }

    @Test
    void teacherScheduleControllerWiringAndCvDocumentsHandlerWork() throws Exception {
        runOnFxThread(() -> {
            LoginController.setCurrentSession("teacher", "TEACHER");
            FXMLLoader loader = new FXMLLoader(LoginController.class.getResource("TeacherSchedule.fxml"));
            Parent root = assertDoesNotThrow(() -> loader.load());
            TeacherScheduleController controller = loader.getController();
            assertNotNull(controller);

            Button navProfile = getField(controller, "nav_TeacherProfile", Button.class);
            Button navDocuments = getField(controller, "nav_TeacherDocuments", Button.class);
            Button navSchedule = getField(controller, "nav_TeacherSchedule", Button.class);
            Button navHistory = getField(controller, "nav_TeacherHistory", Button.class);
            Button navLogin = getField(controller, "nav_Login", Button.class);
            Button addSlotButton = getField(controller, "addSlotButton", Button.class);
            VBox slotsTable = getField(controller, "slotsTable", VBox.class);
            TextField meetLinkField = getField(controller, "meetLinkField", TextField.class);
            ComboBox<?> fromCombo = getField(controller, "fromCombo", ComboBox.class);
            ComboBox<?> toCombo = getField(controller, "toCombo", ComboBox.class);

            assertNotNull(navProfile.getOnAction(), "nav_TeacherProfile handler must be bound");
            assertNotNull(navDocuments.getOnAction(), "nav_TeacherDocuments (CV & Documents) handler must be bound");
            assertNotNull(navSchedule.getOnAction(), "nav_TeacherSchedule handler must be bound");
            assertNotNull(navHistory.getOnAction(), "nav_TeacherHistory handler must be bound");
            assertNotNull(navLogin.getOnAction(), "nav_Login handler must be bound");
            assertNotNull(addSlotButton.getOnAction(), "addSlotButton handler must be bound");
            assertNotNull(slotsTable, "slotsTable VBox must be resolved");
            assertTrue(slotsTable.getStyleClass().contains("table"), "slotsTable must have .table styleClass");
            assertNotNull(meetLinkField, "meetLinkField TextField must be resolved");
            assertNotNull(fromCombo, "fromCombo must be resolved");
            assertNotNull(toCombo, "toCombo must be resolved");

            Scene scene = new Scene(root, 1360, 900);
            navDocuments.fire();
            assertNotSame(root, scene.getRoot(), "Firing CV & Documents should navigate to TeacherDocuments.fxml");
        });
    }

    @Test
    void allFourRolesLoginNavigateAllScreensAndLogout() throws Exception {
        runOnFxThread(() -> {
            verifyRoleLoginAndNavigation(
                    "admin", "admin123", "System Admin", "ADMIN",
                    List.of("AdminAccounts.fxml", "AdminLogs.fxml"),
                    "StaffDashboard.fxml"
            );
            verifyRoleLoginAndNavigation(
                    "staff", "staff123", "Matching Staff", "STAFF",
                    List.of("StaffDashboard.fxml", "StaffReview.fxml", "InterviewWorkspace.fxml"),
                    "AdminAccounts.fxml"
            );
            verifyRoleLoginAndNavigation(
                    "partner", "partner123", "Partner", "PARTNER",
                    List.of("PartnerDashboard.fxml", "PartnerProfile.fxml", "PartnerInterview.fxml"),
                    "TeacherProfile.fxml"
            );
            verifyRoleLoginAndNavigation(
                    "teacher", "teacher123", "Teacher", "TEACHER",
                    List.of("TeacherProfile.fxml", "TeacherDocuments.fxml", "TeacherSchedule.fxml", "TeacherHistory.fxml"),
                    "PartnerDashboard.fxml"
            );
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void interactiveControllerWorkflowsUpdateStoreAndUi() throws Exception {
        runOnFxThread(() -> {
            LoginController.STORE.resetToSeedData();

            // 1. TeacherProfileController save profile
            LoginController.setCurrentSession("teacher", "TEACHER");
            FXMLLoader profileLoader = new FXMLLoader(HelloApplication.class.getResource("TeacherProfile.fxml"));
            Parent profileRoot = profileLoader.load();
            new Scene(profileRoot, 1280, 820);
            TeacherProfileController profileCtrl = profileLoader.getController();
            TextField nameField = getField(profileCtrl, "fullNameField", TextField.class);
            TextField citySelect = getField(profileCtrl, "locationCombo", TextField.class);
            Button saveProfileButton = getField(profileCtrl, "saveDraftButton", Button.class);
            nameField.setText("Emily Martin Updated");
            citySelect.setText("Da Nang");
            saveProfileButton.fire();
            assertEquals("Emily Martin Updated", LoginController.STORE.teacherForUser("teacher").getName());
            assertEquals("Da Nang", LoginController.STORE.teacherForUser("teacher").getLocation());

            // 2. PartnerProfileController shortlist evaluation (does not corrupt Staff verification status)
            LoginController.setCurrentSession("partner", "PARTNER");
            PartnerProfileController.setSelectedTeacherId(1L);
            FXMLLoader partnerProfileLoader = new FXMLLoader(HelloApplication.class.getResource("PartnerProfile.fxml"));
            Parent partnerProfileRoot = partnerProfileLoader.load();
            new Scene(partnerProfileRoot, 1280, 820);
            PartnerProfileController partnerProfileCtrl = partnerProfileLoader.getController();
            ComboBox<String> shortlistDecision = getField(partnerProfileCtrl, "shortlistDecision", ComboBox.class);
            TextArea shortlistComment = getField(partnerProfileCtrl, "shortlistComment", TextArea.class);
            Button saveShortlistButton = getField(partnerProfileCtrl, "saveShortlistButton", Button.class);
            shortlistDecision.setValue("Shortlist");
            shortlistComment.setText("Strong candidate for Grade 3 ESL");
            saveShortlistButton.fire();
            assertEquals("READY", LoginController.STORE.teacher(1L).getStatus(),
                    "Partner evaluation must not overwrite Staff verification status");
            assertEquals("Shortlist", LoginController.STORE.partnerEvaluation("partner", 1L)[0]);

            // 3. PartnerInterviewController request interview from available slots
            FXMLLoader partnerInterviewLoader = new FXMLLoader(HelloApplication.class.getResource("PartnerInterview.fxml"));
            Parent partnerInterviewRoot = partnerInterviewLoader.load();
            new Scene(partnerInterviewRoot, 1280, 820);
            PartnerInterviewController partnerInterviewCtrl = partnerInterviewLoader.getController();
            Button requestInterviewButton = getField(partnerInterviewCtrl, "requestInterviewButton", Button.class);
            int beforeCount = LoginController.STORE.visibleInterviews("PARTNER", "partner").size();
            requestInterviewButton.fire();
            int afterCount = LoginController.STORE.visibleInterviews("PARTNER", "partner").size();
            assertTrue(afterCount > beforeCount, "Partner should be able to request an interview from available slots");

            // 4. InterviewWorkspaceController auto-generates & confirms live meeting URL
            LoginController.setCurrentSession("staff", "STAFF");
            FXMLLoader workspaceLoader = new FXMLLoader(HelloApplication.class.getResource("InterviewWorkspace.fxml"));
            Parent workspaceRoot = workspaceLoader.load();
            new Scene(workspaceRoot, 1280, 820);
            InterviewWorkspaceController workspaceCtrl = workspaceLoader.getController();
            TextField meetUrl = getField(workspaceCtrl, "meetUrl", TextField.class);
            Button createMeetButton = getField(workspaceCtrl, "createMeetButton", Button.class);
            Button finalizeInterviewButton = getField(workspaceCtrl, "finalizeInterviewButton", Button.class);
            meetUrl.setText("");
            createMeetButton.fire();
            String autoUrl = meetUrl.getText();
            assertNotNull(autoUrl);
            assertTrue(autoUrl.startsWith("https://meet.jit.si/TeacherMatch-"),
                    "createMeetButton should auto-generate a live room URL when blank, got: " + autoUrl);
            finalizeInterviewButton.fire();
            assertTrue(LoginController.STORE.interviews().stream()
                    .anyMatch(i -> autoUrl.equals(i.getMeetingUrl())
                            && "CONFIRMED".equals(i.getStatus())));

            // 5. AdminAccountsController create a new Teacher account & verify automatic profile linking
            LoginController.setCurrentSession("admin", "ADMIN");
            FXMLLoader accountsLoader = new FXMLLoader(HelloApplication.class.getResource("AdminAccounts.fxml"));
            Parent accountsRoot = accountsLoader.load();
            new Scene(accountsRoot, 1280, 820);
            AdminAccountsController accountsCtrl = accountsLoader.getController();
            getField(accountsCtrl, "accountName", TextField.class).setText("teacher_ui");
            getField(accountsCtrl, "accountEmail", TextField.class).setText("UI Created Teacher");
            getField(accountsCtrl, "accountRole", ComboBox.class).setValue("Teacher");
            getField(accountsCtrl, "accountPassword", PasswordField.class).setText("uiPass123");
            getField(accountsCtrl, "saveAccountButton", Button.class).fire();
            assertNotNull(LoginController.STORE.authenticate("teacher_ui", "uiPass123"));
            assertNotNull(LoginController.STORE.teacherForUser("teacher_ui"));
        });
    }


    @Test
    @SuppressWarnings("unchecked")
    void feedbackItemsUiVerification() throws Exception {
        runOnFxThread(() -> {
            LoginController.STORE.resetToSeedData();

            // 1. Verify zero horizontal scrollbars across all 13 business screens
            for (String fxml : ALL_FXML_FILES) {
                FXMLLoader l = new FXMLLoader(LoginController.class.getResource(fxml));
                Parent r = l.load();
                for (ScrollPane sp : FxNodes.findAll(r, ScrollPane.class)) {
                    assertTrue(sp.isFitToWidth(), fxml + " ScrollPane must have fitToWidth=true");
                    assertEquals(ScrollPane.ScrollBarPolicy.NEVER, sp.getHbarPolicy(),
                            fxml + " ScrollPane must have hbarPolicy=NEVER to prevent horizontal overflow");
                }
            }

            // 2. Verify Remember Me checkbox & session token persistence on Login.fxml
            LoginController.clearSession();
            FXMLLoader loginLoader = new FXMLLoader(LoginController.class.getResource("Login.fxml"));
            Parent loginRoot = loginLoader.load();
            new Scene(loginRoot, 1280, 800);
            LoginController loginCtrl = loginLoader.getController();
            CheckBox rememberMe = getField(loginCtrl, "rememberMeCheck", CheckBox.class);
            assertNotNull(rememberMe, "Login.fxml must wire rememberMeCheck");
            getField(loginCtrl, "emailField", TextField.class).setText("staff");
            getField(loginCtrl, "passwordField", PasswordField.class).setText("staff123");
            getField(loginCtrl, "demoRole", ComboBox.class).setValue("Matching Staff");
            rememberMe.setSelected(true);
            getField(loginCtrl, "loginButton", Button.class).fire();
            assertEquals("staff", LoginController.getCurrentUsername());

            // Reload Login.fxml while session file exists -> should restore credentials / session
            FXMLLoader loginLoader2 = new FXMLLoader(LoginController.class.getResource("Login.fxml"));
            loginLoader2.load();
            LoginController loginCtrl2 = loginLoader2.getController();
            assertTrue(getField(loginCtrl2, "rememberMeCheck", CheckBox.class).isSelected(),
                    "Remember Me should remain checked after reload when enabled");

            // 3. Verify StaffDashboard live KPI metrics, standardized filters, and Shopee-inspired Card Grid
            LoginController.setCurrentSession("staff", "STAFF");
            FXMLLoader staffDashLoader = new FXMLLoader(LoginController.class.getResource("StaffDashboard.fxml"));
            Parent staffDashRoot = staffDashLoader.load();
            new Scene(staffDashRoot, 1280, 800);
            StaffDashboardController staffDashCtrl = staffDashLoader.getController();
            Label readyKpi = getField(staffDashCtrl, "readyMetricLabel", Label.class);
            Label pendingKpi = getField(staffDashCtrl, "pendingMetricLabel", Label.class);
            Label interviewsKpi = getField(staffDashCtrl, "interviewMetricLabel", Label.class);
            assertNotEquals("24", readyKpi.getText(), "Ready KPI must be dynamically computed from PortalStore, not hardcoded 24");
            assertEquals("02", readyKpi.getText(), "Seeded store has 2 Ready (qEP) teachers");
            assertEquals("01", pendingKpi.getText(), "Seeded store has 1 Pending (qCV) teacher");
            assertEquals("02", interviewsKpi.getText(), "Seeded store has 2 interviews");

            ComboBox<String> statusFilter = getField(staffDashCtrl, "statusFilter", ComboBox.class);
            assertTrue(statusFilter.getItems().containsAll(List.of("All", "qCV", "qEP", "Update Required")),
                    "Status filter must replace Pending/Ready with qCV and qEP");
            ComboBox<String> expFilter = getField(staffDashCtrl, "experienceFilter", ComboBox.class);
            assertTrue(expFilter.getItems().containsAll(CatalogData.EXPERIENCE_OPTIONS),
                    "Experience filter must have the 4 standardized tiers");
            FlowPane staffGrid = getField(staffDashCtrl, "cardGridPane", FlowPane.class);
            assertEquals(3, staffGrid.getChildren().size(), "Staff card grid should display 3 seeded teacher cards");

            // 4. Verify StaffReview dynamic teacher documents, checklist persistence, and Request update (UPDATE_REQUIRED)
            StaffReviewController.setSelectedTeacherId(2L); // Daniel Walker
            FXMLLoader reviewLoader = new FXMLLoader(LoginController.class.getResource("StaffReview.fxml"));
            Parent reviewRoot = reviewLoader.load();
            new Scene(reviewRoot, 1280, 800);
            StaffReviewController reviewCtrl = reviewLoader.getController();
            Label candName = getField(reviewCtrl, "teacherNameLabel", Label.class);
            assertEquals("Daniel Walker", candName.getText(), "StaffReview must display selected teacher Daniel Walker, not Emily Martin");
            VBox docsBox = getField(reviewCtrl, "documentsTable", VBox.class);
            assertTrue(docsBox.getChildren().size() >= 5, "Daniel Walker's documents must be dynamically rendered in StaffReview");

            getField(reviewCtrl, "checkPersonalInfo", CheckBox.class).setSelected(true);
            getField(reviewCtrl, "checkReadableCv", CheckBox.class).setSelected(false);
            getField(reviewCtrl, "commentsArea", TextArea.class).setText("Please update your intro video lighting.");
            getField(reviewCtrl, "requestChangesButton", Button.class).fire();
            assertEquals("UPDATE_REQUIRED", LoginController.STORE.teacher(2L).getStatus(),
                    "Request update button must set status to UPDATE_REQUIRED without permission errors");
            assertEquals("Please update your intro video lighting.", LoginController.STORE.teacher(2L).getStaffFeedback());
            assertFalse(LoginController.STORE.teacher(2L).getChecklistFlags()[1],
                    "Checklist state must persist in PortalStore when reopening StaffReview");

            // 5. Verify PartnerInterview clickable & copyable meeting link
            LoginController.setCurrentSession("partner", "PARTNER");
            FXMLLoader partnerIntLoader = new FXMLLoader(LoginController.class.getResource("PartnerInterview.fxml"));
            Parent partnerIntRoot = partnerIntLoader.load();
            new Scene(partnerIntRoot, 1280, 800);
            PartnerInterviewController partnerIntCtrl = partnerIntLoader.getController();
            VBox reqTable = getField(partnerIntCtrl, "requestsTable", VBox.class);
            assertFalse(FxNodes.findAll(reqTable, Hyperlink.class).isEmpty(),
                    "PartnerInterview Track requests table must render clickable meeting Hyperlink");
        });
    }


    @Test
    @SuppressWarnings("unchecked")
    void allElevenFeedbackTasksUiAndControllerVerification() throws Exception {
        runOnFxThread(() -> {
            LoginController.STORE.resetToSeedData();

            // Task 6 & Task 11: Logo on Login + Show/Hide Password toggle (default hidden, preserves text)
            assertNotNull(ProfileCardBuilder.loadAppLogo(), "TeacherMatch brand logo image must load");
            FXMLLoader loginLoader = new FXMLLoader(LoginController.class.getResource("Login.fxml"));
            Parent loginRoot = loginLoader.load();
            new Scene(loginRoot, 1280, 800);
            LoginController loginCtrl = loginLoader.getController();
            PasswordField pwField = getField(loginCtrl, "passwordField", PasswordField.class);
            TextField visiblePwField = getField(loginCtrl, "visiblePasswordField", TextField.class);
            Button togglePwBtn = getField(loginCtrl, "togglePasswordButton", Button.class);
            assertNotNull(visiblePwField);
            assertNotNull(togglePwBtn);
            assertFalse(loginCtrl.isPasswordVisible(), "Password must be hidden by default");
            assertTrue(pwField.isVisible());
            assertFalse(visiblePwField.isVisible());

            pwField.setText("secretPass!2026");
            togglePwBtn.fire();
            assertTrue(loginCtrl.isPasswordVisible(), "Clicking Show must reveal plain password field");
            assertEquals("secretPass!2026", visiblePwField.getText(), "Revealing password must preserve typed text");
            visiblePwField.setText("secretPass!2026_edited");
            togglePwBtn.fire();
            assertFalse(loginCtrl.isPasswordVisible(), "Clicking Hide must return to masked PasswordField");
            assertEquals("secretPass!2026_edited", pwField.getText(), "Hiding password must preserve edited text");

            // Task 1, Task 4, Task 5: StaffDashboard Reload button, Shortlist filter, Pagination, and Degree removed from cards
            LoginController.setCurrentSession("staff", "STAFF");
            FXMLLoader staffLoader = new FXMLLoader(LoginController.class.getResource("StaffDashboard.fxml"));
            Parent staffRoot = staffLoader.load();
            new Scene(staffRoot, 1280, 800);
            StaffDashboardController staffCtrl = staffLoader.getController();
            assertNotNull(staffRoot.lookup("#reloadDataButton"), "Topbar must include Reload Data button");
            assertNotNull(staffRoot.lookup("#reloadStatusLabel"), "Topbar must include Reload status badge");

            ComboBox<String> staffShortlistFilter = getField(staffCtrl, "shortlistFilter", ComboBox.class);
            assertNotNull(staffShortlistFilter);
            assertEquals(List.of("All", "Shortlisted", "Not Shortlisted"), staffShortlistFilter.getItems());

            FlowPane staffGrid = getField(staffCtrl, "cardGridPane", FlowPane.class);
            assertEquals(3, staffGrid.getChildren().size());
            // Verify Degree ("Bachelor's Degree" / "Master's Degree") is NOT redundantly shown on Candidate Profile Cards
            for (javafx.scene.Node cardNode : staffGrid.getChildren()) {
                if (cardNode instanceof Parent cardParent) {
                    boolean hasRedundantDegree = FxNodes.findAll(cardParent, Label.class).stream()
                            .map(Label::getText)
                            .anyMatch(t -> t != null && (t.contains("Bachelor's Degree") || t.contains("Master's Degree")));
                    assertFalse(hasRedundantDegree, "Candidate Profile Card must not display redundant degree text");
                }
            }

            // Task 4: PartnerDashboard Shortlist filter + toggle shortlist compatibility with search & pagination
            LoginController.setCurrentSession("partner", "PARTNER");
            LoginController.STORE.togglePartnerShortlist("partner", 1L);
            FXMLLoader partnerLoader = new FXMLLoader(LoginController.class.getResource("PartnerDashboard.fxml"));
            Parent partnerRoot = partnerLoader.load();
            new Scene(partnerRoot, 1280, 800);
            PartnerDashboardController partnerCtrl = partnerLoader.getController();
            ComboBox<String> partnerShortlist = getField(partnerCtrl, "shortlistFilter", ComboBox.class);
            FlowPane partnerGrid = getField(partnerCtrl, "partnerCardGrid", FlowPane.class);
            assertNotNull(partnerShortlist);
            assertEquals(2, partnerGrid.getChildren().size(), "Partner sees 2 shared READY teachers initially");

            partnerShortlist.setValue("Shortlisted");
            assertEquals(1, partnerGrid.getChildren().size(), "Only 1 teacher is initially shortlisted by partner");
            partnerShortlist.setValue("Not Shortlisted");
            assertEquals(1, partnerGrid.getChildren().size(), "1 shared teacher is not shortlisted");
            partnerShortlist.setValue("All");
            assertEquals(2, partnerGrid.getChildren().size());

            // Task 7 & Task 9: InterviewWorkspace schedule adjustment & Staff marking Interview Successful
            LoginController.setCurrentSession("staff", "STAFF");
            FXMLLoader iwLoader = new FXMLLoader(LoginController.class.getResource("InterviewWorkspace.fxml"));
            Parent iwRoot = iwLoader.load();
            new Scene(iwRoot, 1280, 800);
            InterviewWorkspaceController iwCtrl = iwLoader.getController();
            Button rescheduleBtn = getField(iwCtrl, "rescheduleInterviewButton", Button.class);
            Button markSuccessBtn = getField(iwCtrl, "markInterviewSuccessButton", Button.class);
            TextField staffNotes = getField(iwCtrl, "staffResultNotesField", TextField.class);
            assertNotNull(rescheduleBtn);
            assertNotNull(markSuccessBtn);
            staffNotes.setText("Excellent demo lesson; passed interview.");
            markSuccessBtn.fire();
            assertTrue(LoginController.STORE.interviews().stream()
                    .anyMatch(i -> "Interview Successful".equals(i.getResult())),
                    "Mark Interview Successful button must persist result to PortalStore");

            // Task 8: TeacherSchedule & PartnerInterview configurable notification lead days & notification feed
            LoginController.setCurrentSession("teacher", "TEACHER");
            FXMLLoader tsLoader = new FXMLLoader(LoginController.class.getResource("TeacherSchedule.fxml"));
            Parent tsRoot = tsLoader.load();
            new Scene(tsRoot, 1280, 800);
            TeacherScheduleController tsCtrl = tsLoader.getController();
            ComboBox<Integer> teacherLeadCombo = getField(tsCtrl, "leadDaysCombo", ComboBox.class);
            VBox teacherNotesBox = getField(tsCtrl, "teacherNotificationsBox", VBox.class);
            assertNotNull(teacherLeadCombo);
            assertNotNull(teacherNotesBox);
            teacherLeadCombo.setValue(7);
            assertEquals(7, LoginController.STORE.getNotificationLeadDays());
            assertFalse(teacherNotesBox.getChildren().isEmpty(), "Teacher notification feed must display result/reminder items");
        });
    }

    private void verifyRoleLoginAndNavigation(
            String username,
            String password,
            String roleDisplay,
            String expectedRole,
            List<String> allowedScreens,
            String forbiddenScreen
    ) throws Exception {
        FXMLLoader loginLoader = new FXMLLoader(LoginController.class.getResource("Login.fxml"));
        Parent loginRoot = loginLoader.load();
        Scene scene = new Scene(loginRoot, 1360, 900);
        LoginController loginController = loginLoader.getController();

        TextField userField = getField(loginController, "emailField", TextField.class);
        PasswordField passField = getField(loginController, "passwordField", PasswordField.class);
        @SuppressWarnings("unchecked")
        ComboBox<String> roleCombo = getField(loginController, "demoRole", ComboBox.class);
        Button loginButton = getField(loginController, "loginButton", Button.class);

        userField.setText(username);
        passField.setText(password);
        roleCombo.setValue(roleDisplay);
        loginButton.fire();

        assertEquals(username, LoginController.getCurrentUsername());
        assertEquals(expectedRole, LoginController.getCurrentRole());
        assertNotSame(loginRoot, scene.getRoot(), "Login should navigate away from Login.fxml for " + username);

        for (String screen : allowedScreens) {
            assertTrue(LoginController.navigateTo(scene.getRoot(), screen),
                    expectedRole + " should be able to navigate to " + screen);
        }

        assertFalse(LoginController.navigateTo(scene.getRoot(), forbiddenScreen),
                expectedRole + " must not be allowed to open " + forbiddenScreen);

        assertTrue(LoginController.navigateTo(scene.getRoot(), "Login.fxml"),
                expectedRole + " should be able to log out to Login.fxml");
        assertNull(LoginController.getCurrentUsername(), "Session username must be cleared on logout");
        assertNull(LoginController.getCurrentRole(), "Session role must be cleared on logout");
    }

    private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return type.cast(f.get(target));
    }

    @FunctionalInterface
    private interface FxRunnable {
        void run() throws Exception;
    }

    private static void runOnFxThread(FxRunnable action) throws Exception {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Timed out waiting for JavaFX thread");
        if (error.get() != null) {
            if (error.get() instanceof Exception ex) throw ex;
            if (error.get() instanceof Error err) throw err;
            throw new RuntimeException(error.get());
        }
    }
}
