package com.example.tmver36;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
