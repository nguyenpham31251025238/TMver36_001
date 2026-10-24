# TeacherMatch Desktop Application (TMver36_001)

Ứng dụng desktop quản lý tuyển dụng, kết nối và điều phối phỏng vấn giáo viên (**TeacherMatch - TMver36**) được xây dựng bằng **Java 21**, **JavaFX 21**, **FXML**, và **Maven**.

---

## 1. Tải Xuống & Cài Đặt Cho Người Dùng Cuối (Windows 10/11)

Bạn có thể gửi trực tiếp đường dẫn **GitHub Releases (`v1.0.0`)** hoặc các file trong thư mục `dist/` cho bất kỳ ai dùng Windows 10/11 (người nhận **không cần cài đặt Java hay Maven**, vì bộ cài đã đóng gói sẵn Java Runtime độc lập):

- **Link tải trực tiếp (GitHub Release v1.0.0):**
  `https://github.com/nguyenpham31251025238/TMver36_001/releases/tag/v1.0.0`

### 3 lựa chọn cài đặt / chạy ngay cho người nhận:
1. **Bản Portable chạy ngay không cần cài đặt (`TeacherMatch-1.0.0-win64-portable.zip`) — Khuyên dùng khi gửi nhanh:**
   - Giải nén file `.zip` ra bất kỳ thư mục nào.
   - Mở thư mục `TeacherMatch` và nhấp đúp vào **`TeacherMatch.exe`** (hoặc `Run-TeacherMatch.bat`) để chạy ngay lập tức mà không cần quyền Administrator.
2. **Bộ cài đặt chuẩn Windows Installer (`TeacherMatch-1.0.0.msi`):**
   - Nhấp đúp vào `TeacherMatch-1.0.0.msi` (hoặc chạy `Install-TeacherMatch.bat`) để cài đặt vào `%LOCALAPPDATA%\TeacherMatch` và tự động tạo shortcut **TeacherMatch** ngoài **Desktop** và **Start Menu**.
3. **Bộ cài đặt thực thi (`TeacherMatch-1.0.0.exe`):**
   - Chạy trực tiếp trình cài đặt `.exe` trên Windows 10/11.

*(Ghi chú: Toàn bộ các file `.exe` và `.msi` đều đã được ký số Authenticode SHA-256 kèm chứng chỉ `TeacherMatch-CodeSigning.cer`)*

---

## 2. Tính Năng Chính & 4 Vai Trò (Roles)

Hệ thống hỗ trợ 4 vai trò người dùng với phân quyền màn hình riêng biệt, băm mật khẩu bảo mật (**PBKDF2-HMAC-SHA256**), **tự động sinh phòng họp video trực tuyến (Live Meeting Room)** cho từng buổi phỏng vấn, và lưu trữ dữ liệu bền vững trên đĩa (`~/.tmver36_001/portal-store.properties` + `~/.tmver36_001/documents/`):

| Vai trò | Tài khoản Demo | Mật khẩu | Màn hình được phép truy cập |
| :--- | :--- | :--- | :--- |
| **System Admin** (`ADMIN`) | `admin` | `admin123` | `AdminAccounts.fxml`, `AdminLogs.fxml` |
| **Matching Staff** (`STAFF`) | `staff` | `staff123` | `StaffDashboard.fxml`, `StaffReview.fxml`, `InterviewWorkspace.fxml` |
| **Partner** (`PARTNER`) | `partner` | `partner123` | `PartnerDashboard.fxml`, `PartnerProfile.fxml`, `PartnerInterview.fxml` |
| **Teacher** (`TEACHER`) | `teacher` | `teacher123` | `TeacherProfile.fxml`, `TeacherDocuments.fxml`, `TeacherSchedule.fxml`, `TeacherHistory.fxml` |

### Các luồng nghiệp vụ chính & Phòng họp trực tuyến tự động (Auto-Generated Meeting Links):
1. **Tự động tạo phòng họp trực tuyến dùng chung (`https://meet.jit.si/TeacherMatch-<id>-<teacher>-<partner>-<token>`):**
   - Ngay khi **Partner** đặt lịch phỏng vấn từ khung giờ rảnh của giáo viên, hệ thống tự động tạo một đường dẫn phòng họp video trực tuyến duy nhất cho buổi phỏng vấn đó (`PortalStore.generateMeetingLink`).
   - Khi **Partner**, **Teacher**, hoặc **Matching Staff** bấm **Open Meeting / Join**, cả 3 bên đều vào **đúng cùng một phòng họp video hoạt động thực tế ngay trên trình duyệt** (không cần tài khoản hay API key), đồng thời đường dẫn được tự động sao chép vào Clipboard.
   - Ngoài phòng họp tự động, **Matching Staff** hoặc **Teacher** vẫn có thể dán đường dẫn Google Meet / Zoom / Microsoft Teams tùy chỉnh nếu muốn.
2. **System Admin:**
   - Tạo mới, cập nhật họ tên/mật khẩu, đổi vai trò (in-place), khóa/mở khóa tài khoản, xóa tài khoản (bảo vệ tài khoản `admin` gốc).
   - Tự động khởi tạo và liên kết hồ sơ `TeacherProfile` khi tạo tài khoản có vai trò `Teacher`.
   - Tra cứu, lọc nhật ký hoạt động hệ thống (`AdminLogs.fxml`) và xuất CSV.
3. **Teacher:**
   - Cập nhật hồ sơ giảng dạy cá nhân (`TeacherProfile.fxml`) liên kết động với tài khoản đang đăng nhập.
   - Tải lên, thay thế, mở xem và xóa tài liệu (`CV`, `Certificate`, `ID / Passport`) với bộ lọc định dạng `PDF, DOC, DOCX, JPG, JPEG, PNG` (`TeacherDocuments.fxml`).
   - Đăng ký/xóa khung giờ rảnh (`AvailabilitySlot`) trong tương lai (có kiểm tra chống trùng lịch) và mở phòng họp phỏng vấn trực tuyến trên `TeacherSchedule.fxml`.
   - Theo dõi lịch sử phỏng vấn và phản hồi tại `TeacherHistory.fxml`.
4. **Matching Staff:**
   - Xem tổng quan số liệu ứng viên, trạng thái xác minh và phỏng vấn (`StaffDashboard.fxml`).
   - Duyệt hồ sơ giáo viên (`READY` / `PENDING` / `UPDATE_REQUIRED`) và cấp/thu hồi quyền truy cập từng giáo viên cho Partner (`StaffReview.fxml`).
   - Điều phối phỏng vấn, tự động tạo/xác nhận link phòng họp trực tuyến (`CONFIRMED`), và lưu ghi chú giai đoạn (`InterviewWorkspace.fxml`).
5. **Partner:**
   - Duyệt danh sách giáo viên đã được Staff xác minh (`READY`) và cấp quyền cho Partner (`PartnerDashboard.fxml`).
   - Xem chi tiết hồ sơ, mở tài liệu của giáo viên, và lưu đánh giá Shortlist (`Shortlist` / `Hold` / `Reject`) riêng của Partner mà không ghi đè trạng thái xác minh của Staff (`PartnerProfile.fxml`).
   - Chọn khung giờ rảnh thực tế của giáo viên để đặt lịch phỏng vấn (tự động nhận link phòng họp trực tuyến), mở phòng họp và gửi đánh giá kết quả sau phỏng vấn (`PROCEED` / `NOT_PROCEEDING` / `ADDITIONAL_INTERVIEW`) (`PartnerInterview.fxml`).

---

## 3. Hướng Dẫn Build, Chạy Ứng Dụng & Kiểm Thử Từ Mã Nguồn

### 3.1. Chạy bộ kiểm thử tự động (JUnit 5 + JavaFX End-to-End Tests)
```bash
mvn clean test
```
*(Trên máy chủ Linux không có màn hình vật lý, sử dụng `xvfb-run -a mvn clean test`)*

### 3.2. Chạy ứng dụng trực tiếp từ mã nguồn
```bash
mvn javafx:run
```

### 3.3. Tạo lại bộ cài đặt Windows (`.msi`, `.exe`, và `portable.zip`) + Ký số Authenticode
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\package-windows.ps1
```
Các file xuất ra tại thư mục `dist/`:
- `dist/TeacherMatch-1.0.0.msi` (Bộ cài đặt Windows MSI đã ký số)
- `dist/TeacherMatch-1.0.0.exe` (Bộ cài đặt Windows EXE đã ký số)
- `dist/TeacherMatch-1.0.0-win64-portable.zip` (Bản Portable giải nén chạy ngay)
- `dist/TeacherMatch-CodeSigning.cer` & `dist/Install-TeacherMatch.bat`

---

## 4. Lưu Trữ Dữ Liệu Bền Vững (Persistence)

- Mặc định, toàn bộ dữ liệu ứng dụng được lưu tự động sau mỗi thao tác vào:
  - File trạng thái: `%USERPROFILE%\.tmver36_001\portal-store.properties` (trên Windows) hoặc `~/.tmver36_001/portal-store.properties` (trên Linux/macOS).
  - Thư mục tài liệu tải lên: `%USERPROFILE%\.tmver36_001\documents\<teacherId>\`
- Có thể tùy chỉnh thư mục dữ liệu bằng JVM property `-Dtm.data.dir=/path/to/custom-dir`.

---

## 5. Tài Liệu Chi Tiết

Xem báo cáo kiểm tra, danh sách lỗi đã sửa và kết quả kiểm thử chi tiết tại [`docs/PROGRESS.md`](docs/PROGRESS.md).
