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
}
