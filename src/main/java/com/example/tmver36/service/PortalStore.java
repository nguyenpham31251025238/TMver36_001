package com.example.tmver36.service;

import com.example.tmver36.CatalogData;
import com.example.tmver36.model.AppUser;
import com.example.tmver36.model.AvailabilitySlot;
import com.example.tmver36.model.Interview;
import com.example.tmver36.model.TeacherDocument;
import com.example.tmver36.model.TeacherProfile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class PortalStore {

    private static final String STATE_FILE_NAME = "portal-store.properties";
    private static final String SCHEMA_VERSION = "2.0";
    private static final DateTimeFormatter UPDATED_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Map<String, AppUser> users = new LinkedHashMap<>();
    private final Map<String, Long> teacherProfilesByUser = new LinkedHashMap<>();
    private final List<TeacherProfile> teachers = new ArrayList<>();
    private final List<TeacherDocument> documents = new ArrayList<>();
    private final List<Interview> interviews = new ArrayList<>();
    private final List<AvailabilitySlot> availabilitySlots = new ArrayList<>();
    private final List<String[]> logs = new ArrayList<>();

    private final Set<String> granted = new LinkedHashSet<>();
    private final Map<String, String[]> partnerEvaluations = new LinkedHashMap<>();

    private final AtomicLong teacherIds = new AtomicLong(3);
    private final AtomicLong documentIds = new AtomicLong(0);
    private final AtomicLong interviewIds = new AtomicLong(3);
    private final AtomicLong availabilityIds = new AtomicLong(0);

    private Path customDataDir;
    private boolean suppressSave;
    private CloudSyncService cloudSync;

    public PortalStore() {
        this(null, new CloudSyncService());
    }

    public PortalStore(Path customDataDir) {
        this(customDataDir, new CloudSyncService(
                System.getProperty("tm.supabase.url"),
                System.getProperty("tm.supabase.key"),
                System.getProperty("tm.supabase.stateId"),
                customDataDir == null ? Boolean.parseBoolean(System.getProperty("tm.cloud.sync", "true"))
                        : Boolean.parseBoolean(System.getProperty("tm.cloud.sync", "false"))
        ));
    }

    public PortalStore(Path customDataDir, CloudSyncService cloudSync) {
        this.customDataDir = customDataDir;
        this.cloudSync = cloudSync != null ? cloudSync : new CloudSyncService();
        initializeStore();
    }

    private void initializeStore() {
        if (cloudSync != null && cloudSync.isEnabled()) {
            Properties remoteProps = cloudSync.pullProperties();
            if (remoteProps != null && loadFromProperties(remoteProps)) {
                saveLocalOnly();
                return;
            }
        }
        Path stateFile = getStateFilePath();
        if (Files.isRegularFile(stateFile)) {
            if (loadFromDisk(stateFile)) {
                return;
            }
        }
        seedDefaults();
        saveState();
    }

    public CloudSyncService getCloudSync() {
        return cloudSync;
    }

    public void setCloudSync(CloudSyncService cloudSync) {
        this.cloudSync = cloudSync;
    }

    public synchronized boolean syncFromCloud() {
        if (cloudSync == null || !cloudSync.isEnabled()) {
            return false;
        }
        Properties remoteProps = cloudSync.pullProperties();
        if (remoteProps != null && loadFromProperties(remoteProps)) {
            saveLocalOnly();
            return true;
        }
        return false;
    }

    public synchronized boolean pushToCloud() {
        if (cloudSync == null || !cloudSync.isEnabled()) {
            return false;
        }
        return cloudSync.pushProperties(toProperties());
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
        if (cloudSync != null && cloudSync.isEnabled()) {
            Properties remote = cloudSync.pullProperties();
            if (remote != null && loadFromProperties(remote)) {
                saveLocalOnly();
                return true;
            }
        }
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

    private Path materializeBundledResource(String resourcePath, long teacherId, String fileName) {
        try {
            Path targetDir = getDataDir().resolve("documents").resolve(Long.toString(teacherId));
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);
            if (!Files.isRegularFile(targetFile)) {
                try (InputStream in = PortalStore.class.getResourceAsStream(resourcePath)) {
                    if (in != null) {
                        Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            if (Files.isRegularFile(targetFile)) {
                return targetFile.toAbsolutePath();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String seedDocLocation(long teacherId, String sampleFileName) {
        Path p = materializeBundledResource("/com/example/tmver36/samples/" + sampleFileName, teacherId, sampleFileName);
        return p != null ? p.toString() : sampleFileName;
    }

    private void seedDefaults() {
        suppressSave = true;
        try {
            clearInMemoryState();

            users.put("admin", new AppUser("admin", "admin123", "ADMIN", "System Administrator"));
            users.put("staff", new AppUser("staff", "staff123", "STAFF", "Matching Staff"));
            users.put("partner", new AppUser("partner", "partner123", "PARTNER", "Bright English Center"));
            users.put("teacher", new AppUser("teacher", "teacher123", "TEACHER", "Emily Martin"));
            users.put("daniel", new AppUser("daniel", "daniel123", "TEACHER", "Daniel Walker"));
            users.put("sarah", new AppUser("sarah", "sarah123", "TEACHER", "Sarah Thompson"));

            String emilyCv = seedDocLocation(1L, "Emily_Martin_CV.pdf");
            String emilyCert = seedDocLocation(1L, "Emily_Martin_CELTA_TESOL.pdf");
            String emilyIntro = seedDocLocation(1L, "Emily_Martin_Intro_Video.html");
            String emilyDemo = seedDocLocation(1L, "Emily_Martin_Demo_Lesson.html");

            TeacherProfile emily = new TeacherProfile(
                    1L,
                    "Emily Martin",
                    "United Kingdom",
                    "Ho Chi Minh City",
                    "3–5 years",
                    "CELTA, TESOL",
                    "English Education",
                    "Bachelor's Degree",
                    "English Education",
                    "$1,500 - $2,000",
                    "READY",
                    "01/12/2026",
                    "emily.martin@example.com",
                    "+84 912 345 678",
                    "Experienced primary English teacher with CELTA and TESOL certifications. Passionate about interactive phonics, communicative ESL, and student-centered classroom activities.",
                    "photo/emily_avatar.png",
                    emilyCv,
                    emilyCert,
                    emilyIntro,
                    emilyDemo,
                    "Verified profile and teaching demo. Ready for partner introduction.",
                    "1,1,1,1,1"
            );
            emily.setTargetAgeGroup("Primary (6-11)");
            emily.setAvailableStartDate("01/12/2026");
            teachers.add(emily);
            teacherProfilesByUser.put("teacher", 1L);

            String danielCv = seedDocLocation(2L, "Daniel_Walker_CV.pdf");
            String danielCert = seedDocLocation(2L, "Daniel_Walker_TESOL.pdf");
            String danielIntro = seedDocLocation(2L, "Daniel_Walker_Intro_Video.html");
            String danielDemo = seedDocLocation(2L, "Daniel_Walker_Demo_Lesson.html");

            TeacherProfile daniel = new TeacherProfile(
                    2L,
                    "Daniel Walker",
                    "Canada",
                    "Hanoi",
                    "3–5 years",
                    "TESOL, IELTS",
                    "TESOL & Applied Linguistics",
                    "Master's Degree",
                    "TESOL & Applied Linguistics",
                    "$2,000 - $2,500",
                    "READY",
                    "15/01/2027",
                    "daniel.walker@example.com",
                    "+84 934 567 890",
                    "Academic English and IELTS specialist with a Master's degree in TESOL & Applied Linguistics. Proven track record helping secondary and high-school learners reach IELTS 7.5+.",
                    "photo/daniel_avatar.png",
                    danielCv,
                    danielCert,
                    danielIntro,
                    danielDemo,
                    "All academic credentials and IELTS teaching demo verified.",
                    "1,1,1,1,1"
            );
            daniel.setTargetAgeGroup("Secondary (12-17)");
            daniel.setAvailableStartDate("15/01/2027");
            teachers.add(daniel);
            teacherProfilesByUser.put("daniel", 2L);

            String sarahCv = seedDocLocation(3L, "Sarah_Thompson_CV.pdf");
            String sarahCert = seedDocLocation(3L, "Sarah_Thompson_CELTA_IELTS.pdf");
            String sarahIntro = seedDocLocation(3L, "Sarah_Thompson_Intro_Video.html");
            String sarahDemo = seedDocLocation(3L, "Sarah_Thompson_Demo_Lesson.html");

            TeacherProfile sarah = new TeacherProfile(
                    3L,
                    "Sarah Thompson",
                    "Australia",
                    "Da Nang",
                    "Over 5 years",
                    "CELTA, TEFL, IELTS",
                    "Applied Linguistics",
                    "Master's Degree",
                    "Applied Linguistics",
                    "Over $2,500",
                    "PENDING",
                    "15/11/2026",
                    "sarah.thompson@example.com",
                    "+84 978 222 333",
                    "Senior curriculum mentor and communication English instructor with 7+ years in international academies across Australia and Vietnam.",
                    "photo/sarah_avatar.png",
                    sarahCv,
                    sarahCert,
                    sarahIntro,
                    sarahDemo,
                    "",
                    "1,1,1,0,0"
            );
            sarah.setTargetAgeGroup("Adults");
            sarah.setAvailableStartDate("15/11/2026");
            teachers.add(sarah);
            teacherProfilesByUser.put("sarah", 3L);

            teacherIds.set(3);

            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 1L, "Emily_Martin_CV.pdf", "CV", "14/10/2026", emilyCv));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 1L, "Emily_Martin_CELTA_TESOL.pdf", "Certificates", "14/10/2026", emilyCert));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 1L, "Emily_Martin_Intro_Video.html", "Intro video", "15/10/2026", emilyIntro));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 1L, "Emily_Martin_Demo_Lesson.html", "Teaching video", "15/10/2026", emilyDemo));

            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 2L, "Daniel_Walker_CV.pdf", "CV", "12/10/2026", danielCv));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 2L, "Daniel_Walker_TESOL.pdf", "Certificates", "12/10/2026", danielCert));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 2L, "Daniel_Walker_Intro_Video.html", "Intro video", "12/10/2026", danielIntro));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 2L, "Daniel_Walker_Demo_Lesson.html", "Teaching video", "12/10/2026", danielDemo));

            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 3L, "Sarah_Thompson_CV.pdf", "CV", "13/10/2026", sarahCv));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 3L, "Sarah_Thompson_CELTA_IELTS.pdf", "Certificates", "13/10/2026", sarahCert));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 3L, "Sarah_Thompson_Intro_Video.html", "Intro video", "13/10/2026", sarahIntro));
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), 3L, "Sarah_Thompson_Demo_Lesson.html", "Teaching video", "13/10/2026", sarahDemo));

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
            availabilitySlots.add(new AvailabilitySlot(
                    availabilityIds.incrementAndGet(),
                    3L,
                    LocalDateTime.of(secondSlotDate, LocalTime.of(10, 0)),
                    LocalDateTime.of(secondSlotDate, LocalTime.of(11, 0))
            ));

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

    public synchronized Properties toProperties() {
        Properties props = new Properties();
        props.setProperty("schema.version", SCHEMA_VERSION);
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
            props.setProperty(prefix + "qualification", nn(t.getCertificates()));
            props.setProperty(prefix + "certificates", nn(t.getCertificates()));
            props.setProperty(prefix + "degree", nn(t.getDegree()));
            props.setProperty(prefix + "major", nn(t.getMajor()));
            props.setProperty(prefix + "expectedSalary", nn(t.getExpectedSalary()));
            props.setProperty(prefix + "status", nn(t.getStatus()));
            props.setProperty(prefix + "availability", nn(t.getAvailability()));
            props.setProperty(prefix + "avatarUrl", nn(t.getAvatarUrl()));
            props.setProperty(prefix + "videoUrl", nn(t.getVideoUrl()));
            props.setProperty(prefix + "introVideoUrl", nn(t.getIntroVideoUrl()));
            props.setProperty(prefix + "demoVideoUrl", nn(t.getDemoVideoUrl()));
            props.setProperty(prefix + "cvUrl", nn(t.getCvUrl()));
            props.setProperty(prefix + "certificateUrl", nn(t.getCertificateUrl()));
            props.setProperty(prefix + "subject", nn(t.getSubject()));
            props.setProperty(prefix + "bio", nn(t.getBio()));
            props.setProperty(prefix + "phone", nn(t.getPhone()));
            props.setProperty(prefix + "targetAgeGroup", nn(t.getTargetAgeGroup()));
            props.setProperty(prefix + "availableStartDate", nn(t.getAvailableStartDate()));
            props.setProperty(prefix + "timezone", nn(t.getTimezone()));
            props.setProperty(prefix + "staffFeedback", nn(t.getStaffFeedback()));
            props.setProperty(prefix + "reviewChecklist", nn(t.getReviewChecklist()));
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
        return props;
    }

    private synchronized void saveLocalOnly() {
        try {
            Path dataDir = getDataDir();
            Files.createDirectories(dataDir);
            Path stateFile = dataDir.resolve(STATE_FILE_NAME);
            Path tempFile = dataDir.resolve(STATE_FILE_NAME + ".tmp");
            Properties props = toProperties();
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                props.store(out, "TeacherMatch PortalStore persistent state");
            }
            Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }

    public synchronized void saveState() {
        if (suppressSave) {
            return;
        }
        Properties props = toProperties();
        try {
            Path dataDir = getDataDir();
            Files.createDirectories(dataDir);
            Path stateFile = dataDir.resolve(STATE_FILE_NAME);
            Path tempFile = dataDir.resolve(STATE_FILE_NAME + ".tmp");
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                props.store(out, "TeacherMatch PortalStore persistent state");
            }
            Files.move(tempFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
        if (cloudSync != null && cloudSync.isEnabled()) {
            cloudSync.pushProperties(props);
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
        return loadFromProperties(props);
    }

    public synchronized boolean loadFromProperties(Properties props) {
        if (props == null) {
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
                String name = props.getProperty(prefix + "fullName", props.getProperty(prefix + "name", ""));
                String certs = props.getProperty(prefix + "certificates", props.getProperty(prefix + "qualification", ""));
                String subject = props.getProperty(prefix + "subject", "English Education");
                String degree = props.getProperty(prefix + "degree", defaultDegreeForId(id, certs));
                String major = props.getProperty(prefix + "major", defaultMajorForId(id, subject));
                String expectedSalary = props.getProperty(prefix + "expectedSalary", defaultSalaryForId(id));
                String rawExp = props.getProperty(prefix + "experience", "3–5 years");
                String normalizedExp = CatalogData.normalizeExperienceBand(rawExp);
                String rawNation = props.getProperty(prefix + "nationality", defaultNationalityForId(id));
                String normalizedNation = normalizeLegacyNationality(rawNation, id);
                String avatarUrl = props.getProperty(prefix + "avatarUrl", defaultAvatarForId(id));
                String cvUrl = upgradeSampleDocPath(id, props.getProperty(prefix + "cvUrl", ""), "CV");
                String certUrl = upgradeSampleDocPath(id, props.getProperty(prefix + "certificateUrl", ""), "Certificates");
                String videoUrl = props.getProperty(prefix + "videoUrl", "");
                String introUrl = upgradeSampleDocPath(id, props.getProperty(prefix + "introVideoUrl", videoUrl), "Intro video");
                String demoUrl = upgradeSampleDocPath(id, props.getProperty(prefix + "demoVideoUrl", videoUrl), "Teaching video");

                TeacherProfile t = new TeacherProfile(
                        id,
                        name,
                        normalizedNation,
                        props.getProperty(prefix + "location", "Ho Chi Minh City"),
                        normalizedExp,
                        certs,
                        subject,
                        degree,
                        major,
                        expectedSalary,
                        props.getProperty(prefix + "status", "PENDING"),
                        props.getProperty(prefix + "availability", "01/12/2026"),
                        props.getProperty(prefix + "email", ""),
                        props.getProperty(prefix + "phone", ""),
                        props.getProperty(prefix + "bio", ""),
                        avatarUrl,
                        cvUrl,
                        certUrl,
                        introUrl,
                        demoUrl,
                        props.getProperty(prefix + "staffFeedback", ""),
                        props.getProperty(prefix + "reviewChecklist", id <= 2 ? "1,1,1,1,1" : "1,1,1,0,0")
                );
                t.setTargetAgeGroup(props.getProperty(prefix + "targetAgeGroup", "Primary (6-11)"));
                t.setAvailableStartDate(props.getProperty(prefix + "availableStartDate", "01/12/2026"));
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
                String location = upgradeSampleDocPath(teacherId, props.getProperty(prefix + "location", ""), type);
                if (!fileName.isBlank()) {
                    documents.add(new TeacherDocument(id, teacherId, fileName, type, updatedAt, location));
                    if (id > documentIds.get()) {
                        documentIds.set(id);
                    }
                }
            }

            ensureCoreTeacherDocuments();

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

    private void ensureCoreTeacherDocuments() {
        for (TeacherProfile t : teachers) {
            long tid = t.getId();
            if (tid >= 1L && tid <= 3L) {
                List<TeacherDocument> existing = documents(tid);
                if (existing.size() < 4) {
                    String prefix = tid == 1L ? "Emily_Martin" : (tid == 2L ? "Daniel_Walker" : "Sarah_Thompson");
                    String certFile = tid == 1L ? "Emily_Martin_CELTA_TESOL.pdf"
                            : (tid == 2L ? "Daniel_Walker_TESOL.pdf" : "Sarah_Thompson_CELTA_IELTS.pdf");
                    ensureDocIfMissing(tid, prefix + "_CV.pdf", "CV", "14/10/2026");
                    ensureDocIfMissing(tid, certFile, "Certificates", "14/10/2026");
                    ensureDocIfMissing(tid, prefix + "_Intro_Video.html", "Intro video", "15/10/2026");
                    ensureDocIfMissing(tid, prefix + "_Demo_Lesson.html", "Teaching video", "15/10/2026");
                }
            }
        }
    }

    private void ensureDocIfMissing(long teacherId, String sampleFile, String type, String date) {
        boolean present = documents.stream().anyMatch(d -> d.teacherId() == teacherId && d.type().equalsIgnoreCase(type));
        if (!present) {
            String loc = seedDocLocation(teacherId, sampleFile);
            documents.add(new TeacherDocument(documentIds.incrementAndGet(), teacherId, sampleFile, type, date, loc));
        }
    }

    private String upgradeSampleDocPath(long teacherId, String existingLocation, String type) {
        if (existingLocation != null && !existingLocation.isBlank()
                && !existingLocation.contains("example.com")) {
            return existingLocation;
        }
        if (teacherId < 1L || teacherId > 3L) {
            return existingLocation == null ? "" : existingLocation;
        }
        String prefix = teacherId == 1L ? "Emily_Martin" : (teacherId == 2L ? "Daniel_Walker" : "Sarah_Thompson");
        String certFile = teacherId == 1L ? "Emily_Martin_CELTA_TESOL.pdf"
                : (teacherId == 2L ? "Daniel_Walker_TESOL.pdf" : "Sarah_Thompson_CELTA_IELTS.pdf");
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.contains("cv")) {
            return seedDocLocation(teacherId, prefix + "_CV.pdf");
        }
        if (t.contains("cert")) {
            return seedDocLocation(teacherId, certFile);
        }
        if (t.contains("demo") || t.contains("teaching")) {
            return seedDocLocation(teacherId, prefix + "_Demo_Lesson.html");
        }
        if (t.contains("intro") || t.contains("video")) {
            return seedDocLocation(teacherId, prefix + "_Intro_Video.html");
        }
        return existingLocation == null ? "" : existingLocation;
    }

    private static String normalizeLegacyNationality(String raw, long id) {
        if (raw == null || raw.isBlank()) {
            return defaultNationalityForId(id);
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "american", "usa", "u.s.a." -> "United States";
            case "british", "uk", "english" -> "United Kingdom";
            case "australian" -> "Australia";
            case "canadian" -> "Canada";
            default -> raw.trim();
        };
    }

    private static String defaultNationalityForId(long id) {
        return switch ((int) id) {
            case 1 -> "United Kingdom";
            case 2 -> "Canada";
            case 3 -> "Australia";
            default -> "United States";
        };
    }

    private static String defaultDegreeForId(long id, String certs) {
        if (certs != null && certs.toLowerCase(Locale.ROOT).contains("master")) {
            return "Master's Degree";
        }
        return (id == 2L || id == 3L) ? "Master's Degree" : "Bachelor's Degree";
    }

    private static String defaultMajorForId(long id, String subject) {
        return switch ((int) id) {
            case 1 -> "English Education";
            case 2 -> "TESOL & Applied Linguistics";
            case 3 -> "Applied Linguistics";
            default -> (subject == null || subject.isBlank()) ? "English Education" : subject;
        };
    }

    private static String defaultSalaryForId(long id) {
        return switch ((int) id) {
            case 1 -> "$1,500 - $2,000";
            case 2 -> "$2,000 - $2,500";
            case 3 -> "Over $2,500";
            default -> "$1,500 - $2,000";
        };
    }

    public static String defaultAvatarForId(long id) {
        return switch ((int) id) {
            case 1 -> "photo/emily_avatar.png";
            case 2 -> "photo/daniel_avatar.png";
            case 3 -> "photo/sarah_avatar.png";
            default -> "";
        };
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
        String trimmedUser = username.trim();
        AppUser user = users.get(trimmedUser);
        if (user == null && cloudSync != null && cloudSync.isEnabled()) {
            syncFromCloud();
            user = users.get(trimmedUser);
        }
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
                "United States",
                "Ho Chi Minh City",
                "1–2 years",
                "TEFL",
                "DRAFT",
                "",
                ""
        );
        created.setDegree("Bachelor's Degree");
        created.setMajor("English Education");
        created.setExpectedSalary("$1,500 - $2,000");
        created.setReviewChecklist("0,0,0,0,0");
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
            target.setCertificates(updated.getCertificates());
            target.setDegree(updated.getDegree());
            target.setMajor(updated.getMajor());
            target.setExpectedSalary(updated.getExpectedSalary());
            target.setStatus(updated.getStatus());
            target.setAvailability(updated.getAvailability());
            target.setAvatarUrl(updated.getAvatarUrl());
            target.setCvUrl(updated.getCvUrl());
            target.setCertificateUrl(updated.getCertificateUrl());
            target.setIntroVideoUrl(updated.getIntroVideoUrl());
            target.setDemoVideoUrl(updated.getDemoVideoUrl());
            target.setSubject(updated.getSubject());
            target.setBio(updated.getBio());
            target.setPhone(updated.getPhone());
            target.setTargetAgeGroup(updated.getTargetAgeGroup());
            target.setAvailableStartDate(updated.getAvailableStartDate());
            target.setTimezone(updated.getTimezone());
            target.setStaffFeedback(updated.getStaffFeedback());
            target.setReviewChecklist(updated.getReviewChecklist());
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

    public synchronized boolean saveReviewState(
            String actor,
            long teacherId,
            String status,
            String staffFeedback,
            boolean[] checklistFlags,
            String bioOverride
    ) {
        String role = userRole(actor);
        if (!("STAFF".equals(role) || "ADMIN".equals(role))) {
            return false;
        }
        TeacherProfile teacher = teacher(teacherId);
        if (teacher == null) {
            return false;
        }
        if (checklistFlags != null && checklistFlags.length > 0) {
            teacher.setChecklistFlags(checklistFlags);
        }
        if (staffFeedback != null) {
            teacher.setStaffFeedback(staffFeedback.trim());
        }
        if (bioOverride != null && !bioOverride.isBlank()) {
            teacher.setBio(bioOverride.trim());
        }
        if (status != null && !status.isBlank()) {
            String normalizedStatus = CatalogData.normalizeStatusCode(status);
            teacher.setStatus(normalizedStatus);
        }
        log(actor, "Reviewed profile for " + teacher.getName() + " -> " + teacher.getStatus());
        saveState();
        return true;
    }

    public synchronized String updateTeacherAvatar(String actor, long teacherId, Path sourceImage) {
        String role = userRole(actor);
        boolean isStaffOrAdmin = "STAFF".equals(role) || "ADMIN".equals(role);
        boolean isOwner = "TEACHER".equals(role) && teacherIdForUser(actor) == teacherId;
        if (!isStaffOrAdmin && !isOwner) {
            return null;
        }
        TeacherProfile teacher = teacher(teacherId);
        if (teacher == null || sourceImage == null || !Files.isRegularFile(sourceImage)) {
            return null;
        }
        try {
            Path targetDir = getDataDir().resolve("documents").resolve(Long.toString(teacherId));
            Files.createDirectories(targetDir);
            String safeName = "avatar_" + teacherId + "_" + sourceImage.getFileName().toString().replaceAll("[^a-zA-Z0-9._-]", "_");
            Path dest = targetDir.resolve(safeName);
            Files.copy(sourceImage, dest, StandardCopyOption.REPLACE_EXISTING);
            String storedPath = dest.toAbsolutePath().toString();
            teacher.setAvatarUrl(storedPath);
            if (cloudSync != null && cloudSync.isEnabled()) {
                cloudSync.uploadSharedFile("avatar:" + teacherId, teacherId, safeName, dest);
            }
            log(actor, "Updated profile avatar for " + teacher.getName());
            saveState();
            return storedPath;
        } catch (IOException ex) {
            return null;
        }
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
        Path copiedLocalFile = null;
        String safeFileName = fileName.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        if (!storedLocation.isEmpty() && !storedLocation.startsWith("http://") && !storedLocation.startsWith("https://")) {
            try {
                Path src = storedLocation.startsWith("file:")
                        ? Paths.get(java.net.URI.create(storedLocation))
                        : Paths.get(storedLocation);
                if (Files.isRegularFile(src)) {
                    Path targetDir = getDataDir().resolve("documents").resolve(Long.toString(teacherId));
                    Files.createDirectories(targetDir);
                    Path dest = targetDir.resolve(safeFileName);
                    if (!src.toAbsolutePath().equals(dest.toAbsolutePath())) {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                    copiedLocalFile = dest.toAbsolutePath();
                    storedLocation = copiedLocalFile.toString();
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
            if (item.teacherId() == teacherId && isSameDocCategory(item.type(), type.trim())) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex >= 0) {
            documents.set(existingIndex, doc);
        } else {
            documents.add(doc);
        }

        if (copiedLocalFile != null && cloudSync != null && cloudSync.isEnabled()) {
            String fileKey = "doc:" + teacherId + ":" + normalizeDocCategoryKey(type);
            cloudSync.uploadSharedFile(fileKey, teacherId, safeFileName, copiedLocalFile);
        }

        syncTeacherDocumentFields(teacher, doc.type(), storedLocation, doc.fileName());
        log(actor, "Updated document (" + type.trim() + "): " + fileName.trim());
        saveState();
        return doc;
    }

    private static boolean isSameDocCategory(String a, String b) {
        return normalizeDocCategoryKey(a).equals(normalizeDocCategoryKey(b));
    }

    public static String normalizeDocCategoryKey(String type) {
        if (type == null) return "other";
        String lower = type.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("cv") || lower.contains("resume")) return "cv";
        if (lower.contains("cert")) return "certificates";
        if (lower.contains("intro")) return "intro_video";
        if (lower.contains("demo") || lower.contains("teaching")) return "teaching_video";
        return lower.replaceAll("[^a-z0-9]+", "_");
    }

    public synchronized Path resolveDocumentLocalFile(TeacherDocument doc) {
        if (doc == null || doc.location() == null || doc.location().isBlank()) {
            return null;
        }
        String loc = doc.location().trim();
        if (loc.startsWith("http://") || loc.startsWith("https://")) {
            return null;
        }
        try {
            Path p = loc.startsWith("file:") ? Paths.get(java.net.URI.create(loc)) : Paths.get(loc);
            if (Files.isRegularFile(p)) {
                return p;
            }
        } catch (Exception ignored) {
        }
        String safeName = doc.fileName().replaceAll("[^a-zA-Z0-9._-]", "_");
        Path localTarget = getDataDir().resolve("documents").resolve(Long.toString(doc.teacherId())).resolve(safeName);
        if (Files.isRegularFile(localTarget)) {
            return localTarget;
        }
        if (cloudSync != null && cloudSync.isEnabled()) {
            String key = "doc:" + doc.teacherId() + ":" + normalizeDocCategoryKey(doc.type());
            if (cloudSync.downloadSharedFileIfMissing(key, localTarget)) {
                return localTarget;
            }
        }
        return materializeBundledResource("/com/example/tmver36/samples/" + doc.fileName(), doc.teacherId(), safeName);
    }

    public synchronized boolean openDocument(String actor, TeacherDocument doc) {
        if (doc == null) {
            return false;
        }
        String loc = doc.location() == null ? "" : doc.location().trim();
        if (loc.startsWith("http://") || loc.startsWith("https://")) {
            boolean ok = openInBrowser(loc);
            if (ok) {
                log(actor, "Opened document URL " + doc.fileName());
            }
            return ok;
        }
        Path local = resolveDocumentLocalFile(doc);
        if (local == null || !Files.isRegularFile(local)) {
            return false;
        }
        if (Boolean.getBoolean("tm.test.suppressAlerts")) {
            log(actor, "Opened document " + doc.fileName());
            return true;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(local.toFile());
                log(actor, "Opened document " + doc.fileName());
                return true;
            }
        } catch (Exception ignored) {
        }
        boolean ok = openInBrowser(local.toUri().toString());
        if (ok) {
            log(actor, "Opened document " + doc.fileName());
        }
        return ok;
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
            String key = normalizeDocCategoryKey(target.type());
            switch (key) {
                case "cv" -> teacher.setCvUrl("");
                case "certificates" -> teacher.setCertificateUrl("");
                case "intro_video" -> teacher.setIntroVideoUrl("");
                case "teaching_video" -> teacher.setDemoVideoUrl("");
            }
        }
        log(actor, "Deleted document (" + target.type() + "): " + target.fileName());
        saveState();
        return true;
    }

    private void syncTeacherDocumentFields(TeacherProfile teacher, String type, String location, String fileName) {
        String key = normalizeDocCategoryKey(type);
        switch (key) {
            case "cv" -> teacher.setCvUrl(location);
            case "certificates" -> {
                teacher.setCertificateUrl(location);
                if (teacher.getCertificates() == null || teacher.getCertificates().isBlank()) {
                    teacher.setCertificates(fileName);
                }
            }
            case "intro_video" -> teacher.setIntroVideoUrl(location);
            case "teaching_video" -> teacher.setDemoVideoUrl(location);
            default -> {
                if (type != null && type.toLowerCase(Locale.ROOT).contains("video")) {
                    teacher.setVideoUrl(location);
                }
            }
        }
    }

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

    public synchronized int countPartnersGrantedForTeacher(long teacherId) {
        String suffix = ":" + teacherId;
        int count = 0;
        for (String entry : granted) {
            if (entry != null && entry.endsWith(suffix)) {
                count++;
            }
        }
        return count;
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
        String normalized = CatalogData.normalizeStatusCode(status);
        boolean staff = "STAFF".equals(role) || "ADMIN".equals(role);
        boolean teacherOwner = "TEACHER".equals(role) && teacherIdForUser(actor) == id;
        if (!staff && !(teacherOwner && ("PENDING".equals(normalized) || "DRAFT".equals(normalized)))) {
            return false;
        }
        if (!Set.of("DRAFT", "PENDING", "READY", "UPDATE_REQUIRED").contains(normalized)) {
            return false;
        }
        teacher.setStatus(normalized);
        log(actor, "Updated " + teacher.getName() + " status to " + normalized);
        saveState();
        return true;
    }

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
        if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://") || trimmed.startsWith("file:"))) {
            File local = new File(trimmed);
            if (local.exists()) {
                trimmed = local.toURI().toString();
            } else {
                return false;
            }
        }
        if (Boolean.getBoolean("tm.test.suppressAlerts")) {
            return true;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                if (trimmed.startsWith("file:")) {
                    java.awt.Desktop.getDesktop().open(new File(java.net.URI.create(trimmed)));
                    return true;
                } else if (java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(trimmed));
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
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
