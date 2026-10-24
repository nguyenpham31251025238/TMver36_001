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
                "Certificate",
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
    void fullStatePersistsAcrossPortalStoreReload() throws Exception {
        Path dataDir = tempDir.resolve("persistence");
        PortalStore store1 = new PortalStore(dataDir);

        // 1. Add user
        assertTrue(store1.saveOrUpdateUser("admin", "partner2", "Global Academy", "PARTNER", "global123", true));
        // 2. Grant partner2 access to teacher 3 and set teacher 3 READY
        assertTrue(store1.status("staff", 3L, "READY"));
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
}
