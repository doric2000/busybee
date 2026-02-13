package com.securefromscratch.busybee.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class UsersPrePopulate {
    private static final SecureRandom s_random = new SecureRandom();

    @Bean
    CommandLineRunner populateInitialUsers(UsersStorage usersStorage, PasswordEncoder passwordEncoder) {
        return args -> {
            populateUser("Yariv", new String[]{"CREATOR"}, usersStorage, passwordEncoder);
            populateUser("Or", new String[]{"TRIAL"}, usersStorage, passwordEncoder);
            populateUser("Dor", new String[]{"ADMIN"}, usersStorage, passwordEncoder);
            // ניתן להוסיף כאן עוד משתמשים בקלות:
            // populateUser("Dor", new String[]{"ROLE_ADMIN"}, usersStorage, passwordEncoder);
        };
    }

    private void populateUser(String username, String[] roles, UsersStorage usersStorage, PasswordEncoder passwordEncoder) {
        String plainPassword = generatePwd();
        String encodedPassword = passwordEncoder.encode(plainPassword);
        UserAccount newAccount = usersStorage.createUser(username, encodedPassword , roles);
        newAccount.setEnabled(true);
        newAccount.setUsername(username);
        newAccount.setHashedPassword(encodedPassword);
        newAccount.setEnabled(true);
        System.out.println("User created: " + newAccount.getUsername());
        System.out.println("Password: " + plainPassword);
        System.out.println("Hashed Password: " + newAccount.getHashedPassword());
    }

    private String generatePwd() {
        // סיסמה אקראית באורך 8 תווים
        int length = 8;
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(s_random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
