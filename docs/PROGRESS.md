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
