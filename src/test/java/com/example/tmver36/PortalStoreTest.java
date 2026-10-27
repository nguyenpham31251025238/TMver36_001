package com.example.tmver36;

import com.example.tmver36.model.AppUser;
import com.example.tmver36.model.AvailabilitySlot;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PortalStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void demoAuthenticationAndPasswordHashingWork() {
        PortalStore store = new PortalStore(tempDir.resolve("auth"));

        assertNotNull(store.authenticate("admin", "admin123"));
        assertNotNull(store.authenticate("staff", "staff123"));
        assertNotNull(store.authenticate("partner", "partner123"));
        assertNotNull(store.authenticate("teacher", "teacher123"));

        assertNull(store.authenticate("admin", "wrongpass"));
        assertNull(store.authenticate("unknown", "admin123"));

        // Legacy plaintext fallback upgrades hash automatically
        AppUser legacy = AppUser.fromStoredHash("legacyUser", "plainSecret", "TEACHER", "Legacy Teacher", true);
        assertFalse(legacy.getPasswordHash().contains(":"));
        assertTrue(legacy.verifyPassword("plainSecret"));
        assertTrue(legacy.getPasswordHash().contains(":"));
        assertTrue(legacy.verifyPassword("plainSecret"));
    }

    @Test
    void accountManagementAndDynamicTeacherLinkingWork() {
        PortalStore store = new PortalStore(tempDir.resolve("accounts"));

        assertTrue(store.saveOrUpdateUser("admin", "teacher2", "Michael Brown", "TEACHER", "pass1234", true));
        long teacher2Id = store.teacherIdForUser("teacher2");
        assertTrue(teacher2Id > 3L);

        TeacherProfile profile = store.teacherForUser("teacher2");
        assertNotNull(profile);
        assertEquals("Michael Brown", profile.getName());

        // Disable account blocks authentication
        assertTrue(store.setUserActive("admin", "teacher2", false));
        assertNull(store.authenticate("teacher2", "pass1234"));

        // Re-enable account restores authentication without losing password hash
        assertTrue(store.setUserActive("admin", "teacher2", true));
        assertNotNull(store.authenticate("teacher2", "pass1234"));

        // Primary admin account cannot be deleted or demoted
        assertFalse(store.deleteUser("admin", "admin"));
        assertFalse(store.updateUserRole("admin", "admin", "STAFF"));

        // Non-admin account can have role updated and be deleted
        assertTrue(store.updateUserRole("admin", "teacher2", "PARTNER"));
        assertEquals("PARTNER", store.userRole("teacher2"));
        assertTrue(store.deleteUser("admin", "teacher2"));
        assertFalse(store.userExists("teacher2"));
    }

    @Test
    void teacherProfileDocumentsSlotsAndInterviewWorkflowsWork() throws Exception {
        PortalStore store = new PortalStore(tempDir.resolve("workflow"));

        TeacherProfile emily = store.teacherForUser("teacher");
        assertNotNull(emily);
        emily.setFullName("Emily Grace Martin");
        emily.setSubject("IELTS / Academic English");
        emily.setDegree("Master's Degree");
        emily.setMajor("Applied Linguistics");
        emily.setExpectedSalary("$2,000 - $2,500");
        emily.setCertificates("CELTA, IELTS, TESOL");
        emily.setPhone("+84 988 111 222");
        emily.setBio("Updated teacher biography.");
        assertTrue(store.saveTeacherProfile("teacher", emily));

        // Upload local file document and verify copy into store data dir
        Path samplePng = tempDir.resolve("cert_scan.png");
        Files.writeString(samplePng, "fake-png-content", StandardCharsets.UTF_8);
        TeacherDocument addedDoc = store.addOrReplaceDocument(
                "teacher",
                emily.getId(),
                "cert_scan.png",
                "Certificates",
                samplePng.toString()
        );
        assertNotNull(addedDoc);
        assertTrue(Files.isRegularFile(Path.of(addedDoc.location())));

        // Add future availability slot
        LocalDate futureDate = LocalDate.now().plusDays(10);
        LocalDateTime start = LocalDateTime.of(futureDate, LocalTime.of(10, 0));
        LocalDateTime end = LocalDateTime.of(futureDate, LocalTime.of(11, 0));
        assertTrue(store.addAvailabilitySlot("teacher", emily.getId(), start, end));
        // Overlapping slot rejected
        assertFalse(store.addAvailabilitySlot("teacher", emily.getId(), start.plusMinutes(15), end.plusMinutes(15)));

        // Partner books the slot
        assertTrue(store.requestInterview(emily.getId(), "partner", start.toString()));
        // Slot was consumed by booking
        assertTrue(store.availabilitySlots(emily.getId()).stream().noneMatch(s -> s.startAt().equals(start)));

        Interview booked = store.visibleInterviews("PARTNER", "partner").stream()
                .filter(i -> i.getDateTime().equals(start.toString()))
                .findFirst()
                .orElse(null);
        assertNotNull(booked);
        assertEquals("PENDING", booked.getStatus());
        assertNotNull(booked.getMeetingUrl(), "Booked interview must immediately have an auto-generated meeting room URL");
        assertTrue(booked.getMeetingUrl().startsWith("https://meet.jit.si/TeacherMatch-" + booked.getId() + "-"),
                "Auto-generated meeting URL must be a unique Jitsi room URL: " + booked.getMeetingUrl());

        // Staff confirms interview with Meet link
        String meetLink = "https://meet.google.com/tm-emily-test";
        assertTrue(store.updateInterview("staff", booked.getId(), "CONFIRMED", booked.getResult(), booked.getFeedback(), meetLink));

        // Partner submits evaluation feedback
        assertTrue(store.updatePartnerFeedback(booked.getId(), "partner", "PROCEED", "Suitability: Qualified\nStrong demo."));
        Interview updated = store.visibleInterviews("TEACHER", "teacher").stream()
                .filter(i -> i.getId() == booked.getId())
                .findFirst()
                .orElseThrow();
        assertEquals("CONFIRMED", updated.getStatus());
        assertEquals("PROCEED", updated.getResult());
        assertEquals(meetLink, updated.getMeetingUrl());
    }

    @Test
    void distinctTeacherAvatarsRealDocumentsChecklistAndStatusCatalogWork() throws Exception {
        PortalStore store = new PortalStore(tempDir.resolve("catalog"));

        TeacherProfile emily = store.teacher(1L);
        TeacherProfile daniel = store.teacher(2L);
        TeacherProfile sarah = store.teacher(3L);

        assertNotNull(emily);
        assertNotNull(daniel);
        assertNotNull(sarah);

        // Feedback I.6 & II.8: Each teacher has their own distinct avatar and documents (never hardcoded Emily Martin)
        assertNotEquals(emily.getAvatarUrl(), daniel.getAvatarUrl());
        assertNotEquals(emily.getAvatarUrl(), sarah.getAvatarUrl());
        assertNotEquals(daniel.getAvatarUrl(), sarah.getAvatarUrl());

        List<TeacherDocument> danielDocs = store.documents(2L);
        assertFalse(danielDocs.isEmpty());
        assertTrue(danielDocs.stream().allMatch(d -> d.fileName().contains("Daniel_Walker")),
                "Daniel Walker's documents must belong to Daniel Walker, not Emily Martin");
        for (TeacherDocument doc : danielDocs) {
            Path resolved = store.resolveDocumentLocalFile(doc);
            assertNotNull(resolved, "Sample document must resolve to a real local file: " + doc.fileName());
            assertTrue(Files.isRegularFile(resolved));
        }

        // Feedback II.7 & II.9: Staff can mark UPDATE_REQUIRED / NEED_UPDATE, qCV, qEP and persist 5-item checklist + feedback
        boolean[] flags = new boolean[]{true, true, false, true, false};
        assertTrue(store.saveReviewState("staff", 3L, "NEED_UPDATE", "Please upload high-res TESOL scan.", flags, "Updated Sarah bio"));
        assertEquals("UPDATE_REQUIRED", store.teacher(3L).getStatus());
        assertEquals("Update Required", CatalogData.formatShortStatusCode(store.teacher(3L).getStatus()));
        assertEquals("Please upload high-res TESOL scan.", store.teacher(3L).getStaffFeedback());
        assertArrayEquals(flags, store.teacher(3L).getChecklistFlags());

        assertTrue(store.status("staff", 3L, "qCV"));
        assertEquals("PENDING", store.teacher(3L).getStatus());
        assertEquals("qCV", CatalogData.formatShortStatusCode(store.teacher(3L).getStatus()));

        assertTrue(store.status("staff", 3L, "qEP"));
        assertEquals("READY", store.teacher(3L).getStatus());
        assertEquals("qEP", CatalogData.formatShortStatusCode(store.teacher(3L).getStatus()));

        // CatalogData filters: Nationality A-Z + Other, Experience 4 tiers, Multi-certificate, Salary, Major/Degree
        assertTrue(CatalogData.WORLD_COUNTRIES_AZ.size() > 190);
        assertEquals("Other", CatalogData.WORLD_COUNTRIES_AZ.get(CatalogData.WORLD_COUNTRIES_AZ.size() - 1));
        assertEquals(List.of("Under 1 year", "1–2 years", "3–5 years", "Over 5 years"), CatalogData.EXPERIENCE_OPTIONS);
        assertTrue(CatalogData.matchesCertificatesFilter("CELTA, IELTS, TESOL", Set.of("CELTA", "IELTS")));
        assertFalse(CatalogData.matchesCertificatesFilter("TEFL", Set.of("CELTA", "DELTA")));
        assertTrue(CatalogData.matchesSalaryFilter("$2,100", "$2,000 - $2,500"));
        assertTrue(CatalogData.matchesMajorOrDegreeFilter("Master's Degree", "Applied Linguistics", "Applied Linguistics"));
    }

    @Test
    void fullStatePersistsAcrossPortalStoreReloadAndCloudSync() throws Exception {
        Path dataDir = tempDir.resolve("persistence");
        PortalStore store1 = new PortalStore(dataDir);

        // 1. Add user
        assertTrue(store1.saveOrUpdateUser("admin", "partner2", "Global Academy", "PARTNER", "global123", true));
        // 2. Grant partner2 access to teacher 3 and set teacher 3 READY (qEP)
        assertTrue(store1.status("staff", 3L, "qEP"));
        assertTrue(store1.grant("staff", "partner2", 3L));
        // 3. Save partner evaluation
        assertTrue(store1.savePartnerEvaluation("partner2", 3L, "Shortlisted", "Great IELTS background"));
        // 4. Add availability slot for teacher 1
        LocalDateTime slotStart = LocalDate.now().plusDays(12).atTime(15, 0);
        LocalDateTime slotEnd = LocalDate.now().plusDays(12).atTime(16, 0);
        assertTrue(store1.addAvailabilitySlot("teacher", 1L, slotStart, slotEnd));
        // 5. Add document for teacher 1
        TeacherDocument doc = store1.addOrReplaceDocument("teacher", 1L, "Portfolio.pdf", "CV", "https://example.com/portfolio.pdf");
        assertNotNull(doc);

        assertTrue(Files.isRegularFile(store1.getStateFilePath()));

        // Reload in a brand-new PortalStore instance pointing to the same dataDir
        PortalStore store2 = new PortalStore(dataDir);

        assertNotNull(store2.authenticate("partner2", "global123"));
        assertEquals("READY", store2.teacher(3L).getStatus());
        assertTrue(store2.canPartnerAccessTeacher("partner2", 3L));

        String[] eval = store2.partnerEvaluation("partner2", 3L);
        assertNotNull(eval);
        assertEquals("Shortlisted", eval[0]);
        assertEquals("Great IELTS background", eval[1]);

        List<AvailabilitySlot> slots = store2.availabilitySlots(1L);
        assertTrue(slots.stream().anyMatch(s -> s.startAt().equals(slotStart) && s.endAt().equals(slotEnd)));

        List<TeacherDocument> docs = store2.documents(1L);
        assertTrue(docs.stream().anyMatch(d -> "Portfolio.pdf".equals(d.fileName()) && "CV".equals(d.type())));
    }

    @Test
    void multiDeviceCloudSyncPropagatesAccountsAndProfilesAcrossStores() {
        PortalStore deviceA = new PortalStore(tempDir.resolve("deviceA"), new com.example.tmver36.service.CloudSyncService());
        PortalStore deviceB = new PortalStore(tempDir.resolve("deviceB"), new com.example.tmver36.service.CloudSyncService());

        if (!deviceA.getCloudSync().isEnabled()) {
            return;
        }

        String uniqueUser = "sync_teacher_" + (System.currentTimeMillis() % 100000);
        try {
            assertTrue(deviceA.saveOrUpdateUser("admin", uniqueUser, "Cloud Synced Teacher", "TEACHER", "cloudPass123", true));
            TeacherProfile pA = deviceA.teacherForUser(uniqueUser);
            assertNotNull(pA);
            pA.setExpectedSalary("$2,500 - $3,000");
            pA.setMajor("TESOL & Applied Linguistics");
            assertTrue(deviceA.saveTeacherProfile(uniqueUser, pA));

            // Device B pulls from shared Supabase backend on authenticate / syncNow
            assertNotNull(deviceB.authenticate(uniqueUser, "cloudPass123"),
                    "Account created on Device A must be immediately usable on Device B via shared database");
            TeacherProfile pB = deviceB.teacherForUser(uniqueUser);
            assertNotNull(pB);
            assertEquals("$2,500 - $3,000", pB.getExpectedSalary());
            assertEquals("TESOL & Applied Linguistics", pB.getMajor());
        } finally {
            deviceA.deleteUser("admin", uniqueUser);
            deviceA.resetToSeedData();
        }
    }

    @Test
    void reloadDataAvatarSyncAndVideoAuthPlaybackWork() throws Exception {
        Path dir1 = tempDir.resolve("storeA");
        PortalStore storeA = new PortalStore(dir1);

        // Task 1: Reload latest data & error state handling
        PortalStore.ReloadResult okReload = storeA.reloadLatestData();
        assertNotNull(okReload);
        assertTrue(okReload.success());
        assertNotNull(okReload.timestamp());

        storeA.setSimulatedReloadError("Simulated network timeout");
        PortalStore.ReloadResult failReload = storeA.reloadLatestData();
        assertFalse(failReload.success());
        assertTrue(failReload.message().contains("Simulated network timeout"));

        // Task 2: Avatar update by Teacher persists base64 & file and restores even if local file is missing
        Path customAvatar = tempDir.resolve("new_teacher_avatar.png");
        byte[] samplePngBytes = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0ioAAAAASUVORK5CYII="
        );
        Files.write(customAvatar, samplePngBytes);
        String storedAvatarPath = storeA.updateTeacherAvatar("teacher", 1L, customAvatar);
        assertNotNull(storedAvatarPath);
        assertFalse(storeA.teacher(1L).getAvatarBase64().isBlank(), "Avatar base64 must be stored for cross-role/session sync");

        // Reload in another PortalStore instance and delete local path to test base64/shared restoration for Staff/Partner
        PortalStore storeB = new PortalStore(dir1);
        TeacherProfile emilyB = storeB.teacher(1L);
        assertNotNull(emilyB);
        assertEquals(storedAvatarPath, emilyB.getAvatarUrl());
        Files.deleteIfExists(Path.of(storedAvatarPath));
        Path restoredAvatar = storeB.resolveTeacherAvatarFile(emilyB);
        assertNotNull(restoredAvatar, "Staff/Partner must resolve Teacher avatar from persisted store even if original file was removed");
        assertTrue(Files.isRegularFile(restoredAvatar));

        // Task 3: Salary, Nationality, and UTC+7 timestamp normalization & role access control
        emilyB.setExpectedSalary("2200");
        emilyB.setNationality("united kingdom");
        assertFalse(storeB.saveTeacherProfile("partner", emilyB), "Partner must not be allowed to modify Teacher profile");
        assertTrue(storeB.saveTeacherProfile("teacher", emilyB));
        assertEquals("$2,200", storeB.teacher(1L).getExpectedSalary());
        assertEquals("United Kingdom", storeB.teacher(1L).getNationality());
        assertTrue(storeB.teacher(1L).getUpdatedAt().contains("UTC+7"));

        // Task 10: Video upload, storage resolution, category lookup, and role-based authorization
        Path customVideo = tempDir.resolve("demo_teaching_lesson.mp4");
        Files.write(customVideo, new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70, 0x6d, 0x70, 0x34, 0x32});
        TeacherDocument videoDoc = storeB.addOrReplaceDocument("teacher", 1L, "demo_teaching_lesson.mp4", "Intro video", customVideo.toString());
        assertNotNull(videoDoc);
        assertTrue(storeB.isVideoDocument(videoDoc));

        TeacherDocument foundVideo = ProfileCardBuilder.findDocumentByCategory(storeB, 1L, "Intro video");
        assertNotNull(foundVideo);
        assertEquals(videoDoc.id(), foundVideo.id(), "Video lookup must return the video document, never fallback to PDF CV");

        // Role authorization check for opening video:
        assertTrue(storeB.canUserAccessDocument("teacher", foundVideo));
        assertTrue(storeB.canUserAccessDocument("staff", foundVideo));
        assertTrue(storeB.canUserAccessDocument("partner", foundVideo), "Partner granted access to Teacher 1 can view video");

        // Revoke partner access -> Partner must be blocked from opening Teacher 1 video
        storeB.revoke("staff", "partner", 1L);
        assertFalse(storeB.canUserAccessDocument("partner", foundVideo), "Unshared Partner must be blocked from accessing Teacher video");
        assertFalse(storeB.openDocument("partner", foundVideo), "openDocument must reject unauthorized Partner");
        storeB.grant("staff", "partner", 1L);
        assertTrue(storeB.canUserAccessDocument("partner", foundVideo));
        assertNotNull(storeB.resolveDocumentLocalFile(foundVideo));
    }

    @Test
    void interviewScheduleConflictDetectionConfigurableNotificationsAndStaffResultSyncWork() {
        PortalStore store = new PortalStore(tempDir.resolve("schedule_notify"));

        // Task 4: Shortlist toggle & filter matching
        assertFalse(store.isTeacherShortlistedByPartner("partner", 2L));
        assertTrue(store.togglePartnerShortlist("partner", 2L));
        assertTrue(store.isTeacherShortlistedByPartner("partner", 2L));
        assertTrue(CatalogData.matchesShortlistFilter(true, "Shortlisted"));
        assertFalse(CatalogData.matchesShortlistFilter(true, "Not Shortlisted"));
        assertTrue(CatalogData.matchesShortlistFilter(false, "Not Shortlisted"));

        // Task 7: Interview reschedule & conflict detection
        List<Interview> all = store.interviews();
        assertFalse(all.isEmpty());
        Interview first = all.get(0); // Teacher 1 (Emily Martin)
        LocalDate targetDate = LocalDate.now().plusDays(2);
        LocalDateTime slot1Start = LocalDateTime.of(targetDate, LocalTime.of(14, 0));
        LocalDateTime slot1End = LocalDateTime.of(targetDate, LocalTime.of(14, 30));

        // Staff reschedules interview #1
        assertTrue(store.rescheduleInterview("staff", first.getId(), slot1Start, slot1End),
                "Valid reschedule by Staff should succeed without conflict");
        assertEquals(slot1Start.toString(), store.findInterview(first.getId()).getDateTime());

        // Reschedule another interview to overlap with interview #1 -> must detect conflict!
        Interview second = all.get(1);
        assertFalse(store.rescheduleInterview("staff", second.getId(),
                slot1Start.plusMinutes(10), slot1End.plusMinutes(10)),
                "Overlapping interview for same teacher must be rejected");
        assertTrue(store.getLastValidationError().toLowerCase().contains("conflict"));

        // Unauthorized role cannot reschedule
        assertFalse(store.rescheduleInterview("teacher", first.getId(),
                slot1Start.plusHours(2), slot1End.plusHours(2)),
                "Teacher role cannot arbitrarily reschedule interview slot");

        // Task 8: Configurable lead-days notifications for Teacher and Partner
        assertTrue(store.setNotificationLeadDays("staff", 3));
        assertEquals(3, store.getNotificationLeadDays());
        assertTrue(store.notificationsForUser("teacher").stream()
                .anyMatch(n -> "INTERVIEW_REMINDER".equals(n.getCategory()) || "INTERVIEW_RESCHEDULED".equals(n.getCategory())),
                "Teacher must receive upcoming interview reminder within configured 3 lead days");
        assertTrue(store.notificationsForUser("partner").stream()
                .anyMatch(n -> "INTERVIEW_REMINDER".equals(n.getCategory()) || "INTERVIEW_RESCHEDULED".equals(n.getCategory())),
                "Partner must receive upcoming interview reminder within configured 3 lead days");

        // Task 9: Staff updates interview result to Interview Successful -> syncs & notifies Teacher and Partner
        assertFalse(store.updateInterviewResultByStaff("partner", first.getId(), "Interview Successful", "Attempt by partner"),
                "Non-staff must not be able to call updateInterviewResultByStaff");
        assertTrue(store.updateInterviewResultByStaff("staff", first.getId(), "Interview Successful", "Passed demo lesson with high score"));

        Interview updated = store.findInterview(first.getId());
        assertEquals("CONFIRMED", updated.getStatus());
        assertEquals("Interview Successful", updated.getResult());
        assertTrue(updated.getFeedback().contains("Passed demo lesson with high score"));

        assertTrue(store.notificationsForUser("teacher").stream()
                .anyMatch(n -> "INTERVIEW_RESULT".equals(n.getCategory()) && n.getMessage().contains("Interview Successful")),
                "Teacher must be notified when Staff updates interview result");
        assertTrue(store.notificationsForUser("partner").stream()
                .anyMatch(n -> "INTERVIEW_RESULT".equals(n.getCategory()) && n.getMessage().contains("Interview Successful")),
                "Partner must be notified when Staff updates interview result");
    }
}
