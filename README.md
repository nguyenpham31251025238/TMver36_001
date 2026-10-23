# Kế hoạch Cập nhật & Vá lỗi TMver36

Dự án đã được xử lý xong phần responsive giao diện và chuyển sang kho chứa (repository) mới để dọn sạch lỗi xung đột lịch sử code. Dưới đây là phân công công việc tiếp theo và các lưu ý quan trọng khi chạy dự án.

## 📌 Phân công công việc

### 👨‍💻 Dev 1: Quản lý Admin & Staff Dashboard
* **Quản lý tài khoản Admin:** Hoàn thiện chức năng phân quyền, thêm/sửa/xóa tài khoản, đảm bảo dữ liệu cập nhật đúng vào bộ nhớ tạm.
* **Bảng điều khiển Staff (Staff Dashboard):** Xử lý logic thống kê, hiển thị danh sách công việc, duyệt hồ sơ và kết nối dữ liệu lên bảng (Table).

### 👨‍💻 Dev 2: Partner Screen & Teacher Documents
* **Màn hình Partner:** Hoàn thiện giao diện, chức năng đánh giá hồ sơ và hiển thị chi tiết thông tin giáo viên.
* **Tài liệu Giáo viên (Teacher Documents):** Sửa lỗi tính năng tải file, thiết lập logic lưu file an toàn, cập nhật trạng thái hồ sơ sau khi upload.

---

## ⚠️ Hướng dẫn Chạy dự án (Bắt buộc đọc để tránh lỗi)

Sau khi Clone dự án về máy, hãy thực hiện đúng các bước sau để code không bị báo lỗi đỏ:

1. **Luôn chạy qua file Launcher:** Khởi chạy ứng dụng bằng file `Launcher.java`. Tuyệt đối không chạy trực tiếp bằng `Main.java` để tránh các lỗi liên quan đến cấu hình Module của JavaFX.
2. **Đồng bộ JDK 21:** Dự án đang sử dụng JDK 21. Vào `File > Project Structure` và đảm bảo SDK đang được chọn là phiên bản 21.
3. **Reload Maven:** Nếu các thư viện JavaFX bị báo lỗi đỏ, hãy mở tab **Maven** ở lề phải màn hình IntelliJ và nhấn nút **Reload All Maven Projects** (biểu tượng 2 mũi tên xoay vòng) để phần mềm tự động kéo thư viện về.
4. **Tuyệt đối dùng đường dẫn tương đối (Relative Path):** Khi code xử lý đường dẫn file hoặc hình ảnh, phải dùng đường dẫn tương đối (ví dụ: `src/main/resources/...`). Tuyệt đối KHÔNG code cứng đường dẫn cá nhân (kiểu `C:\Users\Dell\...`) vì sang máy khác sẽ bị lỗi không tìm thấy file.
