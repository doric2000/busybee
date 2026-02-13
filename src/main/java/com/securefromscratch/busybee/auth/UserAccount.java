package com.securefromscratch.busybee.auth;

public class UserAccount {
    private String username;
    private String hashedPassword;
    private String[] roles;
    private boolean enabled = true;

    public UserAccount(String username, String hashedPassword, String[] roles) {
        this.username = username;
        this.hashedPassword = hashedPassword;
        this.roles = roles;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }


    public String[] getRoles() {
        return roles;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
