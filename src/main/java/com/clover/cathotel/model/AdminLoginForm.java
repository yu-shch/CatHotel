package com.clover.cathotel.model;

import jakarta.validation.constraints.NotBlank;

public class AdminLoginForm {

    @NotBlank(message = "ユーザー名を入力してください。")
    private String username;

    @NotBlank(message = "パスワードを入力してください。")
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
