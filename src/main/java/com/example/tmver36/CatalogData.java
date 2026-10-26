package com.example.tmver36;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standardized catalog data and matching utilities for TeacherMatch:
 * - Full A-Z world countries + "Other"
 * - Standardized 4 Experience tiers
 * - Standardized Degrees & Majors
 * - Standardized Certificates (multi-select)
 * - Standardized Expected Salary bands
 * - Status normalization (qCV / qEP / Update Required)
 */
public final class CatalogData {

    private CatalogData() {
    }

    public static final List<String> EXPERIENCE_OPTIONS = List.of(
            "Under 1 year",
            "1–2 years",
            "3–5 years",
            "Over 5 years"
    );

    public static final List<String> EXPERIENCE_FILTER_OPTIONS = List.of(
            "All",
            "Under 1 year",
            "1–2 years",
            "3–5 years",
            "Over 5 years"
    );

    public static final List<String> CERTIFICATE_OPTIONS = List.of(
            "CELTA",
            "TEFL",
            "TESOL",
            "IELTS",
            "TOEFL",
            "DELTA",
            "Cambridge TKT",
            "PGCE"
    );

    public static final List<String> CERTIFICATE_FILTER_OPTIONS = List.of(
            "All",
            "CELTA",
            "TEFL",
            "TESOL",
            "IELTS",
            "TOEFL",
            "DELTA",
            "Cambridge TKT",
            "PGCE"
    );

    public static final List<String> DEGREE_OPTIONS = List.of(
            "Bachelor's Degree",
            "Master's Degree",
            "Doctorate (PhD)",
            "Associate / Diploma"
    );

    public static final List<String> DEGREE_FILTER_OPTIONS = List.of(
            "All",
            "Bachelor's Degree",
            "Master's Degree",
            "Doctorate (PhD)",
            "Associate / Diploma"
    );

    public static final List<String> MAJOR_OPTIONS = List.of(
            "English Education",
            "TESOL & Applied Linguistics",
            "Applied Linguistics",
            "Secondary Education",
            "Elementary / Primary Education",
            "Early Childhood Education",
            "English Literature",
            "Modern Languages",
            "Mathematics & STEM",
            "General Education"
    );

    public static final List<String> MAJOR_FILTER_OPTIONS;
    static {
        List<String> majors = new ArrayList<>();
        majors.add("All");
        majors.addAll(MAJOR_OPTIONS);
        majors.add("Bachelor's Degree");
        majors.add("Master's Degree");
        MAJOR_FILTER_OPTIONS = List.copyOf(majors);
    }

    public static final List<String> EXPECTED_SALARY_OPTIONS = List.of(
            "Under $1,500",
            "$1,500 - $2,000",
            "$2,000 - $2,500",
            "Over $2,500"
    );

    public static final List<String> EXPECTED_SALARY_FILTER_OPTIONS = List.of(
            "All",
            "Under $1,500",
            "$1,500 - $2,000",
            "$2,000 - $2,500",
            "Over $2,500"
    );

    public static final List<String> STAFF_STATUS_FILTER_OPTIONS = List.of(
            "All",
            "qCV",
            "qEP",
            "Update Required"
    );

    public static final List<String> WORLD_COUNTRIES_AZ = List.of(
            "Afghanistan", "Albania", "Algeria", "Andorra", "Angola", "Antigua and Barbuda",
            "Argentina", "Armenia", "Australia", "Austria", "Azerbaijan", "Bahamas", "Bahrain",
            "Bangladesh", "Barbados", "Belarus", "Belgium", "Belize", "Benin", "Bhutan",
            "Bolivia", "Bosnia and Herzegovina", "Botswana", "Brazil", "Brunei", "Bulgaria",
            "Burkina Faso", "Burundi", "Cabo Verde", "Cambodia", "Cameroon", "Canada",
            "Central African Republic", "Chad", "Chile", "China", "Colombia", "Comoros",
            "Congo", "Costa Rica", "Croatia", "Cuba", "Cyprus", "Czech Republic", "Denmark",
            "Djibouti", "Dominica", "Dominican Republic", "Ecuador", "Egypt", "El Salvador",
            "Equatorial Guinea", "Eritrea", "Estonia", "Eswatini", "Ethiopia", "Fiji",
            "Finland", "France", "Gabon", "Gambia", "Georgia", "Germany", "Ghana", "Greece",
            "Grenada", "Guatemala", "Guinea", "Guinea-Bissau", "Guyana", "Haiti", "Honduras",
            "Hungary", "Iceland", "India", "Indonesia", "Iran", "Iraq", "Ireland", "Israel",
            "Italy", "Jamaica", "Japan", "Jordan", "Kazakhstan", "Kenya", "Kiribati",
            "Kuwait", "Kyrgyzstan", "Laos", "Latvia", "Lebanon", "Lesotho", "Liberia",
            "Libya", "Liechtenstein", "Lithuania", "Luxembourg", "Madagascar", "Malawi",
            "Malaysia", "Maldives", "Mali", "Malta", "Marshall Islands", "Mauritania",
            "Mauritius", "Mexico", "Micronesia", "Moldova", "Monaco", "Mongolia", "Montenegro",
            "Morocco", "Mozambique", "Myanmar", "Namibia", "Nauru", "Nepal", "Netherlands",
            "New Zealand", "Nicaragua", "Niger", "Nigeria", "North Korea", "North Macedonia",
            "Norway", "Oman", "Pakistan", "Palau", "Palestine", "Panama", "Papua New Guinea",
            "Paraguay", "Peru", "Philippines", "Poland", "Portugal", "Qatar", "Romania",
            "Russia", "Rwanda", "Saint Kitts and Nevis", "Saint Lucia",
            "Saint Vincent and the Grenadines", "Samoa", "San Marino", "Sao Tome and Principe",
            "Saudi Arabia", "Senegal", "Serbia", "Seychelles", "Sierra Leone", "Singapore",
            "Slovakia", "Slovenia", "Solomon Islands", "Somalia", "South Africa", "South Korea",
            "South Sudan", "Spain", "Sri Lanka", "Sudan", "Suriname", "Sweden", "Switzerland",
            "Syria", "Taiwan", "Tajikistan", "Tanzania", "Thailand", "Timor-Leste", "Togo",
            "Tonga", "Trinidad and Tobago", "Tunisia", "Turkey", "Turkmenistan", "Tuvalu",
            "Uganda", "Ukraine", "United Arab Emirates", "United Kingdom", "United States",
            "Uruguay", "Uzbekistan", "Vanuatu", "Vatican City", "Venezuela", "Vietnam",
            "Yemen", "Zambia", "Zimbabwe", "Other"
    );

    public static final List<String> NATIONALITY_FILTER_OPTIONS;
    static {
        List<String> list = new ArrayList<>(WORLD_COUNTRIES_AZ.size() + 1);
        list.add("All");
        list.addAll(WORLD_COUNTRIES_AZ);
        NATIONALITY_FILTER_OPTIONS = List.copyOf(list);
    }

    public static List<String> filterCountries(String query, boolean includeAll) {
        String q = normalizeSearch(query);
        List<String> source = includeAll ? NATIONALITY_FILTER_OPTIONS : WORLD_COUNTRIES_AZ;
        if (q.isEmpty()) {
            return source;
        }
        List<String> matches = new ArrayList<>();
        if (includeAll && "all".contains(q)) {
            matches.add("All");
        }
        for (String country : WORLD_COUNTRIES_AZ) {
            if (normalizeSearch(country).contains(q) || "Other".equalsIgnoreCase(country)) {
                matches.add(country);
            }
        }
        return matches;
    }

    public static String normalizeExperienceBand(String rawExperience) {
        if (rawExperience == null || rawExperience.isBlank()) {
            return "1–2 years";
        }
        String text = rawExperience.trim().replace('-', '–');
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("under 1") || lower.contains("< 1") || lower.contains("0–1")) {
            return "Under 1 year";
        }
        if (lower.contains("1–2") || lower.contains("1-2") || lower.contains("1 to 2")) {
            return "1–2 years";
        }
        if (lower.contains("3–5") || lower.contains("3-5") || lower.contains("2–4") || lower.contains("2-4")) {
            return "3–5 years";
        }
        if (lower.contains("over 5") || lower.contains("5+") || lower.contains("> 5")) {
            return "Over 5 years";
        }
        Matcher m = Pattern.compile("(\\d+)").matcher(lower);
        if (m.find()) {
            int years = Integer.parseInt(m.group(1));
            if (years < 1) {
                return "Under 1 year";
            }
            if (years <= 2) {
                return "1–2 years";
            }
            if (years <= 5) {
                return "3–5 years";
            }
            return "Over 5 years";
        }
        return "3–5 years";
    }

    public static boolean matchesExperienceFilter(String profileExperience, String filterValue) {
        if (filterValue == null || filterValue.isBlank() || "All".equalsIgnoreCase(filterValue)) {
            return true;
        }
        String normalizedProfile = normalizeExperienceBand(profileExperience);
        String normalizedFilter = normalizeExperienceBand(filterValue);
        return normalizedProfile.equalsIgnoreCase(normalizedFilter);
    }

    public static String normalizeStatusCode(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return "qCV";
        }
        String u = rawStatus.trim().toUpperCase(Locale.ROOT);
        return switch (u) {
            case "READY", "QEP" -> "READY";
            case "UPDATE_REQUIRED", "NEED_UPDATE", "UPDATE REQUIRED", "NEEDS UPDATE" -> "UPDATE_REQUIRED";
            case "DRAFT" -> "DRAFT";
            case "PENDING", "QCV" -> "PENDING";
            default -> "PENDING";
        };
    }

    public static String formatStatusBadge(String rawStatus) {
        String code = normalizeStatusCode(rawStatus);
        return switch (code) {
            case "READY" -> "qEP · Ready";
            case "UPDATE_REQUIRED" -> "Update Required";
            case "DRAFT" -> "Draft";
            default -> "qCV · Reviewing";
        };
    }

    public static String formatShortStatusCode(String rawStatus) {
        String code = normalizeStatusCode(rawStatus);
        return switch (code) {
            case "READY" -> "qEP";
            case "UPDATE_REQUIRED" -> "Update Required";
            case "DRAFT" -> "Draft";
            default -> "qCV";
        };
    }

    public static boolean matchesStatusFilter(String profileStatus, String filterStatus) {
        if (filterStatus == null || filterStatus.isBlank() || "All".equalsIgnoreCase(filterStatus)) {
            return true;
        }
        String f = filterStatus.trim().toUpperCase(Locale.ROOT);
        String code = normalizeStatusCode(profileStatus);
        if (f.equals("QEP") || f.equals("READY") || f.startsWith("QEP")) {
            return "READY".equals(code);
        }
        if (f.equals("QCV") || f.equals("PENDING") || f.startsWith("QCV")) {
            return "PENDING".equals(code) || "DRAFT".equals(code);
        }
        if (f.contains("UPDATE")) {
            return "UPDATE_REQUIRED".equals(code);
        }
        return code.equalsIgnoreCase(f);
    }

    public static List<String> parseCertificates(String rawCertificates) {
        if (rawCertificates == null || rawCertificates.isBlank()) {
            return List.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : rawCertificates.split("[,;/|]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return new ArrayList<>(set);
    }

    public static String joinCertificates(Iterable<String> certs) {
        Set<String> cleaned = new LinkedHashSet<>();
        if (certs != null) {
            for (String c : certs) {
                if (c != null && !c.isBlank()) {
                    cleaned.add(c.trim());
                }
            }
        }
        return String.join(", ", cleaned);
    }

    public static boolean matchesCertificatesFilter(String profileCertificates, Collection<String> selectedFilters) {
        if (selectedFilters == null || selectedFilters.isEmpty()) {
            return true;
        }
        String hay = normalizeSearch(profileCertificates);
        for (String selected : selectedFilters) {
            if (selected == null || selected.isBlank() || "All".equalsIgnoreCase(selected)) {
                continue;
            }
            String needle = normalizeSearch(selected);
            if (!needle.isEmpty() && !hay.contains(needle)) {
                return false;
            }
        }
        return true;
    }

    public static boolean matchesSalaryFilter(String profileSalary, String salaryFilter) {
        if (salaryFilter == null || salaryFilter.isBlank() || "All".equalsIgnoreCase(salaryFilter)) {
            return true;
        }
        if (profileSalary == null || profileSalary.isBlank()) {
            return false;
        }
        String pNorm = normalizeSearch(profileSalary);
        String fNorm = normalizeSearch(salaryFilter);
        if (pNorm.equals(fNorm) || pNorm.contains(fNorm)) {
            return true;
        }
        List<Integer> nums = extractNumbers(profileSalary);
        if (nums.isEmpty()) {
            return false;
        }
        int avg = (int) nums.stream().mapToInt(Integer::intValue).average().orElse(0);
        List<Integer> filterNums = extractNumbers(salaryFilter);
        if (fNorm.contains("under") && !filterNums.isEmpty()) {
            return avg < filterNums.get(0);
        }
        if (fNorm.contains("over") && !filterNums.isEmpty()) {
            return avg > filterNums.get(0);
        }
        if (filterNums.size() >= 2) {
            int low = Math.min(filterNums.get(0), filterNums.get(1));
            int high = Math.max(filterNums.get(0), filterNums.get(1));
            return avg >= low && avg <= high;
        }
        if (filterNums.size() == 1) {
            return avg == filterNums.get(0);
        }
        return false;
    }

    private static List<Integer> extractNumbers(String text) {
        List<Integer> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = Pattern.compile("(\\d[\\d,]*)").matcher(text);
        while (m.find()) {
            String raw = m.group(1).replace(",", "");
            try {
                out.add(Integer.parseInt(raw));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    public static String normalizeSearch(String value) {
        if (value == null) {
            return "";
        }
        String nfd = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return nfd.toLowerCase(Locale.ROOT).trim();
    }

    public static String badgeStyleForStatus(String rawStatus) {
        String code = normalizeStatusCode(rawStatus);
        return switch (code) {
            case "READY" -> "badge-green";
            case "UPDATE_REQUIRED" -> "badge-amber";
            case "DRAFT" -> "badge-gray";
            default -> "badge-blue";
        };
    }

    public static boolean matchesMajorOrDegreeFilter(String degree, String major, String filterValue) {
        if (filterValue == null || filterValue.isBlank() || "All".equalsIgnoreCase(filterValue)) {
            return true;
        }
        String f = normalizeSearch(filterValue);
        String d = normalizeSearch(degree);
        String m = normalizeSearch(major);
        return d.contains(f) || m.contains(f) || f.contains(d) && !d.isEmpty() || f.contains(m) && !m.isEmpty();
    }
}
