package com.example.tmver36;

import com.example.tmver36.model.AppUser;
import com.example.tmver36.model.AvailabilitySlot;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;
import com.example.tmver36.service.PortalStore;
import com.example.tmver36.service.VideoValidator;
import java.io.RandomAccessFile;
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
        Files.write(customVideo, VideoValidator.buildValidMp4Bytes(25.0));
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

    @Test
    void group1TeacherProfileCreatedMarkedReadyDistributesToPartnersAndPersistsAcrossRefreshAndRelogin() {
        Path dir = tempDir.resolve("group1_ready_partner");
        PortalStore store = new PortalStore(dir);

        // 1. Create a new Teacher account and profile
        assertTrue(store.saveOrUpdateUser("admin", "teacher_new", "Liam O'Connor", "TEACHER", "liamPass123", true));
        TeacherProfile liam = store.teacherForUser("teacher_new");
        assertNotNull(liam);
        liam.setNationality("Ireland");
        liam.setSubject("ESL & Academic Writing");
        liam.setExpectedSalary("$2,200");
        liam.setExperience("3-5 years");
        liam.setBio("Experienced ESL educator ready for placement.");
        assertTrue(store.saveTeacherProfile("teacher_new", liam));
        long liamId = liam.getId();

        // Also create a second Partner account to verify distribution to all active partners
        assertTrue(store.saveOrUpdateUser("admin", "partner_hanoi", "Hanoi International School", "PARTNER", "hanoi123", true));

        // 2. Staff sees the newly created profile immediately
        assertTrue(store.visibleTeachers("staff", "STAFF").stream().anyMatch(t -> t.getId() == liamId),
                "Staff must see newly created Teacher profile");
        // Before Mark as Ready, Partner cannot see PENDING profile
        assertFalse(store.visibleTeachers("partner", "PARTNER").stream().anyMatch(t -> t.getId() == liamId),
                "Partner must not see profile while status is still PENDING");

        // 3. Staff clicks Mark as Ready (via saveReviewState or status)
        boolean[] allChecked = new boolean[]{true, true, true, true, true};
        assertTrue(store.saveReviewState("staff", liamId, "READY", "All documents verified.", allChecked, liam.getBio()));
        assertEquals("READY", store.teacher(liamId).getStatus());

        // 4. Partner immediately sees the READY profile without extra manual grant step
        assertTrue(store.canPartnerAccessTeacher("partner", liamId),
                "Marking teacher READY must automatically grant access to active partner");
        assertTrue(store.canPartnerAccessTeacher("partner_hanoi", liamId),
                "Marking teacher READY must automatically grant access to all active partner accounts");
        assertTrue(store.visibleTeachers("partner", "PARTNER").stream().anyMatch(t -> t.getId() == liamId),
                "Partner visibleTeachers must include the newly READY teacher");

        // 5. After refresh (reloadLatestData) and re-login (new PortalStore instance from persisted state), Partner still sees profile
        assertTrue(store.reloadLatestData().success());
        assertTrue(store.visibleTeachers("partner", "PARTNER").stream().anyMatch(t -> t.getId() == liamId));

        PortalStore reloginStore = new PortalStore(dir);
        assertNotNull(reloginStore.authenticate("partner", "partner123"));
        assertTrue(reloginStore.visibleTeachers("partner", "PARTNER").stream().anyMatch(t -> t.getId() == liamId),
                "Partner must still see READY teacher after logging out and logging back in");
        assertNotNull(reloginStore.authenticate("partner_hanoi", "hanoi123"));
        assertTrue(reloginStore.visibleTeachers("partner_hanoi", "PARTNER").stream().anyMatch(t -> t.getId() == liamId),
                "Custom Partner account must also see READY teacher after re-login");

        // 6. Staff can still explicitly revoke access for a specific Partner and it stays revoked across reload
        assertTrue(reloginStore.revoke("staff", "partner_hanoi", liamId));
        assertFalse(reloginStore.canPartnerAccessTeacher("partner_hanoi", liamId));
        PortalStore afterRevokeStore = new PortalStore(dir);
        assertFalse(afterRevokeStore.canPartnerAccessTeacher("partner_hanoi", liamId),
                "Explicitly revoked partner access must remain revoked after reload");
        assertTrue(afterRevokeStore.canPartnerAccessTeacher("partner", liamId),
                "Other active partners retain access");
    }

    @Test
    void group2VideoMp4PlaybackSizeLimitDurationValidationGoogleDriveLinksAndTeachingVideoRenameWork() throws Exception {
        Path dir = tempDir.resolve("group2_video");
        PortalStore store = new PortalStore(dir);

        // 1. Legacy "Demo video" / "Demo Video" data is normalized and preserved as "Teaching video"
        TeacherDocument legacyDoc = new TeacherDocument(99L, 1L, "legacy_lesson.mp4", "Demo Video", "2026-10-27 10:00 (UTC+7)", "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOpQrStUvWxYz12345/view");
        assertEquals("Teaching video", legacyDoc.type(), "Legacy Demo Video type must normalize to Teaching video");
        assertTrue(store.documents(1L).stream().anyMatch(d -> "Teaching video".equalsIgnoreCase(d.type())),
                "Seeded video documents must use Teaching video type");
        assertTrue(store.documents(1L).stream().noneMatch(d -> "Demo video".equalsIgnoreCase(d.type())),
                "No document should remain labeled Demo video");

        // 2. Valid MP4 upload for Intro video (<= 60s) and Teaching video (<= 300s)
        Path validIntroMp4 = tempDir.resolve("valid_intro_45s.mp4");
        Files.write(validIntroMp4, VideoValidator.buildValidMp4Bytes(45.0));
        TeacherDocument introDoc = store.addOrReplaceDocument("teacher", 1L, "valid_intro_45s.mp4", "Intro video", validIntroMp4.toString());
        assertNotNull(introDoc, "45s Intro MP4 (< 60s, < 100MB) must be accepted");

        Path validTeachingMp4 = tempDir.resolve("valid_teaching_240s.mp4");
        Files.write(validTeachingMp4, VideoValidator.buildValidMp4Bytes(240.0));
        TeacherDocument teachingDoc = store.addOrReplaceDocument("teacher", 1L, "valid_teaching_240s.mp4", "Teaching video", validTeachingMp4.toString());
        assertNotNull(teachingDoc, "240s Teaching MP4 (< 300s, < 100MB) must be accepted");

        // 3. Duration limit enforcement on local MP4 files:
        // Intro video > 60s (e.g. 75s) must be rejected
        Path longIntroMp4 = tempDir.resolve("long_intro_75s.mp4");
        Files.write(longIntroMp4, VideoValidator.buildValidMp4Bytes(75.0));
        assertNull(store.addOrReplaceDocument("teacher", 1L, "long_intro_75s.mp4", "Intro video", longIntroMp4.toString()),
                "Intro video > 60s must be rejected");
        assertTrue(store.getLastValidationError().contains("1 minute"),
                "Error message must explain the 1-minute limit: " + store.getLastValidationError());

        // Teaching video > 300s (e.g. 360s) must be rejected
        Path longTeachingMp4 = tempDir.resolve("long_teaching_360s.mp4");
        Files.write(longTeachingMp4, VideoValidator.buildValidMp4Bytes(360.0));
        assertNull(store.addOrReplaceDocument("teacher", 1L, "long_teaching_360s.mp4", "Teaching video", longTeachingMp4.toString()),
                "Teaching video > 300s must be rejected");
        assertTrue(store.getLastValidationError().contains("5 minutes"),
                "Error message must explain the 5-minute limit: " + store.getLastValidationError());

        // 4. File size limit (>= 100 MB) must be blocked with Google Drive instructions
        Path hugeMp4 = tempDir.resolve("oversized_100mb.mp4");
        try (RandomAccessFile raf = new RandomAccessFile(hugeMp4.toFile(), "rw")) {
            byte[] header = VideoValidator.buildValidMp4Bytes(30.0);
            raf.write(header);
            raf.setLength(VideoValidator.MAX_VIDEO_FILE_BYTES); // Exactly 100 MB (>= 100 MB limit)
        }
        assertNull(store.addOrReplaceDocument("teacher", 1L, "oversized_100mb.mp4", "Teaching video", hugeMp4.toString()),
                "File >= 100 MB must be blocked");
        assertTrue(store.getLastValidationError().toLowerCase().contains("google drive"),
                "Oversized video error must instruct Teacher to upload to Google Drive and paste the link");
        Files.deleteIfExists(hugeMp4);

        // 5. Google Drive link support + duration verification for both Intro video and Teaching video
        // Invalid non-Drive URL rejected
        assertNull(store.addOrReplaceDocument("teacher", 1L, "bad_link", "Intro video", "https://example.com/not-drive.mp4", 30.0),
                "Non-Google Drive URL must be rejected for video link");

        // Valid Google Drive URL but unverified duration (when Drive cannot be probed offline) rejected
        String driveIntroUrl = "https://drive.google.com/file/d/1IntroVideoFileId9876543210/view?usp=sharing";
        assertNull(store.addOrReplaceDocument("teacher", 1L, "Drive_Intro_Unverified", "Intro video", driveIntroUrl, null),
                "Google Drive link without verified duration must not be assumed valid");

        // Google Drive URL with duration > 60s for Intro video rejected
        assertNull(store.addOrReplaceDocument("teacher", 1L, "Drive_Intro_TooLong", "Intro video", driveIntroUrl, 90.0),
                "Google Drive Intro video > 60s must be rejected");

        // Google Drive URL with duration > 300s for Teaching video rejected
        String driveTeachingUrl = "https://drive.google.com/file/d/1TeachingVideoFileId9876543210/view?usp=sharing";
        assertNull(store.addOrReplaceDocument("teacher", 1L, "Drive_Teaching_TooLong", "Teaching video", driveTeachingUrl, 345.0),
                "Google Drive Teaching video > 300s must be rejected");

        // Valid Google Drive URL with valid duration <= 60s (Intro) and <= 300s (Teaching) accepted
        TeacherDocument driveIntroDoc = store.addOrReplaceDocument("teacher", 1L, "Drive_Intro_50s", "Intro video", driveIntroUrl, 50.0);
        assertNotNull(driveIntroDoc, "Valid Google Drive Intro video (50s <= 60s) must be saved");
        TeacherDocument driveTeachingDoc = store.addOrReplaceDocument("teacher", 1L, "Drive_Teaching_210s", "Teaching video", driveTeachingUrl, 210.0);
        assertNotNull(driveTeachingDoc, "Valid Google Drive Teaching video (210s <= 300s) must be saved");

        // 6. Role-based playback & persistence after reload/refresh
        // Re-upload local MP4 for Intro video so we verify both local MP4 and Google Drive Teaching video after reload
        store.addOrReplaceDocument("teacher", 1L, "valid_intro_45s.mp4", "Intro video", validIntroMp4.toString());
        PortalStore reloaded = new PortalStore(dir);
        TeacherDocument reloadedIntro = ProfileCardBuilder.findDocumentByCategory(reloaded, 1L, "Intro video");
        TeacherDocument reloadedTeaching = ProfileCardBuilder.findDocumentByCategory(reloaded, 1L, "Teaching video");
        assertNotNull(reloadedIntro);
        assertNotNull(reloadedTeaching);
        assertNotNull(reloaded.resolveDocumentLocalFile(reloadedIntro), "Local MP4 must resolve after reload");
        assertTrue(VideoValidator.isGoogleDriveUrl(reloadedTeaching.location()), "Google Drive link must persist after reload");

        assertTrue(reloaded.canUserAccessDocument("teacher", reloadedIntro));
        assertTrue(reloaded.canUserAccessDocument("staff", reloadedIntro));
        assertTrue(reloaded.canUserAccessDocument("partner", reloadedIntro));
        assertTrue(reloaded.canUserAccessDocument("partner", reloadedTeaching));
    }

    @Test
    void group3MultiSlotInterviewSetupAddRemoveSaveOverlapValidationAndConfirmationWorkflowWork() {
        Path dir = tempDir.resolve("group3_multislot");
        PortalStore store = new PortalStore(dir);

        LocalDate d1 = LocalDate.now().plusDays(5);
        LocalDate d2 = LocalDate.now().plusDays(6);

        // 1. Reject batch with invalid time (endTime <= startTime)
        List<PortalStore.TimeSlotSpec> invalidRangeBatch = List.of(
                new PortalStore.TimeSlotSpec(d1, LocalTime.of(10, 0), LocalTime.of(9, 30))
        );
        assertFalse(store.addAvailabilitySlotsBatch("teacher", 1L, invalidRangeBatch),
                "Slot with endTime <= startTime must be rejected");
        assertTrue(store.getLastValidationError().toLowerCase().contains("end time"));

        // 2. Reject batch with internal overlap between two slots in the same setup
        List<PortalStore.TimeSlotSpec> internalOverlapBatch = List.of(
                new PortalStore.TimeSlotSpec(d1, LocalTime.of(9, 0), LocalTime.of(10, 0)),
                new PortalStore.TimeSlotSpec(d1, LocalTime.of(9, 30), LocalTime.of(10, 30))
        );
        assertFalse(store.addAvailabilitySlotsBatch("teacher", 1L, internalOverlapBatch),
                "Overlapping slots within the same setup must be rejected");
        assertTrue(store.getLastValidationError().toLowerCase().contains("overlap"));

        // 3. Save valid multiple availability slots in one setup
        List<PortalStore.TimeSlotSpec> validTeacherBatch = List.of(
                new PortalStore.TimeSlotSpec(d1, LocalTime.of(9, 0), LocalTime.of(9, 45)),
                new PortalStore.TimeSlotSpec(d1, LocalTime.of(10, 0), LocalTime.of(10, 45)),
                new PortalStore.TimeSlotSpec(d2, LocalTime.of(14, 0), LocalTime.of(14, 45))
        );
        assertTrue(store.addAvailabilitySlotsBatch("teacher", 1L, validTeacherBatch),
                "Valid multi-slot batch must be saved in one setup");
        assertEquals(3, store.availabilitySlots(1L).stream()
                .filter(s -> s.startAt().toLocalDate().equals(d1) || s.startAt().toLocalDate().equals(d2))
                .count());

        // 4. Reject new batch that conflicts with already saved slot
        List<PortalStore.TimeSlotSpec> conflictWithExistingSlot = List.of(
                new PortalStore.TimeSlotSpec(d1, LocalTime.of(9, 15), LocalTime.of(9, 50))
        );
        assertFalse(store.addAvailabilitySlotsBatch("teacher", 1L, conflictWithExistingSlot),
                "Slot conflicting with existing availability slot must be rejected");

        // 5. Multi-slot interview reschedule/proposal by Partner/Staff + conflict check + Teacher confirmation workflow
        Interview interview = store.visibleInterviews("PARTNER", "partner").get(0);
        LocalDate idate = LocalDate.now().plusDays(8);
        List<PortalStore.TimeSlotSpec> proposedMultiSlots = List.of(
                new PortalStore.TimeSlotSpec(idate, LocalTime.of(13, 0), LocalTime.of(13, 30)),
                new PortalStore.TimeSlotSpec(idate, LocalTime.of(15, 0), LocalTime.of(15, 30)),
                new PortalStore.TimeSlotSpec(idate.plusDays(1), LocalTime.of(10, 0), LocalTime.of(10, 30))
        );
        assertTrue(store.rescheduleInterviewMultiSlots("partner", interview.getId(), proposedMultiSlots),
                "Partner should be able to save multiple interview time slots in one setup");

        // Verify persistence across PortalStore reload
        PortalStore reloaded = new PortalStore(dir);
        Interview reloadedInterview = reloaded.findInterview(interview.getId());
        assertNotNull(reloadedInterview);
        assertEquals(3, reloadedInterview.getProposedSlots().size(),
                "All 3 proposed interview slots must persist across store reload");

        // Teacher confirms the second proposed slot
        String chosenSlot = reloadedInterview.getProposedSlots().get(1);
        assertTrue(reloaded.confirmInterviewSlot("teacher", interview.getId(), chosenSlot),
                "Teacher must be able to confirm one of the proposed multi-slots");
        Interview confirmed = reloaded.findInterview(interview.getId());
        assertEquals("CONFIRMED", confirmed.getStatus());
        assertEquals(LocalDateTime.of(idate, LocalTime.of(15, 0)).toString(), confirmed.getDateTime());
        assertEquals(LocalDateTime.of(idate, LocalTime.of(15, 30)).toString(), confirmed.getEndDateTime());
    }
}
