# Báo Cáo Tiến Độ Hoàn Thiện Dự Án TMver36_001

**Cập nhật lần cuối:** 2026-10-25 01:00 (UTC+7)
**Repository:** `https://github.com/nguyenpham31251025238/TMver36_001.git`
**Bản chuẩn làm việc (Canonical):** `C:\Users\LAPTOP\OneDrive\Desktop\TMver36_001-master` (gắn lịch sử `.git` trên nhánh `master`)

---

## 1. Tổng Quan Kiểm Tra & Sao Lưu (Phase 1)

### 1.1. Kiểm tra các bản sao trên máy Windows & GitHub
- Đã kiểm tra toàn bộ các thư mục liên quan trên máy Windows:
  1. `C:\Users\LAPTOP\OneDrive\Desktop\TMver36_001-master` (Bản chuẩn OneDrive)
  2. `C:\Users\LAPTOP\IdeaProjects\TMver36_001` (Bản có `.git` ở commit `330dc8a`)
  3. `C:\Users\LAPTOP\Downloads\TMver36_001-master\TMver36_001-master`
  4. `C:\Users\LAPTOP\.codex\worktrees\2835\TMver36_001` và `26bf\TMver36_001`
- Đã tạo bản sao lưu đầy đủ trước khi chỉnh sửa tại:
  - Trên máy Windows: `C:\Users\LAPTOP\TMver36_001_backups_20261024\`
  - Trên máy Box kiểm thử: `/workspace/TMver36_001_backup/`
- Đã gắn thư mục `.git` từ `IdeaProjects\TMver36_001` sang bản chuẩn OneDrive `C:\Users\LAPTOP\OneDrive\Desktop\TMver36_001-master` để bảo toàn toàn bộ lịch sử commit.

### 1.2. Các vấn đề phát hiện qua Audit ban đầu
1. **Lỗi `TeacherScheduleController.initialize()` và `getScene()` gọi quá sớm:**
   - Khi `FXMLLoader.load()` chạy `initialize()`, root node chưa được gắn vào `Scene` (`getScene() == null`) và các `ScrollPane`/`TabPane` chưa khởi tạo `Skin`, dẫn tới `lookup()`/`lookupAll()` trả về rỗng hoặc thiếu node bên trong `ScrollPane.getContent()`.
   - Trong `TeacherSchedule.fxml`: `fx:id="slotsTable"` bị đặt nhầm vào `<HBox styleClass="table-head">` thay vì `<VBox styleClass="table">`, và `fx:id="meetLinkField"` bị đặt nhầm vào `<VBox>` thay vì `<TextField>`.
   - Hậu quả: Nút `CV & Documents` (`nav_TeacherDocuments`) và các nút thao tác trên `TeacherSchedule` hoặc nhiều màn hình khác không gắn được event handler nếu phụ thuộc vào `getScene()`.
2. **Bảo mật, phân quyền & Session:**
   - Mật khẩu trong `AppUser` trước đó lưu plaintext; khi logout chưa xóa sạch session; `LoginController.navigateTo` chưa chặn điều hướng chéo trái phép giữa các vai trò (`ADMIN`, `STAFF`, `PARTNER`, `TEACHER`).
   - `AdminAccounts.fxml` thiếu import `PasswordField` và `AdminAccountsController` thiếu `@FXML handleLogout`.
3. **Liên kết Tài khoản – Giáo viên, Hồ sơ & Tài liệu:**
   - Các màn hình Teacher (`TeacherProfileController`, `TeacherDocumentsController`, `TeacherScheduleController`, `TeacherHistoryController`) trước đó hardcode `TEACHER_ID = 1L` thay vì liên kết động theo tài khoản đang đăng nhập.
   - Tài liệu tải lên chỉ lưu tên file trên UI, chưa sao chép vào thư mục dữ liệu bền vững và chưa đồng bộ sang màn hình Staff/Partner.
4. **Lịch rảnh (Availability), Phỏng vấn, Meeting Link & Đánh giá của Partner:**
   - `PartnerProfileController` khi lưu Shortlist có nguy cơ ghi đè trạng thái xác minh (`READY`/`PENDING`) của Staff.
   - Lịch rảnh của giáo viên (`AvailabilitySlot`), đặt lịch phỏng vấn của Partner (`PartnerInterviewController`), xác nhận link họp của Staff (`InterviewWorkspaceController`) cần được quản lý thống nhất trong `PortalStore`.
5. **Lưu trữ bền vững (Persistence):**
   - `PortalStore` ban đầu chỉ lưu dữ liệu trong RAM; khi đóng ứng dụng mở lại thì mọi thay đổi bị mất.
6. **Kiểm thử tự động (Automated Tests) & Đóng gói Windows:**
   - `pom.xml` chưa cấu hình `maven-surefire-plugin` để chạy JUnit 5 + JavaFX tests tự động và chưa có quy trình tạo bộ cài đặt Windows `.msi`/`.exe`.

---

## 2. Các Nhóm Sửa Lỗi Đã Thực Hiện (Phase 2 & Phase 3)

### Group A: Sửa `TeacherScheduleController`, FXML/Controller wiring và loại bỏ phụ thuộc `getScene()` sớm trên cả 14 màn hình
- Thêm lớp tiện ích `com.example.tmver36.FxNodes`:
  - `rootOf(Node)` đi ngược chuỗi `getParent()` ngay cả khi `getScene() == null`.
  - `allNodes`, `findAll`, `findAllByStyleClass`, `findFirstByStyleClass` duyệt đệ quy qua `Parent.getChildrenUnmodifiable()`, `ScrollPane.getContent()`, `TabPane.getTabs()`, `TitledPane.getContent()`, `SplitPane.getItems()`, `Labeled.getGraphic()`, `MenuButton.getItems()`.
- Sửa `TeacherSchedule.fxml` & `TeacherScheduleController.java`:
  - Đặt đúng `fx:id="slotsTable"` vào `<VBox styleClass="table">` và `fx:id="meetLinkField"` vào `<TextField>`.
  - Gắn đầy đủ `@FXML` và handler cho toàn bộ 5 nút điều hướng (`nav_TeacherProfile`, `nav_TeacherDocuments`, `nav_TeacherSchedule`, `nav_TeacherHistory`, `nav_Login`), nút `+ Add Slot`, nút xóa slot, cập nhật link họp và xem chi tiết phỏng vấn.
- Cập nhật đồng bộ toàn bộ 13 Controller & FXML còn lại (`LoginController`, `AdminAccountsController`, `AdminLogsController`, `StaffDashboardController`, `StaffReviewController`, `InterviewWorkspaceController`, `PartnerDashboardController`, `PartnerProfileController`, `PartnerInterviewController`, `TeacherProfileController`, `TeacherDocumentsController`, `TeacherHistoryController`).

### Group B: Bảo mật Vai trò/Session, Liên kết Tài khoản – Giáo viên, Hồ sơ/Tài liệu và Luồng Nghiệp vụ
- **Xác thực & Phân quyền (`AppUser`, `LoginController`, `AdminAccountsController`):**
  - Băm mật khẩu bằng **PBKDF2-HMAC-SHA256** (65.536 vòng lặp + salt ngẫu nhiên 16 byte), hỗ trợ tương thích ngược và tự động nâng cấp cho các tài khoản demo (`admin/admin123`, `staff/staff123`, `partner/partner123`, `teacher/teacher123`).
  - Kiểm tra quyền truy cập màn hình theo vai trò (`LoginController.canOpen`) và xóa sạch session khi đăng xuất (`Login.fxml`).
  - Quản lý tài khoản Admin: thêm/cập nhật tài khoản kèm họ tên, mật khẩu, đổi vai trò giữ nguyên thông tin, bật/tắt trạng thái hoạt động, bảo vệ tài khoản `admin` gốc không bị xóa/hạ quyền.
- **Liên kết Tài khoản – Giáo viên & Hồ sơ/Tài liệu:**
  - `PortalStore.teacherForUser(username)` / `teacherIdForUser(username)` tự động ánh xạ tài khoản Teacher sang hồ sơ `TeacherProfile` riêng (tự tạo hồ sơ mới khi Admin tạo tài khoản Teacher mới).
  - `TeacherProfileController` tải và lưu đầy đủ các trường: họ tên, môn dạy, khối lớp, múi giờ, địa điểm, số điện thoại, quốc tịch, kinh nghiệm, ngày bắt đầu, giới thiệu bản thân (bio).
  - `TeacherDocumentsController` + `TeacherDocument`: hỗ trợ chọn file (`PDF`, `DOC`, `DOCX`, `JPG`, `JPEG`, `PNG`), sao chép file vào `<dataDir>/documents/<teacherId>/`, cho phép thêm/thay thế/xem/xóa tài liệu và đồng bộ số lượng sang màn hình Staff/Partner.
- **Lịch rảnh, Phỏng vấn, Meeting Link & Đánh giá:**
  - `AvailabilitySlot`: Giáo viên thêm/xóa khung giờ rảnh trong tương lai (kiểm tra trùng lịch) trên `TeacherScheduleController`.
  - `PartnerProfileController`: Partner lưu quyết định Shortlist (`Shortlist` / `Hold` / `Reject`) và nhận xét riêng cho từng Partner (`savePartnerEvaluation`) mà **không làm hỏng** trạng thái xác minh `READY`/`PENDING` của Staff.
  - `PartnerInterviewController`: Partner xem danh sách slot rảnh thực tế của giáo viên được cấp quyền, đặt lịch phỏng vấn (tiêu thụ slot và tạo `Interview`), mở link họp và gửi đánh giá sau phỏng vấn (`PROCEED` / `HOLD` / `REJECTED`).
  - `StaffDashboardController`, `StaffReviewController`, `InterviewWorkspaceController`: Staff duyệt/từ chối hồ sơ, cấp/thu hồi quyền xem hồ sơ cho Partner, xác nhận lịch phỏng vấn và gắn Google Meet/Zoom URL.

### Group C: Lưu trữ Bền vững (Local Persistence qua các lần đóng/mở ứng dụng)
- Trong `PortalStore`, cài đặt cơ chế lưu trữ tự động vào `<dataDir>/portal-store.properties` (mặc định tại `~/.tmver36_001/portal-store.properties`, hoặc cấu hình qua `-Dtm.data.dir=...`):
  - Lưu nguyên tử (atomic write qua file tạm `.tmp` rồi `Files.move`) sau mỗi thao tác thay đổi dữ liệu.
  - Tự động nạp lại toàn bộ trạng thái khi khởi tạo `PortalStore`: danh sách tài khoản (`AppUser` + hash mật khẩu), liên kết tài khoản–giáo viên, hồ sơ giáo viên (`TeacherProfile`), tài liệu (`TeacherDocument`), khung giờ rảnh (`AvailabilitySlot`), quyền truy cập Partner (`partnerAccess`), đánh giá Shortlist của Partner (`partnerEvaluations`), danh sách phỏng vấn (`Interview`), và nhật ký hệ thống (`ActivityLog`).

### Group D: Kiểm thử Tự động (JUnit 5 + JavaFX End-to-End Tests)
- Cập nhật `pom.xml` cấu hình `maven-surefire-plugin` (`3.2.5`), `maven-jar-plugin` (`3.4.2`), và `maven-dependency-plugin` (`3.8.1`).
- Xây dựng 2 bộ kiểm thử tự động trong `src/test/java/com/example/tmver36/`:
  1. `PortalStoreTest.java` (4 test cases)
  2. `FxmlAndNavigationTest.java` (4 test cases)

---

## 3. Đóng Gói & Kiểm Thử Bộ Cài Windows (Phase 4)

- Tạo script đóng gói tự động `scripts/package-windows.ps1` kết hợp `Maven` + `jpackage` (JDK 21) + portable **WiX Toolset v3.11.2**.
- Chạy đóng gói thực tế trên máy Windows 11 (`C:\Users\LAPTOP\OneDrive\Desktop\TMver36_001-master`), tạo thành công cả 2 bộ cài đặt trong thư mục `dist/`:
  - `dist\TeacherMatch-1.0.0.msi` (`40,168,286 bytes` ~ 38.3 MB)
  - `dist\TeacherMatch-1.0.0.exe` (`40,739,840 bytes` ~ 38.8 MB)
- **Kết quả kiểm thử cài đặt, khởi chạy, lưu trữ và gỡ cài đặt thực tế trên Windows 11:**
  1. **Cài đặt (`msiexec /i dist\TeacherMatch-1.0.0.msi /qn`):** Thành công (`ExitCode = 0`), cài đặt vào `%LOCALAPPDATA%\TeacherMatch\TeacherMatch.exe` kèm runtime Java tích hợp.
  2. **Shortcut:** Tạo thành công shortcut trên Desktop (`C:\Users\LAPTOP\OneDrive\Desktop\TeacherMatch.lnk`) và Start Menu (`%APPDATA%\Microsoft\Windows\Start Menu\Programs\TeacherMatch\TeacherMatch.lnk`).
  3. **Khởi chạy (`TeacherMatch.exe`) & Persistence:** Tiến trình khởi chạy ổn định (`HasExited = False`), nạp giao diện `Login.fxml` và tự động khởi tạo file dữ liệu bền vững `C:\Users\LAPTOP\.tmver36_001\portal-store.properties` (`5,247 bytes`).
  4. **Gỡ cài đặt (`msiexec /x dist\TeacherMatch-1.0.0.msi /qn`):** Thành công (`ExitCode = 0`), xóa sạch thư mục cài đặt và shortcut trên Desktop/Start Menu, đồng thời giữ nguyên dữ liệu người dùng tại `C:\Users\LAPTOP\.tmver36_001\portal-store.properties`.

---

## 4. Tổng Hợp Kết Quả Kiểm Thử & Đồng Bộ GitHub (Phase 5)

| Hạng mục kiểm thử | Môi trường | Kết quả |
| :--- | :--- | :--- |
| `PortalStoreTest` (4 tests) | Linux Box & Windows 11 | **4/4 PASS** |
| `FxmlAndNavigationTest` (4 tests) | Linux Box & Windows 11 | **4/4 PASS** |
| Đóng gói `jpackage` (`.msi` & `.exe`) | Windows 11 (JDK 21.0.12 + WiX 3.11.2) | **PASS** |
| Cài đặt / Khởi chạy / Lưu dữ liệu / Gỡ cài đặt | Windows 11 (`msiexec`) | **PASS** |
| Clone mới từ GitHub & chạy `mvn test` | Fresh Clone Verification | **8/8 PASS** |
