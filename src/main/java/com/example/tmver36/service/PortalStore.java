package com.example.tmver36.service;

import com.example.tmver36.model.AppUser;
import com.example.tmver36.model.AvailabilitySlot;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PortalStore {

    private static final String STATE_FILE_NAME = "portal-store.properties";
    private static final DateTimeFormatter UPDATED_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Map<String, AppUser> users = new LinkedHashMap<>();
    private final Map<String, Long> teacherProfilesByUser = new LinkedHashMap<>();
    private final List<TeacherProfile> teachers = new ArrayList<>();
    private final List<TeacherDocument> documents = new ArrayList<>();
    private final List<Interview> interviews = new ArrayList<>();
    private final List<AvailabilitySlot> availabilitySlots = new ArrayList<>();
    private final List<String[]> logs = new ArrayList<>();

    // Stores access grants in the form username:teacherId.
    private final Set<String> granted = new LinkedHashSet<>();
    private final Map<String, String[]> partnerEvaluations = new LinkedHashMap<>();

    private final AtomicLong teacherIds = new AtomicLong(3);
    private final AtomicLong documentIds = new AtomicLong(0);
    private final AtomicLong interviewIds = new AtomicLong(3);
    private final AtomicLong availabilityIds = new AtomicLong(0);

    private Path customDataDir;
    private boolean suppressSave;

    public PortalStore() {
        this(null);
    }

    public PortalStore(Path customDataDir) {
        this.customDataDir = customDataDir;
        Path stateFile = getStateFilePath();
        if (Files.isRegularFile(stateFile)) {
            if (!loadFromDisk(stateFile)) {
                seedDefaults();
                saveState();
            }
        } else {
            seedDefaults();
            saveState();
        }
    }

    public Path getDataDir() {
        if (customDataDir != null) {
            return customDataDir;
        }
        String override = System.getProperty("tm.data.dir");
        if (override != null && !override.isBlank()) {
            return Paths.get(override.trim());
        }
        return Paths.get(System.getProperty("user.home", "."), ".tmver36_001");
    }

    public Path getStateFilePath() {
        return getDataDir().resolve(STATE_FILE_NAME);
    }

    public synchronized void resetToSeedData() {
        seedDefaults();
        saveState();
    }

    public synchronized boolean reloadFromDisk() {
        return loadFromDisk(getStateFilePath());
    }

    private void clearInMemoryState() {
        users.clear();
        teacherProfilesByUser.clear();
        teachers.clear();
        documents.clear();
        interviews.clear();
        availabilitySlots.clear();
        logs.clear();
        granted.clear();
        partnerEvaluations.clear();
        teacherIds.set(0);
        documentIds.set(0);
        interviewIds.set(0);
        availabilityIds.set(0);
    }

    private void seedDefaults() {
        suppressSave = true;
        try {
            clearInMemoryState();

            users.put("admin", new AppUser("admin", "admin123", "ADMIN", "System Administrator"));
            users.put("staff", new AppUser("staff", "staff123", "STAFF", "Matching Staff"));
            users.put("partner", new AppUser("partner", "partner123", "PARTNER", "Bright English Center"));
            users.put("teacher", new AppUser("teacher", "teacher123", "TEACHER", "Emily Martin"));

            TeacherProfile emily = new TeacherProfile(
                    1,
                    "Emily Martin",
                    "emily.martin@example.com",
                    "American",
                    "Ho Chi Minh City",
                    "5 years",
                    "TEFL, Bachelor's degree",
                    "READY",
                    "https://example.com/emily-demo",
                    "https://example.com/emily-cv"
            );
            emily.setSubject("English (ESL)");
            emily.setTargetAgeGroup("Primary (6-11)");
            emily.setAvailableStartDate("12/2026");
            emily.setPhone("+84 912 345 678");
            emily.setBio("Experienced primary English teacher with a CELTA certificate. I enjoy building engaging lessons through games and collaborative activities.");
            teachers.add(emily);
            teacherProfilesByUser.put("teacher", 1L);

            TeacherProfile daniel = new TeacherProfile(
                    2,
                    "Daniel Walker",
                    "daniel.walker@example.com",
                    "British",
                    "Hanoi",
                    "3 years",
                    "CELTA",
                    "READY",
                    "https://example.com/daniel-demo",
                    "https://example.com/daniel-cv"
            );
            daniel.setSubject("IELTS / Academic English");
            daniel.setTargetAgeGroup("Secondary (12-17)");
            daniel.setAvailableStartDate("01/2027");
            teachers.add(daniel);

            TeacherProfile sarah = new TeacherProfile(
                    3,
                    "Sarah Thompson",
                    "sarah.thompson@example.com",
                    "Australian",
                    "Da Nang",
                    "7 years",
                    "TESOL, Master's degree",
                    "PENDING",
                    "https://example.com/sarah-demo",
                    "https://example.com/sarah-cv"
            );
            sarah.setSubject("Communication English");
            sarah.setTargetAgeGroup("Adults");
            sarah.setAvailableStartDate("11/2026");
            teachers.add(sarah);

            teacherIds.set(3);

            // Seed default documents for teacher 1, 2, 3
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 1L, "Emily_Martin_CV.pdf", "CV", "14/10/2026", "https://example.com/emily-cv"));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 1L, "CELTA_Certificate.pdf", "Certificate", "14/10/2026", "https://example.com/emily-celta"));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 1L, "Intro_Video_Emily.mp4", "Introductory video", "15/10/2026", "https://example.com/emily-demo"));

            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 2L, "Daniel_Walker_CV.pdf", "CV", "12/10/2026", "https://example.com/daniel-cv"));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 3L, "Sarah_Thompson_CV.pdf", "CV", "13/10/2026", "https://example.com/sarah-cv"));

            // Initially, the demo Partner can view teachers 1 and 2.
            granted.add("partner:1");
            granted.add("partner:2");

            LocalDate slotDate = LocalDate.now().plusDays(1);
            while (slotDate.getDayOfWeek().getValue() > 5) {
                slotDate = slotDate.plusDays(1);
            }
            availabilitySlots.add(new AvailabilitySlot(
                    availabilityIds.incrementAndGet(),
                    1L,
                    LocalDateTime.of(slotDate, LocalTime.of(14, 0)),
                    LocalDateTime.of(slotDate, LocalTime.of(15, 0))
            ));
            LocalDate secondSlotDate = slotDate.plusDays(1);
            while (secondSlotDate.getDayOfWeek().getValue() > 5) {
                secondSlotDate = secondSlotDate.plusDays(1);
            }
            availabilitySlots.add(new AvailabilitySlot(
                    availabilityIds.incrementAndGet(),
                    1L,
                    LocalDateTime.of(secondSlotDate, LocalTime.of(9, 0)),
                    LocalDateTime.of(secondSlotDate, LocalTime.of(10, 0))
            ));
            availabilitySlots.add(new AvailabilitySlot(
                    availabilityIds.incrementAndGet(),
                    2L,
                    LocalDateTime.of(secondSlotDate, LocalTime.of(15, 0)),
                    LocalDateTime.of(secondSlotDate, LocalTime.of(16, 0))
            ));

            // Demo interviews with live auto-generated working meeting room URLs
            String firstDateTime = LocalDate.now().plusDays(2).atTime(10, 0).toString();
            Interview firstInterview = new Interview(
                    1,
                    1,
                    "Emily Martin",
                    "Bright English Center",
                    firstDateTime,
                    "PENDING",
                    "Not reviewed",
                    "",
                    generateMeetingLink(1L, "Emily Martin", "Bright English Center", firstDateTime)
            );
            firstInterview.setEndDateTime(LocalDate.now().plusDays(2).atTime(10, 30).toString());
            interviews.add(firstInterview);

            String secondDateTime = LocalDate.now().plusDays(3).atTime(14, 30).toString();
            Interview secondInterview = new Interview(
                    2,
                    2,
                    "Daniel Walker",
                    "Bright English Center",
                    secondDateTime,
                    "CONFIRMED",
                    "Not reviewed",
                    "",
                    generateMeetingLink(2L, "Daniel Walker", "Bright English Center", secondDateTime)
            );
            secondInterview.setEndDateTime(LocalDate.now().plusDays(3).atTime(15, 0).toString());
            interviews.add(secondInterview);

            interviewIds.set(3);

            log("SYSTEM", "Seed data loaded");
        } finally {
            suppressSave = false;
        }
    }

    // =========================
    // PERSISTENCE
    // =========================

    public synchronized void saveState() {
        if (suppressSave) {
            return;
        }
        try {
            Path dataDir = getDataDir();
            Files.createDirectories(dataDir);
            Path stateFile = dataDir.resolve(STATE_FILE_NAME);
            Path tempFile = dataDir.resolve(STATE_FILE_NAME + ".tmp");

            Properties props = new Properties();
            props.setProperty("seq.teacherIds", Long.toString(teacherIds.get()));
            props.setProperty("seq.documentIds", Long.toString(documentIds.get()));
            props.setProperty("seq.interviewIds", Long.toString(interviewIds.get()));
            props.setProperty("seq.availabilityIds", Long.toString(availabilityIds.get()));

            List<AppUser> userList = new ArrayList<>(users.values());
            props.setProperty("users.count", Integer.toString(userList.size()));
            for (int i = 0; i < userList.size(); i++) {
                AppUser u = userList.get(i);
                String prefix = "user." + i + ".";
                props.setProperty(prefix + "username", nn(u.getUsername()));
                props.setProperty(prefix + "passwordHash", nn(u.getPasswordHash()));
                props.setProperty(prefix + "role", nn(u.getRole()));
                props.setProperty(prefix + "fullName", nn(u.getFullName()));
                props.setProperty(prefix + "active", Boolean.toString(u.isActive()));
            }

            List<Map.Entry<String, Long>> links = new ArrayList<>(teacherProfilesByUser.entrySet());
            props.setProperty("teacherLinks.count", Integer.toString(links.size()));
            for (int i = 0; i < links.size(); i++) {
                props.setProperty("teacherLink." + i + ".username", nn(links.get(i).getKey()));
                props.setProperty("teacherLink." + i + ".teacherId", Long.toString(links.get(i).getValue()));
            }

            props.setProperty("teachers.count", Integer.toString(teachers.size()));
            for (int i = 0; i < teachers.size(); i++) {
                TeacherProfile t = teachers.get(i);
                String prefix = "teacher." + i + ".";
                props.setProperty(prefix + "id", Long.toString(t.getId()));
                props.setProperty(prefix + "name", nn(t.getName()));
                props.setProperty(prefix + "fullName", nn(t.getFullName()));
                props.setProperty(prefix + "email", nn(t.getEmail()));
                props.setProperty(prefix + "nationality", nn(t.getNationality()));
                props.setProperty(prefix + "location", nn(t.getLocation()));
                props.setProperty(prefix + "experience", nn(t.getExperience()));
                props.setProperty(prefix + "qualification", nn(t.getQualification()));
                props.setProperty(prefix + "status", nn(t.getStatus()));
                props.setProperty(prefix + "videoUrl", nn(t.getVideoUrl()));
                props.setProperty(prefix + "cvUrl", nn(t.getCvUrl()));
                props.setProperty(prefix + "subject", nn(t.getSubject()));
                props.setProperty(prefix + "bio", nn(t.getBio()));
                props.setProperty(prefix + "phone", nn(t.getPhone()));
                props.setProperty(prefix + "targetAgeGroup", nn(t.getTargetAgeGroup()));
                props.setProperty(prefix + "availableStartDate", nn(t.getAvailableStartDate()));
                props.setProperty(prefix + "timezone", nn(t.getTimezone()));
            }

            props.setProperty("documents.count", Integer.toString(documents.size()));
            for (int i = 0; i < documents.size(); i++) {
                TeacherDocument d = documents.get(i);
                String prefix = "document." + i + ".";
                props.setProperty(prefix + "id", Long.toString(d.id()));
                props.setProperty(prefix + "teacherId", Long.toString(d.teacherId()));
                props.setProperty(prefix + "fileName", nn(d.fileName()));
                props.setProperty(prefix + "type", nn(d.type()));
                props.setProperty(prefix + "updatedAt", nn(d.updatedAt()));
                props.setProperty(prefix + "location", nn(d.location()));
            }

            props.setProperty("slots.count", Integer.toString(availabilitySlots.size()));
            for (int i = 0; i < availabilitySlots.size(); i++) {
                AvailabilitySlot s = availabilitySlots.get(i);
                String prefix = "slot." + i + ".";
                props.setProperty(prefix + "id", Long.toString(s.id()));
                props.setProperty(prefix + "teacherId", Long.toString(s.teacherId()));
                props.setProperty(prefix + "startAt", s.startAt().toString());
                props.setProperty(prefix + "endAt", s.endAt().toString());
            }

            List<String> grantList = new ArrayList<>(granted);
            props.setProperty("grants.count", Integer.toString(grantList.size()));
            for (int i = 0; i < grantList.size(); i++) {
                props.setProperty("grant." + i, nn(grantList.get(i)));
            }

            List<Map.Entry<String, String[]>> evalList = new ArrayList<>(partnerEvaluations.entrySet());
            props.setProperty("evals.count", Integer.toString(evalList.size()));
            for (int i = 0; i < evalList.size(); i++) {
                String prefix = "eval." + i + ".";
                String[] val = evalList.get(i).getValue();
                props.setProperty(prefix + "key", nn(evalList.get(i).getKey()));
                props.setProperty(prefix + "decision", val != null && val.length > 0 ? nn(val[0]) : "");
                props.setProperty(prefix + "comment", val != null && val.length > 1 ? nn(val[1]) : "");
            }

            props.setProperty("interviews.count", Integer.toString(interviews.size()));
            for (int i = 0; i < interviews.size(); i++) {
                Interview iv = interviews.get(i);
                String prefix = "interview." + i + ".";
                props.setProperty(prefix + "id", Long.toString(iv.getId()));
                props.setProperty(prefix + "teacherId", Long.toString(iv.getTeacherId()));
                props.setProperty(prefix + "teacherName", nn(iv.getTeacherName()));
                props.setProperty(prefix + "partner", nn(iv.getPartner()));
                props.setProperty(prefix + "dateTime", nn(iv.getDateTime()));
                props.setProperty(prefix + "endDateTime", nn(iv.getEndDateTime()));
                props.setProperty(prefix + "status", nn(iv.getStatus()));
                props.setProperty(prefix + "result", nn(iv.getResult()));
                props.setProperty(prefix + "feedback", nn(iv.getFeedback()));
                props.setProperty(prefix + "meetingUrl", nn(iv.getMeetingUrl()));
            }

            props.setProperty("logs.count", Integer.toString(logs.size()));
            for (int i = 0; i < logs.size(); i++) {
                String[] entry = logs.get(i);
                String prefix = "log." + i + ".";
                props.setProperty(prefix + "time", entry.length > 0 ? nn(entry[0]) : "");
                props.setProperty(prefix + "actor", entry.length > 1 ? nn(entry[1]) : "");
                props.setProperty(prefix + "action", entry.length > 2 ? nn(entry[2]) : "");
            }

            try (OutputStream out = Files.newOutputStream(tempFile)) {
                props.store(out, "TeacherMatch PortalStore persistent state");
            }
            Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            // Keep working in-memory if disk is temporarily unavailable
        }
    }

    private synchronized boolean loadFromDisk(Path stateFile) {
        if (stateFile == null || !Files.isRegularFile(stateFile)) {
            return false;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(stateFile)) {
            props.load(in);
        } catch (IOException | IllegalArgumentException ex) {
            return false;
        }

        int userCount = parseInt(props.getProperty("users.count"), -1);
        if (userCount <= 0) {
            return false;
        }

        suppressSave = true;
        try {
            clearInMemoryState();

            teacherIds.set(parseLong(props.getProperty("seq.teacherIds"), 3L));
            documentIds.set(parseLong(props.getProperty("seq.documentIds"), 0L));
            interviewIds.set(parseLong(props.getProperty("seq.interviewIds"), 3L));
            availabilityIds.set(parseLong(props.getProperty("seq.availabilityIds"), 0L));

            for (int i = 0; i < userCount; i++) {
                String prefix = "user." + i + ".";
                String username = props.getProperty(prefix + "username", "").trim();
                String passwordHash = props.getProperty(prefix + "passwordHash", "");
                String role = props.getProperty(prefix + "role", "TEACHER").trim();
                String fullName = props.getProperty(prefix + "fullName", username);
                boolean active = Boolean.parseBoolean(props.getProperty(prefix + "active", "true"));
                if (!username.isEmpty()) {
                    users.put(username, AppUser.fromStoredHash(username, passwordHash, role, fullName, active));
                }
            }

            int linkCount = parseInt(props.getProperty("teacherLinks.count"), 0);
            for (int i = 0; i < linkCount; i++) {
                String username = props.getProperty("teacherLink." + i + ".username", "").trim();
                long teacherId = parseLong(props.getProperty("teacherLink." + i + ".teacherId"), -1L);
                if (!username.isEmpty() && teacherId > 0) {
                    teacherProfilesByUser.put(username, teacherId);
                }
            }

            int teacherCount = parseInt(props.getProperty("teachers.count"), 0);
            for (int i = 0; i < teacherCount; i++) {
                String prefix = "teacher." + i + ".";
                long id = parseLong(props.getProperty(prefix + "id"), i + 1L);
                String name = props.getProperty(prefix + "name", "");
                TeacherProfile t = new TeacherProfile(
                        id,
                        name,
                        props.getProperty(prefix + "email", ""),
                        props.getProperty(prefix + "nationality", ""),
                        props.getProperty(prefix + "location", ""),
                        props.getProperty(prefix + "experience", ""),
                        props.getProperty(prefix + "qualification", ""),
                        props.getProperty(prefix + "status", "DRAFT"),
                        props.getProperty(prefix + "videoUrl", ""),
                        props.getProperty(prefix + "cvUrl", "")
                );
                String fullName = props.getProperty(prefix + "fullName", name);
                if (!fullName.isBlank()) {
                    t.setFullName(fullName);
                }
                t.setSubject(props.getProperty(prefix + "subject", "English (ESL)"));
                t.setBio(props.getProperty(prefix + "bio", ""));
                t.setPhone(props.getProperty(prefix + "phone", ""));
                t.setTargetAgeGroup(props.getProperty(prefix + "targetAgeGroup", ""));
                t.setAvailableStartDate(props.getProperty(prefix + "availableStartDate", ""));
                t.setTimezone(props.getProperty(prefix + "timezone", "UTC+7 (Vietnam)"));
                teachers.add(t);
                if (id > teacherIds.get()) {
                    teacherIds.set(id);
                }
            }

            int docCount = parseInt(props.getProperty("documents.count"), 0);
            for (int i = 0; i < docCount; i++) {
                String prefix = "document." + i + ".";
                long id = parseLong(props.getProperty(prefix + "id"), i + 1L);
                long teacherId = parseLong(props.getProperty(prefix + "teacherId"), 1L);
                String fileName = props.getProperty(prefix + "fileName", "");
                String type = props.getProperty(prefix + "type", "CV");
                String updatedAt = props.getProperty(prefix + "updatedAt", "");
                String location = props.getProperty(prefix + "location", "");
                if (!fileName.isBlank()) {
                    documents.add(new TeacherDocument(id, teacherId, fileName, type, updatedAt, location));
                    if (id > documentIds.get()) {
                        documentIds.set(id);
                    }
                }
            }

            int slotCount = parseInt(props.getProperty("slots.count"), 0);
            for (int i = 0; i < slotCount; i++) {
                String prefix = "slot." + i + ".";
                long id = parseLong(props.getProperty(prefix + "id"), i + 1L);
                long teacherId = parseLong(props.getProperty(prefix + "teacherId"), 1L);
                try {
                    LocalDateTime startAt = LocalDateTime.parse(props.getProperty(prefix + "startAt", ""));
                    LocalDateTime endAt = LocalDateTime.parse(props.getProperty(prefix + "endAt", ""));
                    availabilitySlots.add(new AvailabilitySlot(id, teacherId, startAt, endAt));
                    if (id > availabilityIds.get()) {
                        availabilityIds.set(id);
                    }
                } catch (DateTimeParseException ignored) {
                }
            }

            int grantCount = parseInt(props.getProperty("grants.count"), 0);
            for (int i = 0; i < grantCount; i++) {
                String grant = props.getProperty("grant." + i, "").trim();
                if (!grant.isEmpty()) {
                    granted.add(grant);
                }
            }

            int evalCount = parseInt(props.getProperty("evals.count"), 0);
            for (int i = 0; i < evalCount; i++) {
                String prefix = "eval." + i + ".";
                String key = props.getProperty(prefix + "key", "").trim();
                if (!key.isEmpty()) {
                    partnerEvaluations.put(key, new String[]{
                            props.getProperty(prefix + "decision", ""),
                            props.getProperty(prefix + "comment", "")
                    });
                }
            }

            int interviewCount = parseInt(props.getProperty("interviews.count"), 0);
            for (int i = 0; i < interviewCount; i++) {
                String prefix = "interview." + i + ".";
                long id = parseLong(props.getProperty(prefix + "id"), i + 1L);
                long teacherId = parseLong(props.getProperty(prefix + "teacherId"), 1L);
                Interview iv = new Interview(
                        id,
                        teacherId,
                        props.getProperty(prefix + "teacherName", ""),
                        props.getProperty(prefix + "partner", ""),
                        props.getProperty(prefix + "dateTime", ""),
                        props.getProperty(prefix + "status", "PENDING"),
                        props.getProperty(prefix + "result", "Not reviewed"),
                        props.getProperty(prefix + "feedback", ""),
                        props.getProperty(prefix + "meetingUrl", "")
                );
                iv.setEndDateTime(props.getProperty(prefix + "endDateTime", ""));
                if (iv.getMeetingUrl() == null || iv.getMeetingUrl().isBlank()
                        || iv.getMeetingUrl().startsWith("https://meet.google.com/tm-")
                        || iv.getMeetingUrl().equals("https://meet.google.com/abc-defg-hij")) {
                    iv.setMeetingUrl(generateMeetingLink(iv));
                }
                interviews.add(iv);
                if (id > interviewIds.get()) {
                    interviewIds.set(id);
                }
            }

            int logCount = parseInt(props.getProperty("logs.count"), 0);
            for (int i = 0; i < logCount; i++) {
                String prefix = "log." + i + ".";
                logs.add(new String[]{
                        props.getProperty(prefix + "time", ""),
                        props.getProperty(prefix + "actor", "SYSTEM"),
                        props.getProperty(prefix + "action", "")
                });
            }
            return true;
        } finally {
            suppressSave = false;
        }
    }

    private static String nn(String value) {
        return value == null ? "" : value;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    // =========================
    // USERS
    // =========================

    public synchronized Collection<AppUser> users() {
        return List.copyOf(users.values());
    }

    public synchronized AppUser findUser(String username) {
        return username == null ? null : users.get(username);
    }

    public synchronized boolean userExists(String username) {
        return username != null && users.containsKey(username);
    }

    public synchronized String userRole(String username) {
        AppUser user = username == null ? null : users.get(username);
        return user == null ? null : user.getRole();
    }

    public synchronized AppUser authenticate(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        AppUser user = users.get(username);
        if (user == null || !user.isActive()) {
            return null;
        }
        String beforeHash = user.getPasswordHash();
        boolean verified = user.verifyPassword(password);
        if (verified && !nn(beforeHash).equals(user.getPasswordHash())) {
            saveState();
        }
        return verified ? user : null;
    }

    public synchronized void addUser(String username, String password, String role) {
        addUser(username, password, role, username);
    }

    public synchronized void addUser(String username, String password, String role, String fullName) {
        if (username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()
                || role == null || role.trim().isEmpty()
                || fullName == null || fullName.trim().isEmpty()) {
            return;
        }
        String cleanUsername = username.trim();
        String cleanRole = role.trim();
        String cleanName = fullName.trim();
        if (users.containsKey(cleanUsername)) {
            return;
        }
        AppUser created = new AppUser(cleanUsername, password, cleanRole, cleanName);
        users.put(cleanUsername, created);
        if ("TEACHER".equals(cleanRole)) {
            ensureTeacherProfileForUser(created);
        }
        log("ADMIN", "Created account " + cleanUsername);
        saveState();
    }

    public synchronized boolean saveOrUpdateUser(
            String actor,
            String username,
            String fullName,
            String role,
            String password,
            boolean active
    ) {
        if (username == null || username.isBlank() || role == null || !Set.of("ADMIN", "STAFF", "PARTNER", "TEACHER").contains(role)) {
            return false;
        }
        if (actor != null && !"ADMIN".equals(userRole(actor))) {
            return false;
        }
        String cleanUsername = username.trim();
        String cleanName = (fullName == null || fullName.isBlank()) ? cleanUsername : fullName.trim();
        AppUser existing = users.get(cleanUsername);
        if (existing != null) {
            if ("admin".equalsIgnoreCase(cleanUsername) && (!"ADMIN".equals(role) || !active)) {
                return false;
            }
            existing.setFullName(cleanName);
            existing.setRole(role);
            existing.setActive(active);
            if (password != null && !password.isBlank()) {
                existing.setPassword(password);
            }
            if ("TEACHER".equals(role)) {
                TeacherProfile linked = teacherForUser(cleanUsername);
                if (linked != null) {
                    linked.setFullName(cleanName);
                }
            }
            log(actor == null ? "ADMIN" : actor, "Updated account " + cleanUsername);
            saveState();
            return true;
        } else {
            if (password == null || password.isBlank()) {
                return false;
            }
            AppUser created = new AppUser(cleanUsername, password, role, cleanName);
            created.setActive(active);
            users.put(cleanUsername, created);
            if ("TEACHER".equals(role)) {
                ensureTeacherProfileForUser(created);
            }
            log(actor == null ? "ADMIN" : actor, "Created account " + cleanUsername);
            saveState();
            return true;
        }
    }

    public synchronized boolean deleteUser(String actor, String username) {
        if (!"ADMIN".equals(userRole(actor)) || username == null || username.isBlank()
                || "admin".equalsIgnoreCase(username)) {
            return false;
        }
        AppUser removed = users.remove(username);
        if (removed == null) {
            return false;
        }
        log(actor, "Deleted account " + username);
        saveState();
        return true;
    }

    public synchronized boolean updateUserRole(String actor, String username, String role) {
        if (!"ADMIN".equals(userRole(actor)) || username == null || role == null
                || !Set.of("ADMIN", "STAFF", "PARTNER", "TEACHER").contains(role)) {
            return false;
        }
        AppUser user = users.get(username);
        if (user == null || ("admin".equalsIgnoreCase(username) && !"ADMIN".equals(role))) {
            return false;
        }
        String previousRole = user.getRole();
        user.setRole(role);
        if ("TEACHER".equals(role)) {
            ensureTeacherProfileForUser(user);
        }
        log(actor, "Changed role for " + username + " from " + previousRole + " to " + role);
        saveState();
        return true;
    }

    public synchronized boolean setUserActive(String actor, String username, boolean active) {
        if (!"ADMIN".equals(userRole(actor)) || username == null || "admin".equalsIgnoreCase(username)) {
            return false;
        }
        AppUser user = users.get(username);
        if (user == null) {
            return false;
        }
        user.setActive(active);
        log(actor, (active ? "Enabled" : "Disabled") + " account " + username);
        saveState();
        return true;
    }

    // =========================
    // TEACHER PROFILES & DOCUMENTS
    // =========================

    public synchronized List<TeacherProfile> teachers() {
        return List.copyOf(teachers);
    }

    public synchronized TeacherProfile teacher(long id) {
        return teachers.stream()
                .filter(teacher -> teacher.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public synchronized long teacherIdForUser(String username) {
        AppUser user = username == null ? null : users.get(username);
        if (user == null || !"TEACHER".equals(user.getRole())) {
            return -1L;
        }
        Long linkedId = teacherProfilesByUser.get(username);
        if (linkedId != null && teacher(linkedId) != null) {
            return linkedId;
        }
        TeacherProfile byName = teachers.stream()
                .filter(t -> t.getName().equalsIgnoreCase(user.getFullName()) || t.getName().equalsIgnoreCase(user.getUsername()))
                .findFirst()
                .orElse(null);
        if (byName != null) {
            teacherProfilesByUser.put(username, byName.getId());
            saveState();
            return byName.getId();
        }
        TeacherProfile created = ensureTeacherProfileForUser(user);
        saveState();
        return created.getId();
    }

    public synchronized TeacherProfile teacherForUser(String username) {
        long id = teacherIdForUser(username);
        return id < 0 ? null : teacher(id);
    }

    private TeacherProfile ensureTeacherProfileForUser(AppUser user) {
        Long existingId = teacherProfilesByUser.get(user.getUsername());
        if (existingId != null) {
            TeacherProfile found = teacher(existingId);
            if (found != null) {
                return found;
            }
        }
        long newId = teacherIds.incrementAndGet();
        String displayName = (user.getFullName() == null || user.getFullName().isBlank())
                ? user.getUsername()
                : user.getFullName();
        TeacherProfile created = new TeacherProfile(
                newId,
                displayName,
                user.getUsername() + "@example.com",
                "American",
                "Ho Chi Minh City",
                "1-3 years",
                "TEFL",
                "DRAFT",
                "",
                ""
        );
        teachers.add(created);
        teacherProfilesByUser.put(user.getUsername(), newId);
        return created;
    }

    public synchronized boolean saveTeacherProfile(String actor, TeacherProfile updated) {
        if (updated == null) {
            return false;
        }
        String role = userRole(actor);
        boolean isStaffOrAdmin = "STAFF".equals(role) || "ADMIN".equals(role);
        boolean isOwner = "TEACHER".equals(role) && teacherIdForUser(actor) == updated.getId();
        if (!isStaffOrAdmin && !isOwner) {
            return false;
        }
        TeacherProfile target = teacher(updated.getId());
        if (target == null) {
            return false;
        }
        if (target != updated) {
            target.setFullName(updated.getFullName());
            target.setEmail(updated.getEmail());
            target.setNationality(updated.getNationality());
            target.setLocation(updated.getLocation());
            target.setExperience(updated.getExperience());
            target.setQualification(updated.getQualification());
            target.setStatus(updated.getStatus());
            target.setVideoUrl(updated.getVideoUrl());
            target.setCvUrl(updated.getCvUrl());
            target.setSubject(updated.getSubject());
            target.setBio(updated.getBio());
            target.setPhone(updated.getPhone());
            target.setTargetAgeGroup(updated.getTargetAgeGroup());
            target.setAvailableStartDate(updated.getAvailableStartDate());
            target.setTimezone(updated.getTimezone());
        }
        for (Interview iv : interviews) {
            if (iv.getTeacherId() == target.getId()) {
                iv.setTeacherName(target.getName());
            }
        }
        log(actor, "Saved profile for " + target.getName() + " (" + target.getStatus() + ")");
        saveState();
        return true;
    }

    public synchronized List<TeacherDocument> documents(long teacherId) {
        return documents.stream()
                .filter(d -> d.teacherId() == teacherId)
                .toList();
    }

    public synchronized TeacherDocument addOrReplaceDocument(
            String actor,
            long teacherId,
            String fileName,
            String type,
            String sourcePathOrUrl
    ) {
        String role = userRole(actor);
        boolean isStaffOrAdmin = "STAFF".equals(role) || "ADMIN".equals(role);
        boolean isOwner = "TEACHER".equals(role) && teacherIdForUser(actor) == teacherId;
        if (!isStaffOrAdmin && !isOwner) {
            return null;
        }
        TeacherProfile teacher = teacher(teacherId);
        if (teacher == null || fileName == null || fileName.isBlank() || type == null || type.isBlank()) {
            return null;
        }
        String storedLocation = sourcePathOrUrl == null ? "" : sourcePathOrUrl.trim();
        if (!storedLocation.isEmpty() && !storedLocation.startsWith("http://") && !storedLocation.startsWith("https://")) {
            try {
                Path src = storedLocation.startsWith("file:")
                        ? Paths.get(java.net.URI.create(storedLocation))
                        : Paths.get(storedLocation);
                if (Files.isRegularFile(src)) {
                    Path targetDir = getDataDir().resolve("documents").resolve(Long.toString(teacherId));
                    Files.createDirectories(targetDir);
                    String safeName = fileName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
                    Path dest = targetDir.resolve(safeName);
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    storedLocation = dest.toAbsolutePath().toString();
                }
            } catch (Exception ignored) {
            }
        }
        if (storedLocation.isEmpty()) {
            storedLocation = fileName.trim();
        }

        String updatedDate = LocalDate.now().format(UPDATED_DATE_FORMAT);
        TeacherDocument doc = new TeacherDocument(
                documentIds.incrementAndGet(),
                teacherId,
                fileName.trim(),
                type.trim(),
                updatedDate,
                storedLocation
        );
        int existingIndex = -1;
        for (int i = 0; i < documents.size(); i++) {
            TeacherDocument item = documents.get(i);
            if (item.teacherId() == teacherId && item.type().equalsIgnoreCase(type.trim())) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex >= 0) {
            documents.set(existingIndex, doc);
        } else {
            documents.add(doc);
        }

        syncTeacherDocumentFields(teacher, doc.type(), storedLocation, doc.fileName());
        log(actor, "Updated document (" + type.trim() + "): " + fileName.trim());
        saveState();
        return doc;
    }

    public synchronized boolean removeDocument(String actor, long teacherId, long documentId) {
        String role = userRole(actor);
        boolean isStaffOrAdmin = "STAFF".equals(role) || "ADMIN".equals(role);
        boolean isOwner = "TEACHER".equals(role) && teacherIdForUser(actor) == teacherId;
        if (!isStaffOrAdmin && !isOwner) {
            return false;
        }
        TeacherDocument target = documents.stream()
                .filter(d -> d.teacherId() == teacherId && d.id() == documentId)
                .findFirst()
                .orElse(null);
        if (target == null) {
            return false;
        }
        documents.remove(target);
        TeacherProfile teacher = teacher(teacherId);
        if (teacher != null) {
            if ("CV".equalsIgnoreCase(target.type())) {
                teacher.setCvUrl("");
            } else if (target.type().toLowerCase().contains("video")) {
                teacher.setVideoUrl("");
            }
        }
        log(actor, "Deleted document (" + target.type() + "): " + target.fileName());
        saveState();
        return true;
    }

    private void syncTeacherDocumentFields(TeacherProfile teacher, String type, String location, String fileName) {
        if ("CV".equalsIgnoreCase(type)) {
            teacher.setCvUrl(location);
        } else if (type.toLowerCase().contains("video")) {
            teacher.setVideoUrl(location);
        } else if ("Certificate".equalsIgnoreCase(type) && (teacher.getQualification() == null || teacher.getQualification().isBlank())) {
            teacher.setQualification(fileName);
        }
    }

    // =========================
    // AVAILABILITY SLOTS
    // =========================

    public synchronized List<AvailabilitySlot> availabilitySlots(long teacherId) {
        return availabilitySlots.stream()
                .filter(slot -> slot.teacherId() == teacherId)
                .sorted(Comparator.comparing(AvailabilitySlot::startAt))
                .toList();
    }

    public synchronized boolean addAvailabilitySlot(
            String username,
            long teacherId,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
        String role = userRole(username);
        boolean allowed = ("TEACHER".equals(role) && teacherIdForUser(username) == teacherId)
                || "STAFF".equals(role) || "ADMIN".equals(role);
        if (!allowed || startAt == null || endAt == null
                || !startAt.isAfter(LocalDateTime.now())
                || !endAt.isAfter(startAt)) {
            return false;
        }
        boolean overlaps = availabilitySlots.stream().anyMatch(slot -> slot.teacherId() == teacherId
                && startAt.isBefore(slot.endAt()) && endAt.isAfter(slot.startAt()));
        if (overlaps) {
            return false;
        }
        availabilitySlots.add(new AvailabilitySlot(availabilityIds.incrementAndGet(), teacherId, startAt, endAt));
        log(username, "Added availability slot " + startAt + " - " + endAt);
        saveState();
        return true;
    }

    public synchronized boolean removeAvailabilitySlot(String username, long slotId) {
        String role = userRole(username);
        long teacherId = teacherIdForUser(username);
        AvailabilitySlot slot = availabilitySlots.stream()
                .filter(candidate -> candidate.id() == slotId
                        && (candidate.teacherId() == teacherId || "STAFF".equals(role) || "ADMIN".equals(role)))
                .findFirst()
                .orElse(null);
        if (slot == null) {
            return false;
        }
        availabilitySlots.remove(slot);
        log(username, "Removed availability slot " + slot.label());
        saveState();
        return true;
    }

    // =========================
    // ACCESS CONTROL & STATUS
    // =========================

    public synchronized List<TeacherProfile> visibleTeachers(String username, String role) {
        String authenticatedRole = userRole(username);
        if ("STAFF".equals(authenticatedRole) || "ADMIN".equals(authenticatedRole)) {
            return List.copyOf(teachers);
        }
        if ("PARTNER".equals(authenticatedRole)) {
            return teachers.stream()
                    .filter(teacher -> granted.contains(username + ":" + teacher.getId()))
                    .toList();
        }
        return List.of();
    }

    public synchronized boolean canPartnerAccessTeacher(String username, long teacherId) {
        if (username == null || !userExists(username)) {
            return false;
        }
        if (!"PARTNER".equals(userRole(username))) {
            return false;
        }
        if (teacher(teacherId) == null) {
            return false;
        }
        return granted.contains(username + ":" + teacherId);
    }

    public synchronized void grant(String username, long teacherId) {
        grant("staff", username, teacherId);
    }

    public synchronized boolean grant(String actor, String username, long teacherId) {
        if (!("STAFF".equals(userRole(actor)) || "ADMIN".equals(userRole(actor)))
                || !"PARTNER".equals(userRole(username)) || teacher(teacherId) == null) {
            return false;
        }
        granted.add(username + ":" + teacherId);
        log(actor, "Granted teacher " + teacherId + " to " + username);
        saveState();
        return true;
    }

    public synchronized void revoke(String username, long teacherId) {
        revoke("staff", username, teacherId);
    }

    public synchronized boolean revoke(String actor, String username, long teacherId) {
        if (!("STAFF".equals(userRole(actor)) || "ADMIN".equals(userRole(actor)))
                || !"PARTNER".equals(userRole(username))) {
            return false;
        }
        boolean removed = granted.remove(username + ":" + teacherId);
        if (removed) {
            log(actor, "Revoked teacher " + teacherId + " from " + username);
            saveState();
        }
        return removed;
    }

    public synchronized boolean savePartnerEvaluation(String username, long teacherId, String decision, String comment) {
        if (!canPartnerAccessTeacher(username, teacherId) || decision == null || decision.isBlank()) {
            return false;
        }
        partnerEvaluations.put(
                username + ":" + teacherId,
                new String[]{decision.trim(), comment == null ? "" : comment.trim()}
        );
        log(username, "Shortlist evaluation for teacher " + teacherId + ": " + decision.trim());
        saveState();
        return true;
    }

    public synchronized String[] partnerEvaluation(String username, long teacherId) {
        String[] evaluation = partnerEvaluations.get(username + ":" + teacherId);
        return evaluation == null ? null : evaluation.clone();
    }

    public synchronized void status(long id, String status) {
        status("staff", id, status);
    }

    public synchronized boolean status(String actor, long id, String status) {
        TeacherProfile teacher = teacher(id);
        String role = userRole(actor);
        if (teacher == null || status == null || status.isBlank()) {
            return false;
        }
        boolean staff = "STAFF".equals(role) || "ADMIN".equals(role);
        boolean teacherOwner = "TEACHER".equals(role) && teacherIdForUser(actor) == id;
        if (!staff && !(teacherOwner && ("PENDING".equals(status) || "DRAFT".equals(status)))) {
            return false;
        }
        if (!Set.of("DRAFT", "PENDING", "READY", "UPDATE_REQUIRED").contains(status)) {
            return false;
        }
        teacher.setStatus(status);
        log(actor, "Updated " + teacher.getName() + " status to " + status);
        saveState();
        return true;
    }

    // =========================
    // INTERVIEWS
    // =========================

    public synchronized List<Interview> interviews() {
        return List.copyOf(interviews);
    }

    public synchronized List<Interview> visibleInterviews(String role, String username) {
        String authenticatedRole = userRole(username);
        if ("STAFF".equals(authenticatedRole) || "ADMIN".equals(authenticatedRole)) {
            return List.copyOf(interviews);
        }
        if ("TEACHER".equals(authenticatedRole)) {
            long teacherId = teacherIdForUser(username);
            if (teacherId < 0) {
                return List.of();
            }
            return interviews.stream()
                    .filter(interview -> interview.getTeacherId() == teacherId)
                    .toList();
        }
        if ("PARTNER".equals(authenticatedRole)) {
            AppUser user = users.get(username);
            if (user == null) {
                return List.of();
            }
            String partnerName = user.getFullName();
            if (partnerName == null || partnerName.isBlank()) {
                partnerName = user.getUsername();
            }
            final String currentPartner = partnerName;
            return interviews.stream()
                    .filter(interview -> interview.getPartner() != null
                            && (interview.getPartner().equalsIgnoreCase(currentPartner)
                            || interview.getPartner().equalsIgnoreCase(username)))
                    .toList();
        }
        return List.of();
    }

    public synchronized boolean requestInterview(
            long teacherId,
            String partnerUsername,
            String dateTime
    ) {
        TeacherProfile teacher = teacher(teacherId);
        AppUser partner = partnerUsername == null ? null : users.get(partnerUsername);
        if (teacher == null || partner == null || !"PARTNER".equals(partner.getRole())
                || !canPartnerAccessTeacher(partnerUsername, teacherId)) {
            return false;
        }
        LocalDateTime requestedAt;
        try {
            requestedAt = LocalDateTime.parse(dateTime);
        } catch (DateTimeParseException | NullPointerException ex) {
            return false;
        }
        if (!requestedAt.isAfter(LocalDateTime.now())) {
            return false;
        }
        AvailabilitySlot slot = availabilitySlots.stream()
                .filter(candidate -> candidate.teacherId() == teacherId && candidate.startAt().equals(requestedAt))
                .findFirst()
                .orElse(null);
        if (slot == null) {
            return false;
        }

        String partnerName = partner.getFullName();
        if (partnerName == null || partnerName.isBlank()) {
            partnerName = partner.getUsername();
        }

        long newId = interviewIds.incrementAndGet();
        String autoMeetUrl = generateMeetingLink(newId, teacher.getName(), partnerName, dateTime);
        Interview interview = new Interview(
                newId,
                teacherId,
                teacher.getName(),
                partnerName,
                dateTime,
                "PENDING",
                "Not reviewed",
                "",
                autoMeetUrl
        );
        interview.setEndDateTime(slot.endAt().toString());
        interviews.add(interview);
        availabilitySlots.remove(slot);

        log(partnerUsername, "Interview requested for " + teacher.getName() + " at " + dateTime);
        saveState();
        return true;
    }

    public synchronized void updateInterview(
            long id,
            String status,
            String result,
            String feedback,
            String meetUrl
    ) {
        updateInterview("staff", id, status, result, feedback, meetUrl);
    }

    public synchronized boolean updateInterview(
            String actor,
            long id,
            String status,
            String result,
            String feedback,
            String meetUrl
    ) {
        Interview interview = findInterview(id);
        String role = userRole(actor);
        boolean staff = "STAFF".equals(role) || "ADMIN".equals(role);
        boolean teacherOwner = "TEACHER".equals(role) && interview != null
                && teacherIdForUser(actor) == interview.getTeacherId();
        if (interview == null || (!staff && !teacherOwner)) {
            return false;
        }
        if (status != null && !status.isBlank()
                && !Set.of("PENDING", "CONFIRMED", "DECLINED", "COMPLETED").contains(status)) {
            return false;
        }
        if (meetUrl != null && !meetUrl.isBlank()) {
            interview.setMeetingUrl(meetUrl.trim());
        } else if (interview.getMeetingUrl() == null || interview.getMeetingUrl().isBlank()) {
            interview.setMeetingUrl(generateMeetingLink(interview));
        }
        if (status != null && !status.isBlank()) {
            interview.setStatus(status);
        }
        if (result != null && !result.isBlank()) {
            interview.setResult(result);
        }
        if (feedback != null) {
            interview.setFeedback(feedback);
        }
        log(actor, "Updated interview #" + id);
        saveState();
        return true;
    }

    public synchronized boolean updatePartnerFeedback(
            long interviewId,
            String username,
            String result,
            String feedback
    ) {
        if (username == null || result == null
                || !Set.of("PROCEED", "NOT_PROCEEDING", "ADDITIONAL_INTERVIEW").contains(result)
                || feedback == null || feedback.isBlank()) {
            return false;
        }
        AppUser user = users.get(username);
        if (user == null || !"PARTNER".equals(user.getRole())) {
            return false;
        }
        String partnerName = user.getFullName();
        if (partnerName == null || partnerName.isBlank()) {
            partnerName = user.getUsername();
        }
        Interview interview = findInterview(interviewId);
        if (interview == null || interview.getPartner() == null) {
            return false;
        }
        if (!interview.getPartner().equalsIgnoreCase(partnerName)
                && !interview.getPartner().equalsIgnoreCase(username)) {
            return false;
        }
        interview.setResult(result);
        interview.setFeedback(feedback);
        log(username, "Submitted feedback for interview #" + interviewId + ": " + result);
        saveState();
        return true;
    }

    public static String generateMeetingLink(Interview interview) {
        if (interview == null) {
            return "https://meet.jit.si/TeacherMatch-LiveRoom";
        }
        return generateMeetingLink(
                interview.getId(),
                interview.getTeacherName(),
                interview.getPartner(),
                interview.getDateTime()
        );
    }

    public static String generateMeetingLink(long interviewId, String teacherName, String partnerName, String dateTime) {
        String teacherSlug = slugify(teacherName, "Teacher");
        String partnerSlug = slugify(partnerName, "Partner");
        String seed = interviewId + "|" + nn(teacherName).trim().toLowerCase(Locale.ROOT)
                + "|" + nn(partnerName).trim().toLowerCase(Locale.ROOT)
                + "|" + nn(dateTime).trim();
        String token = shortHash(seed, 10);
        return "https://meet.jit.si/TeacherMatch-" + interviewId + "-" + teacherSlug + "-" + partnerSlug + "-" + token;
    }

    public static boolean openInBrowser(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://"))) {
            return false;
        }
        if (Boolean.getBoolean("tm.test.suppressAlerts")) {
            return true;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(trimmed));
                return true;
            }
        } catch (Exception ignored) {
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", trimmed).start();
                return true;
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", trimmed).start();
                return true;
            } else {
                new ProcessBuilder("xdg-open", trimmed).start();
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String slugify(String input, String fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        String cleaned = input.trim().replaceAll("[^A-Za-z0-9]+", "");
        if (cleaned.isEmpty()) {
            return fallback;
        }
        return cleaned.length() > 14 ? cleaned.substring(0, 14) : cleaned;
    }

    private static String shortHash(String input, int length) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(digest);
            return hex.substring(0, Math.min(length, hex.length()));
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(Math.abs(input.hashCode()));
        }
    }

    private Interview findInterview(long id) {
        return interviews.stream()
                .filter(interview -> interview.getId() == id)
                .findFirst()
                .orElse(null);
    }

    // =========================
    // SYSTEM LOGS
    // =========================

    public synchronized List<String[]> logs() {
        return List.copyOf(logs);
    }

    public synchronized void log(String actor, String action) {
        logs.add(
                0,
                new String[]{
                        LocalDateTime.now().toString().replace('T', ' '),
                        actor == null ? "SYSTEM" : actor,
                        action == null ? "" : action
                }
        );
        saveState();
    }
}
