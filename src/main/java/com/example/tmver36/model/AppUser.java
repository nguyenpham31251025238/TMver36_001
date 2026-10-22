package com.example.tmver36.model;

public class AppUser {
    private String username;
    private String password;
    private String role;
    private String fullName;
    public AppUser(String username, String password, String role, String fullName) {
        this.username=username; this.password=password; this.role=role; this.fullName=fullName;
    }
    public String getUsername(){return username;} public String getPassword(){return password;}
    public String getRole(){return role;} public String getFullName(){return fullName;}
}
