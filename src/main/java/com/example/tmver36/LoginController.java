package com.example.tmver36;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import com.example.tmver36.service.PortalStore;
import com.example.tmver36.model.AppUser;

public class LoginController {

    private PortalStore store = new PortalStore();

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    public void handleLoginAction(ActionEvent event) {
        String email = emailField.getText();
        String pass = passwordField.getText();

        AppUser user = store.authenticate(email, pass);

        if (user != null) {
            String role = user.getRole();
            try {
                if ("ADMIN".equals(role)) {
                    chuyenTrang(event, "AdminLogs.fxml");
                } else if ("STAFF".equals(role)) {
                    chuyenTrang(event, "StaffDashboard.fxml");
                } else if ("PARTNER".equals(role)) {
                    chuyenTrang(event, "PartnerDashboard.fxml");
                } else if ("TEACHER".equals(role)) {
                    chuyenTrang(event, "TeacherProfile.fxml");
                } else {
                    if (errorLabel != null) {
                        errorLabel.setText("Vai trò không hợp lệ!");
                    }
                }
            } catch (IOException e) {
                System.out.println("Lỗi: Không tìm thấy file FXML!");
                e.printStackTrace();
            }
        } else {
            if (errorLabel != null) {
                errorLabel.setText("Sai tài khoản hoặc mật khẩu!");
            }
        }
    }

    private void chuyenTrang(ActionEvent event, String fxmlFile) throws IOException {
        Parent root = FXMLLoader.load(getClass().getResource(fxmlFile));
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        Scene scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }
}