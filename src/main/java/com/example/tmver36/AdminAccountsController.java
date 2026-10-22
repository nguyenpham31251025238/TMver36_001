package com.example.tmver36;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import com.example.tmver36.service.PortalStore;
import com.example.tmver36.model.AppUser;

import java.util.ArrayList;
import java.util.List;

public class AdminAccountsController {

    private PortalStore store = new PortalStore();

    // Khai báo bảng và 4 cột
    @FXML private TableView<AppUser> accountTable;
    @FXML private TableColumn<AppUser, String> colUsername;
    @FXML private TableColumn<AppUser, String> colEmail;
    @FXML private TableColumn<AppUser, String> colRole;
    @FXML private TableColumn<AppUser, String> colStatus;

    @FXML
    public void initialize() {
        colUsername.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        colEmail.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername() + "@example.com"));
        colRole.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRole()));
        // Tạm gán mặc định là Active, bạn AI của team sẽ chỉnh lại nếu Model có thêm trạng thái
        colStatus.setCellValueFactory(data -> new SimpleStringProperty("Active"));

        // Test UI
        List<AppUser> danhSach = new ArrayList<>();
        accountTable.getItems().addAll(danhSach);
    }
}