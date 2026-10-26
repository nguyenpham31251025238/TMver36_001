# Báo Cáo Tiến Độ Hoàn Thiện Dự Án TMver36_001

**Cập nhật lần cuối:** 2026-10-25 01:15 (UTC+7)
**Repository:** `https://github.com/nguyenpham31251025238/TMver36_001.git`
**GitHub Release:** `https://github.com/nguyenpham31251025238/TMver36_001/releases/tag/v1.0.0`
**Bản chuẩn làm việc (Canonical):** `C:\Users\LAPTOP\OneDrive\Desktop\TMver36_001-master` (nhánh `master`)

---

## 1. Tổng Quan Kiểm Tra & Sao Lưu (Phase 1)

- Đã kiểm tra toàn bộ các thư mục dự án trên máy Windows và tạo bản sao lưu đầy đủ tại `C:\Users\LAPTOP\TMver36_001_backups_20261024\` và `/workspace/TMver36_001_backup/`.
- Đã gắn lịch sử `.git` vào bản chuẩn OneDrive `C:\Users\LAPTOP\OneDrive\Desktop\TMver36_001-master`.

---

## 2. Các Nhóm Sửa Lỗi & Nâng Cấp Đã Thực Hiện (Phase 2 & Phase 3)

### Group A: Sửa `TeacherScheduleController`, FXML/Controller wiring và loại bỏ phụ thuộc `getScene()` sớm trên cả 14 màn hình
- Thêm lớp tiện ích `com.example.tmver36.FxNodes` duyệt đệ quy qua `Parent.getChildrenUnmodifiable()`, `ScrollPane.getContent()`, `TabPane.getTabs()`, `TitledPane.getContent()`, `SplitPane.getItems()`, `Labeled.getGraphic()`, `MenuButton.getItems()` ngay cả khi `getScene() == null`.
- Sửa `TeacherSchedule.fxml` & `TeacherScheduleController.java` (`fx:id="slotsTable"` trên `<VBox styleClass="table">`, `fx:id="meetLinkField"` trên `<TextField>`, gắn đầy đủ handler cho `nav_TeacherDocuments` / `CV & Documents` và tất cả nút điều hướng).
- Cập nhật đồng bộ toàn bộ 14 màn hình FXML & Controller.

### Group B: Bảo mật Vai trò/Session, Liên kết Tài khoản – Giáo viên, Hồ sơ/Tài liệu & Tự Động Tạo Link Phòng Họp Trực Tuyến
- **Xác thực & Phân quyền (`AppUser`, `LoginController`, `AdminAccountsController`):**
  - Băm mật khẩu bằng **PBKDF2-HMAC-SHA256** (65.536 vòng lặp + salt ngẫu nhiên 16 byte).
  - Kiểm tra quyền truy cập màn hình theo vai trò (`LoginController.canOpen`) và xóa sạch session khi đăng xuất (`Login.fxml`).
- **Liên kết Tài khoản – Giáo viên & Hồ sơ/Tài liệu:**
  - `PortalStore.teacherForUser(username)` / `teacherIdForUser(username)` tự động ánh xạ tài khoản Teacher sang hồ sơ `TeacherProfile` riêng.
  - `TeacherDocumentsController` sao chép file tải lên (`PDF`, `DOC`, `DOCX`, `JPG`, `JPEG`, `PNG`) vào `<dataDir>/documents/<teacherId>/`.
- **Tự động tạo Link Phòng Họp Video Trực Tuyến Dùng Chung (Auto-Generated Working Meeting Links):**
  - Cài đặt `PortalStore.generateMeetingLink(interview)` tạo phòng họp trực tuyến thực tế, duy nhất và cố định cho từng buổi phỏng vấn (`https://meet.jit.si/TeacherMatch-<id>-<teacher>-<partner>-<token>`).
  - Ngay khi **Partner** đặt lịch phỏng vấn (`requestInterview`), phòng họp trực tuyến được tự động khởi tạo ngay lập tức để cả **Partner**, **Teacher**, và **Matching Staff** khi bấm mở link đều vào **cùng một phòng gọi video thực tế trên trình duyệt** (không bị lỗi tạo phòng rời rạc như `meet.google.com/new` hay lỗi mã giả 404).
  - Trên `InterviewWorkspaceController`, `PartnerInterviewController`, và `TeacherScheduleController`: hỗ trợ tự động điền link phòng họp, tự động sao chép vào Clipboard khi bấm mở/xác nhận, mở trình duyệt đa nền tảng (`PortalStore.openInBrowser`), và vẫn cho phép Staff/Teacher dán link Google Meet/Zoom tùy chỉnh nếu muốn.

### Group C: Lưu trữ Bền vững (Local Persistence qua các lần đóng/mở ứng dụng)
- Lưu trữ nguyên tử (atomic write) toàn bộ trạng thái vào `<dataDir>/portal-store.properties` (mặc định `~/.tmver36_001/portal-store.properties`).

### Group D: Kiểm thử Tự động (JUnit 5 + JavaFX End-to-End Tests)
- `PortalStoreTest.java` (4 test cases) và `FxmlAndNavigationTest.java` (4 test cases) kiểm thử toàn diện: xác thực PBKDF2, quản lý tài khoản, liên kết giáo viên, hồ sơ/tài liệu, đặt lịch phỏng vấn, tự động sinh link phòng họp trực tuyến, lưu trữ bền vững, và điều hướng toàn bộ 14 màn hình cho cả 4 vai trò.

---

## 3. Đóng Gói Cho Người Khác Cài Đặt & Phát Hành GitHub Release (Phase 4 & Phase 5)

- Tạo script `scripts/package-windows.ps1` và `scripts/sign-windows.ps1`:
  1. Tự động đóng gói **Java Runtime (JDK 21)** kèm ứng dụng để máy người nhận **không cần cài đặt Java**.
  2. Tự động tạo chứng chỉ Code Signing (`TeacherMatch-CodeSigning.cer`) và ký số **Authenticode SHA-256** cho `TeacherMatch.exe`, `TeacherMatch-1.0.0.msi`, và `TeacherMatch-1.0.0.exe`.
  3. Xuất ra đầy đủ 3 định dạng phân phối trong thư mục `dist/` và tải lên **GitHub Release `v1.0.0`**:
     - `TeacherMatch-1.0.0-win64-portable.zip`: Bản Portable chỉ cần giải nén và chạy `TeacherMatch\TeacherMatch.exe` (không cần cài đặt).
     - `TeacherMatch-1.0.0.msi`: Bộ cài đặt chuẩn Windows Installer (tạo shortcut Desktop & Start Menu).
     - `TeacherMatch-1.0.0.exe`: Bộ cài đặt thực thi Windows EXE.
     - `Install-TeacherMatch.bat` & `TeacherMatch-CodeSigning.cer`: Tiện ích cài đặt nhanh 1-click kèm nạp chứng chỉ tin cậy.

---

## 4. Tổng Hợp Kết Quả Kiểm Thử Thực Tế

| Hạng mục kiểm thử | Môi trường | Kết quả |
| :--- | :--- | :--- |
| `PortalStoreTest` (4 tests, gồm test tự động tạo meeting link & persistence) | Linux Box & Windows 11 | **4/4 PASS** |
| `FxmlAndNavigationTest` (4 tests, gồm test 14 FXML, 4 roles & UI tạo meeting link) | Linux Box & Windows 11 | **4/4 PASS** |
| Đóng gói & ký số `jpackage` (`.msi`, `.exe`, `portable.zip`) | Windows 11 (JDK 21.0.12 + WiX 3.11.2) | **PASS** |
| Cài đặt / Khởi chạy / Lưu dữ liệu / Gỡ cài đặt thực tế | Windows 11 (`msiexec` & Portable) | **PASS** |
| Phát hành GitHub Release `v1.0.0` kèm bộ cài đặt | GitHub Releases | **PASS** |

---

## 5. Nâng Cấp Toàn Diện Theo 19 Mục Feedback (`feedback app.pdf`) & Đồng Bộ Đa Thiết Bị (2026-10-26 23:20 UTC+7)

### 5.1. Checklist 19 Mục Feedback Trong `feedback app.pdf`

| Mã mục | Trang PDF | Nội dung Feedback | Trạng thái | File / Component xử lý & Bằng chứng |
| :--- | :---: | :--- | :---: | :--- |
| **I.1** | Tr. 1 | Số liệu trên Matching Staff (`24`, `06`, `03`) đang hardcode, không khớp hồ sơ thực tế | **Đã sửa** | `StaffDashboard.fxml`, `StaffDashboardController.java` (`updateMetrics()` tính trực tiếp từ `STORE.teachers()` và `STORE.interviews()`) |
| **I.2** | Tr. 1 | Bộ lọc `Share with partner` thừa trên thanh filter Matching Staff | **Đã sửa** | Đã gỡ khỏi hàng filter trong `StaffDashboard.fxml`, giữ thao tác cấp quyền trực tiếp trên từng thẻ hồ sơ |
| **I.3 / II.8** | Tr. 1, 5 | Các tài liệu CV, chứng chỉ, video dùng link mẫu `example.com` hoặc gắn cứng tên file của Emily Martin | **Đã sửa** | `src/main/resources/com/example/tmver36/samples/` (12 file PDF/HTML thật riêng biệt cho từng giáo viên), `PortalStore.java`, `StaffReviewController.java`, `PartnerProfileController.java`, `TeacherDocumentsController.java` |
| **I.4** | Tr. 1 | Chức năng `Remember me` trên màn hình Login chưa hoạt động | **Đã sửa** | `Login.fxml` (`fx:id="rememberMeCheck"`), `LoginController.java` (lưu token băm SHA-256 + thời hạn 14 ngày vào `remember-session.properties`, không lưu mật khẩu thuần) |
| **I.5** | Tr. 1–2 | Tạo tài khoản/cập nhật dữ liệu ở thiết bị này nhưng thiết bị khác không đăng nhập/đồng bộ được | **Đã sửa** | `CloudSyncService.java` + `PortalStore.java` kết nối Supabase PostgreSQL (`public.tm_portal_state` & `public.tm_shared_files`), tự động đồng bộ hai chiều khi khởi động, đăng nhập, lưu dữ liệu hoặc bấm `Sync Cloud` |
| **I.6** | Tr. 2 | Mọi giáo viên đều dùng chung ảnh đại diện của Emily Martin (`EM.jpg`) | **Đã sửa** | Tạo 3 ảnh đại diện riêng (`emily_avatar.png`, `daniel_avatar.png`, `sarah_avatar.png`) + hỗ trợ tải ảnh đại diện mới (`PortalStore.updateTeacherAvatar()`) đồng bộ lên Cloud |
| **I.7** | Tr. 2 | File giáo viên tải lên không hiển thị đúng cho Staff/Partner xem và tải xuống | **Đã sửa** | `TeacherDocumentsController`, `StaffReviewController`, `PartnerProfileController`, `ProfileCardBuilder` đọc động từ `STORE.documents(teacherId)` và `CloudSyncService.downloadSharedFile()` |
| **II.1** | Tr. 2–3 | Giao diện bị tràn ngang (horizontal scrollbar) trên màn hình chuẩn | **Đã sửa** | Toàn bộ 13 màn hình FXML đặt `prefWidth="1280"`, `ScrollPane fitToWidth="true" hbarPolicy="NEVER"`, thanh filter tự xuống dòng (`FlowPane`) |
| **II.2** | Tr. 3 | Quốc tịch chỉ có 3 lựa chọn (`US`, `UK`, `Canada`) | **Đã sửa** | `CatalogData.WORLD_COUNTRIES_AZ` gồm 195+ quốc gia xếp theo A–Z, hỗ trợ gõ tìm kiếm trực tiếp trên `ComboBox` kèm lựa chọn `Other` |
| **II.3** | Tr. 3 | Kinh nghiệm (`Experience`) không đồng bộ giữa Staff và Partner | **Đã sửa** | Chuẩn hóa 4 mức trên toàn hệ thống (`CatalogData.EXPERIENCE_OPTIONS`: `Under 1 year`, `1–2 years`, `3–5 years`, `Over 5 years`) |
| **II.4** | Tr. 3–4 | Danh sách giáo viên dạng bảng chữ đơn điệu, cần thiết kế dạng thẻ hồ sơ chuyên nghiệp (tham khảo Shopee) | **Đã sửa** | `ProfileCardBuilder.java`, `StaffDashboard.fxml`, `PartnerDashboard.fxml`, `theme.css` hiển thị lưới thẻ `FlowPane` với avatar, badge `qEP`/`qCV`, quốc tịch, bằng cấp, chuyên ngành, chip chứng chỉ, lương kỳ vọng và nút thao tác nhanh |
| **II.5** | Tr. 4 | Chứng chỉ (`Certificate`) chỉ chọn được 1 giá trị và thiếu `IELTS` | **Đã sửa** | Hỗ trợ chọn nhiều chứng chỉ (`Multi-select MenuButton` / `CheckBox` gồm `CELTA`, `TEFL`, `TESOL`, `IELTS`, `TOEFL`, `DELTA`, `Cambridge TKT`, `PGCE`) |
| **II.6** | Tr. 4 | Thay bộ lọc `Location` bằng `Major / Degree` (`Bachelor`, `Master`...) | **Đã sửa** | Thêm `degree` và `major` vào `TeacherProfile`, `TeacherProfile.fxml`, và thay bộ lọc `Location` trên `StaffDashboard` & `PartnerDashboard` bằng `Major / Degree` |
| **II.7** | Tr. 5 | Staff bấm `Request update` báo lỗi không có quyền và không gửi được feedback cho giáo viên | **Đã sửa** | `StaffReviewController` gọi `STORE.saveReviewState(..., "UPDATE_REQUIRED", feedback, checklistFlags, ...)` và hiển thị banner phản hồi trên `TeacherProfile.fxml` |
| **II.9** | Tr. 5–6 | `Review checklist` bị hardcode trạng thái tick, thoát ra vào lại bị mất | **Đã sửa** | Lưu bền vững chuỗi `reviewChecklist` (`boolean[5]`) cho từng giáo viên trong `TeacherProfile` & `PortalStore` |
| **III.1** | Tr. 6 | Đổi 2 lựa chọn trạng thái đầu trong bộ lọc Status thành `qCV` và `qEP` | **Đã sửa** | `CatalogData.STAFF_STATUS_FILTER_OPTIONS` (`All`, `qCV`, `qEP`, `Update Required`) ánh xạ tương thích ngược với `PENDING` (`qCV`) và `READY` (`qEP`) |
| **III.2** | Tr. 6–7 | Thêm mức lương kỳ vọng (`Expected Salary`) vào hồ sơ giáo viên và hiển thị/lọc bên Partner & Staff | **Đã sửa** | Thêm trường `expectedSalary` vào `TeacherProfile`, form `TeacherProfile.fxml`, thẻ hồ sơ `ProfileCardBuilder`, và bộ lọc lương trên `StaffDashboard` & `PartnerDashboard` |
| **III.3** | Tr. 7 | Bên Partner không bấm mở hoặc sao chép được `Meeting link` trong bảng phỏng vấn | **Đã sửa** | `PartnerInterviewController.renderRequests()` hiển thị `Hyperlink` có thể bấm mở trình duyệt + nút `Join Meet` + nút `Copy Link` trên từng dòng và giữ nguyên nút `openPartnerMeetButton` |

### 5.2. Kết Quả Kiểm Thử Hồi Quy & Đa Thiết Bị Mới Nhất (11/11 Tests PASS)
- `FxmlAndNavigationTest` (**5/5 PASS**): Kiểm tra nạp 14 FXML không lỗi, kiểm tra `ScrollPane` không tràn ngang (`fitToWidth=true`, `hbarPolicy=NEVER`), kiểm tra `Remember me`, kiểm tra KPI động & bộ lọc `qCV`/`qEP` + lưới thẻ `FlowPane`, kiểm tra `StaffReview` hiển thị đúng tài liệu riêng của từng giáo viên + lưu `Review checklist` + gửi `Update Required`, kiểm tra `PartnerInterview` hiển thị `Hyperlink` phòng họp.
- `PortalStoreTest` (**6/6 PASS**): Kiểm tra xác thực PBKDF2, quản lý tài khoản, luồng đặt lịch & tự sinh phòng họp Jitsi, tách biệt avatar/tài liệu của 3 giáo viên, bộ lọc `CatalogData`, lưu trữ bền vững và **đồng bộ thực tế giữa 2 thiết bị (`deviceA` và `deviceB`) qua Supabase Cloud**.
