🚀 Kế hoạch Cập nhật & Sửa lỗi Dự án TeacherMatch (TMver36)

Danh sách này tổng hợp các bug chức năng hiện tại và định hướng nâng cấp hệ thống. Giao diện (UI/Responsive) đã được fix chuẩn, hiện tại team cần tập trung vào xử lý Data, Logic và kết nối UI với Backend.

Dưới đây là gợi ý chia việc cho 2 thành viên để làm việc song song hiệu quả:

👤 Dev 1: Phụ trách Giao diện dữ liệu, Chuyển hướng & Xử lý Chuỗi (Frontend Logic)

Tập trung vào việc vá các lỗi thao tác trên màn hình Staff và Partner.

1. Màn hình Partner (Hiring Partner)

Lỗi Navigation: Khi chuyển sang tab Candidate Details bị văng popup Navigation failed: Could not load PartnerProfile.fxml.

Hành động: Kiểm tra lại đường dẫn resource FXML trong PartnerDashboardController. Đảm bảo file PartnerProfile.fxml nằm đúng thư mục và gõ đúng chính tả (chú ý chữ hoa/thường).

Lỗi đặt lịch (Interview Request): Báo lỗi Invalid time slot khi gửi ngày giờ.

Hành động: Trong PartnerInterviewController, định dạng ngày giờ đang bị lỗi (dấu . vs -). Cần cập nhật hàm parse chuỗi thời gian cho đúng định dạng dd/MM/yyyy - HH:mm - HH:mm.

Lỗi Evaluate Interview: Tab Send Results & Feedback báo lỗi Select the interview to evaluate.

Hành động: Sửa lại logic lấy dữ liệu từ ComboBox. Hiện tại Controller chưa tách được ID của cuộc phỏng vấn từ chuỗi Text hiển thị.

2. Màn hình Staff (Matching Staff)

Dashboard Data: Các con số thống kê (Ready profiles, Pending...) đang là text cứng.

Hành động: Viết hàm trong StaffDashboardController để quét dữ liệu từ PortalStore, đếm số lượng hồ sơ theo từng trạng thái và gán vào các Label.

Thanh Search / Filter:

Hành động: Kích hoạt tính năng tìm kiếm. Bắt sự kiện gõ phím hoặc nhấn nút Search để lọc lại danh sách trong TableView.

Lưu trạng thái (Profile Review): Nút Update đổi trạng thái thành UPDATE_REQUIRED có lên thông báo nhưng tải lại trang bị mất.

Hành động: Tạm thời cần hàm làm mới (refresh) lại giao diện hoặc bảng sau khi bấm nút để hiển thị ngay trạng thái mới (chờ Dev 2 ghép Database để lưu vĩnh viễn).

👤 Dev 2: Phụ trách Lưu trữ, Database & Upload File (Backend & Data Logic)

Tập trung vào luồng dữ liệu lõi, xử lý file và chuẩn bị tích hợp Cơ sở dữ liệu thật.

1. Quản lý Tài khoản (Admin Workspace)

Lỗi mất tài khoản: Hiện tại dữ liệu đang lưu trong RAM (PortalStore), tắt app là mất.

Hành động: Thiết lập Database (SQLite hoặc MySQL). Viết lại class Store/Service để kết nối JDBC/Hibernate, đảm bảo tài khoản mới tạo được lưu vĩnh viễn.

Lỗi Assign Role: Chuyển role cho user xong thì bảng TableView không cập nhật.

Hành động: Sửa code trong AdminAccountsController. Sau khi gọi hàm đổi quyền, phải gọi lệnh table.refresh() hoặc truy xuất lại danh sách từ Store để bảng hiển thị đúng role mới.

2. Giáo viên Upload Tài liệu (Teacher Workspace)

Lỗi giới hạn định dạng ảnh: Cửa sổ chọn file (FileChooser) đang không cho chọn ảnh.

Hành động: Vào TeacherDocumentsController, thêm các định dạng ảnh vào bộ lọc. Ví dụ: new FileChooser.ExtensionFilter("Images/Docs", "*.pdf", "*.jpg", "*.png").

Logic Upload File thật:

Hành động: Nút "Upload" hiện tại chỉ lấy đường dẫn file. Cần code thêm logic: Khi user chọn file -> Copy file đó lưu vào một thư mục /uploads nội bộ trong Project -> Lưu đường dẫn tương đối (relative path) của file đó vào Database/Store để các Role khác (Staff, Partner) có thể mở lên xem được.

💡 Lưu ý chung khi Clone dự án về máy chạy:

Chạy ứng dụng qua file Launcher.java.

Cấu hình lại JDK tương thích (Khuyên dùng JDK 17 hoặc 21).

Nếu báo lỗi thiếu thư viện JavaFX, hãy bấm "Reload Maven Project" trong IntelliJ.
