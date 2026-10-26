package com.example.tmver36.service;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;

/**
 * Lightweight, zero-external-dependency Cloud Database Synchronization service
 * backed by Supabase PostgREST (`tm_portal_state` and `tm_shared_files`) via JDK 21 HttpClient.
 *
 * Uses only a public/anon scoped key with RLS policies enabled.
 * Can be disabled in isolated unit tests via `-Dtm.cloud.sync=false` or custom sync key.
 */
public final class CloudSyncService {

    private static final String DEFAULT_SUPABASE_URL = "https://fvlbezbjqupwlodqelaz.supabase.co";
    private static final String DEFAULT_PUBLISHABLE_KEY = "sb_publishable_3LKenU-kYZH2qL4eNKfWcw_znTEqVP4";
    private static final String DEFAULT_STATE_ID = "tmver36_shared_v2";
    private static final int MAX_SYNC_FILE_BYTES = 4 * 1024 * 1024; // 4 MB safety cap

    private final HttpClient httpClient;
    private final String supabaseUrl;
    private final String apiKey;
    private final String stateId;
    private volatile boolean enabled;

    public CloudSyncService() {
        this(
                resolveSetting("tm.supabase.url", "TM_SUPABASE_URL", DEFAULT_SUPABASE_URL),
                resolveSetting("tm.supabase.key", "TM_SUPABASE_KEY", DEFAULT_PUBLISHABLE_KEY),
                resolveSetting("tm.supabase.stateId", "TM_SUPABASE_STATE_ID", DEFAULT_STATE_ID),
                isCloudSyncEnabledByDefault()
        );
    }

    public CloudSyncService(String supabaseUrl, String apiKey, String stateId, boolean enabled) {
        this.supabaseUrl = supabaseUrl == null ? DEFAULT_SUPABASE_URL : supabaseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? DEFAULT_PUBLISHABLE_KEY : apiKey.trim();
        this.stateId = stateId == null || stateId.isBlank() ? DEFAULT_STATE_ID : stateId.trim();
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
    }

    private static boolean isCloudSyncEnabledByDefault() {
        String prop = System.getProperty("tm.cloud.sync");
        if (prop != null) {
            return Boolean.parseBoolean(prop);
        }
        String env = System.getenv("TM_CLOUD_SYNC");
        if (env != null) {
            return Boolean.parseBoolean(env);
        }
        // If running inside Surefire unit tests with temporary directories, disable unless explicitly enabled
        if (Boolean.getBoolean("tm.test.suppressAlerts")) {
            return false;
        }
        return true;
    }

    private static String resolveSetting(String sysProp, String envVar, String fallback) {
        String val = System.getProperty(sysProp);
        if (val != null && !val.isBlank()) {
            return val.trim();
        }
        String env = System.getenv(envVar);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return fallback;
    }

    public boolean isEnabled() {
        return enabled && !supabaseUrl.isBlank() && !apiKey.isBlank();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStateId() {
        return stateId;
    }

    public boolean pushProperties(Properties props) {
        if (!isEnabled() || props == null) {
            return false;
        }
        try {
            StringWriter sw = new StringWriter();
            props.store(sw, "TeacherMatch Cloud Sync State");
            String bodyJson = "{\"state_id\":" + jsonString(stateId)
                    + ",\"state_properties\":" + jsonString(sw.toString())
                    + "}";

            String endpoint = supabaseUrl + "/rest/v1/tm_portal_state?on_conflict=state_id";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("apikey", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    public Properties pullProperties() {
        if (!isEnabled()) {
            return null;
        }
        try {
            String encodedId = URLEncoder.encode(stateId, StandardCharsets.UTF_8);
            String endpoint = supabaseUrl + "/rest/v1/tm_portal_state?state_id=eq." + encodedId + "&select=state_properties";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("apikey", apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            String raw = extractJsonField(response.body(), "state_properties");
            if (raw == null || raw.isBlank()) {
                return null;
            }
            Properties props = new Properties();
            props.load(new StringReader(raw));
            return props;
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean uploadSharedFile(String fileKey, long teacherId, String fileName, Path localFile) {
        if (!isEnabled() || fileKey == null || fileKey.isBlank() || localFile == null || !Files.isRegularFile(localFile)) {
            return false;
        }
        try {
            long size = Files.size(localFile);
            if (size <= 0 || size > MAX_SYNC_FILE_BYTES) {
                return false;
            }
            byte[] bytes = Files.readAllBytes(localFile);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String contentType = guessContentType(fileName);
            String bodyJson = "{\"file_key\":" + jsonString(fileKey)
                    + ",\"teacher_id\":" + teacherId
                    + ",\"file_name\":" + jsonString(fileName == null ? localFile.getFileName().toString() : fileName)
                    + ",\"content_type\":" + jsonString(contentType)
                    + ",\"base64_data\":" + jsonString(base64)
                    + "}";

            String endpoint = supabaseUrl + "/rest/v1/tm_shared_files?on_conflict=file_key";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean downloadSharedFileIfMissing(String fileKey, Path targetPath) {
        if (!isEnabled() || fileKey == null || fileKey.isBlank() || targetPath == null) {
            return false;
        }
        if (Files.isRegularFile(targetPath)) {
            return true;
        }
        try {
            String encodedKey = URLEncoder.encode(fileKey, StandardCharsets.UTF_8);
            String endpoint = supabaseUrl + "/rest/v1/tm_shared_files?file_key=eq." + encodedKey + "&select=base64_data";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", apiKey)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            String base64 = extractJsonField(response.body(), "base64_data");
            if (base64 == null || base64.isBlank()) {
                return false;
            }
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.write(targetPath, bytes);
            return Files.isRegularFile(targetPath);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String guessContentType(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".mp4")) return "video/mp4";
        return "application/octet-stream";
    }

    static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    static String extractJsonField(String json, String fieldName) {
        if (json == null || fieldName == null) {
            return null;
        }
        String marker = "\"" + fieldName + "\":";
        int idx = json.indexOf(marker);
        if (idx < 0) {
            return null;
        }
        int start = idx + marker.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length() || json.charAt(start) != '"') {
            return null;
        }
        start++; // skip opening quote
        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                switch (c) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            String hex = json.substring(i + 1, i + 5);
                            out.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default -> out.append(c);
                }
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }
}
