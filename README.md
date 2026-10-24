# TeacherMatch Desktop Application (TMver36_001)

Ứng dụng desktop quản lý tuyển dụng, kết nối và điều phối phỏng vấn giáo viên (**TeacherMatch - TMver36**) được xây dựng bằng **Java 21**, **JavaFX 21**, **FXML**, và **Maven**.

---

## 1. Tính Năng Chính & 4 Vai Trò (Roles)

Hệ thống hỗ trợ 4 vai trò người dùng với phân quyền màn hình riêng biệt, băm mật khẩu bảo mật (**PBKDF2-HMAC-SHA256**) và lưu trữ dữ liệu bền vững trên đĩa (`~/.tmver36_001/portal-store.properties` + `~/.tmver36_001/documents/`):

| Vai trò | Tài khoản Demo | Mật khẩu | Màn hình được phép truy cập |
| :--- | :--- | :--- | :--- |
| **System Admin** (`ADMIN`) | `admin` | `admin123` | `AdminAccounts.fxml`, `AdminLogs.fxml` |
| **Matching Staff** (`STAFF`) | `staff` | `staff123` | `StaffDashboard.fxml`, `StaffReview.fxml`, `InterviewWorkspace.fxml` |
| **Partner** (`PARTNER`) | `partner` | `partner123` | `PartnerDashboard.fxml`, `PartnerProfile.fxml`, `PartnerInterview.fxml` |
| **Teacher** (`TEACHER`) | `teacher` | `teacher123` | `TeacherProfile.fxml`, `TeacherDocuments.fxml`, `TeacherSchedule.fxml`, `TeacherHistory.fxml` |

### Các luồng nghiệp vụ chính:
1. **System Admin:**
   - Tạo mới, cập nhật họ tên/mật khẩu, đổi vai trò (in-place), khóa/mở khóa tài khoản, xóa tài khoản (bảo vệ tài khoản `admin` gốc).
   - Tự động khởi tạo và liên kết hồ sơ `TeacherProfile` khi tạo tài khoản có vai trò `Teacher`.
   - Tra cứu, lọc nhật ký hoạt động hệ thống (`AdminLogs.fxml`) và xuất CSV.
2. **Teacher:**
   - Cập nhật hồ sơ giảng dạy cá nhân (`TeacherProfile.fxml`) liên kết động với tài khoản đang đăng nhập.
   - Tải lên, thay thế, mở xem và xóa tài liệu (`CV`, `Certificate`, `ID / Passport`) với bộ lọc định dạng `PDF, DOC, DOCX, JPG, JPEG, PNG` (`TeacherDocuments.fxml`); file được sao chép bền vững vào `~/.tmver36_001/documents/<teacherId>/`.
   - Đăng ký/xóa khung giờ rảnh (`AvailabilitySlot`) trong tương lai (có kiểm tra chống trùng lịch) và cập nhật đường dẫn phòng họp trên `TeacherSchedule.fxml`.
   - Theo dõi lịch sử phỏng vấn và phản hồi tại `TeacherHistory.fxml`.
3. **Matching Staff:**
   - Xem tổng quan số liệu ứng viên, trạng thái xác minh và phỏng vấn (`StaffDashboard.fxml`).
   - Duyệt hồ sơ giáo viên (`READY` / `PENDING` / `NEEDS_INFO` / `REJECTED`) và cấp/thu hồi quyền truy cập từng giáo viên cho Partner (`StaffReview.fxml`).
   - Điều phối phỏng vấn, xác nhận lịch (`CONFIRMED`), gắn link Google Meet/Zoom và lưu ghi chú giai đoạn (`InterviewWorkspace.fxml`).
4. **Partner:**
   - Duyệt danh sách giáo viên đã được Staff xác minh (`READY`) và cấp quyền cho Partner (`PartnerDashboard.fxml`).
   - Xem chi tiết hồ sơ, mở tài liệu của giáo viên, và lưu đánh giá Shortlist (`Shortlist` / `Hold` / `Reject`) riêng của Partner mà không ghi đè trạng thái xác minh của Staff (`PartnerProfile.fxml`).
   - Chọn khung giờ rảnh thực tế của giáo viên để đặt lịch phỏng vấn, mở link phòng họp và gửi đánh giá kết quả sau phỏng vấn (`PROCEED` / `HOLD` / `REJECTED`) (`PartnerInterview.fxml`).

---

## 2. Yêu Cầu Hệ Thống

- **JDK:** Java 21 trở lên (khuyến nghị Eclipse Adoptium Temurin JDK 21)
- **Maven:** Apache Maven 3.9+
- **Hệ điều hành:** Windows 10/11 (hỗ trợ đóng gói bộ cài `.msi` / `.exe`), macOS, hoặc Linux.

---

## 3. Hướng Dẫn Build, Chạy Ứng Dụng & Kiểm Thử

### 3.1. Chạy bộ kiểm thử tự động (JUnit 5 + JavaFX End-to-End Tests)
```bash
mvn clean test
```
*(Trên máy chủ Linux không có màn hình vật lý, sử dụng `xvfb-run -a mvn clean test`)*

### 3.2. Chạy ứng dụng trực tiếp từ mã nguồn
```bash
mvn javafx:run
```

### 3.3. Đóng gói JAR và thư viện phụ thuộc
```bash
mvn clean package
```
Sau khi lệnh chạy xong:
- File JAR chính nằm tại `target/TMver36-1.0-SNAPSHOT.jar`
- Toàn bộ thư viện runtime JavaFX nằm tại `target/jpackage-input/`

---

## 4. Hướng Dẫn Tạo Bộ Cài Đặt Windows (`.msi` và `.exe`)

Dự án cung cấp sẵn script tự động `scripts/package-windows.ps1` sử dụng `jpackage` (từ JDK 21) và tự động tải bản portable của **WiX Toolset v3.11.2** (nếu máy chưa cài WiX) để đóng gói cả `.msi` và `.exe` kèm theo Java Runtime (không yêu cầu máy người dùng cuối phải cài đặt sẵn Java):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```

Các tùy chọn tham số của script:
- `-InstallerType msi|exe|both` (mặc định: `both`)
- `-AppVersion 1.0.0` (mặc định: `1.0.0`)
- `-SkipTests` (bỏ qua bước chạy lại unit test khi đóng gói nhanh)

Sau khi hoàn tất, bộ cài đặt được xuất ra tại thư mục `dist/`:
- `dist/TeacherMatch-1.0.0.msi`
- `dist/TeacherMatch-1.0.0.exe`

Bộ cài đặt hỗ trợ cài đặt theo từng người dùng (`--win-per-user-install`, không cần quyền Administrator), tạo biểu tượng ngoài **Desktop**, nhóm **Start Menu (`TeacherMatch`)**, cho phép chọn thư mục cài đặt và gỡ cài đặt sạch sẽ qua Windows Settings / Control Panel hoặc `msiexec /x`.

---

## 5. Lưu Trữ Dữ Liệu Bền Vững (Persistence)

- Mặc định, toàn bộ dữ liệu ứng dụng được lưu tự động sau mỗi thao tác vào:
  - File trạng thái: `%USERPROFILE%\.tmver36_001\portal-store.properties` (trên Windows) hoặc `~/.tmver36_001/portal-store.properties` (trên Linux/macOS).
  - Thư mục tài liệu tải lên: `%USERPROFILE%\.tmver36_001\documents\<teacherId>\`
- Có thể tùy chỉnh thư mục dữ liệu bằng JVM property `-Dtm.data.dir=/path/to/custom-dir`.
- Khi gỡ cài đặt ứng dụng (`.msi`/`.exe`), thư mục dữ liệu người dùng `~/.tmver36_001` được giữ nguyên để tránh mất dữ liệu khi nâng cấp phiên bản.

---

## 6. Tài Liệu Chi Tiết

Xem báo cáo kiểm tra, danh sách lỗi đã sửa và kết quả kiểm thử chi tiết tại [`docs/PROGRESS.md`](docs/PROGRESS.md).
