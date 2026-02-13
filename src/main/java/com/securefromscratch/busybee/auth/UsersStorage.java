package com.securefromscratch.busybee.auth;

import org.springframework.stereotype.Service;

import javax.swing.text.html.Option;
import java.util.*;

@Service
public class UsersStorage {
    private final Map<String, UserAccount> m_users = new HashMap<>();

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(m_users.get(username));
    }

    public UserAccount createUser(String username, String password , String[] roles) {
        // i have added an error so you wont be able to create the same user twice.
        if (m_users.containsKey(username)) { 
            throw new IllegalArgumentException("User already exists");
        }
        UserAccount newAccount = new UserAccount(username, password, roles);
        m_users.put(username, newAccount);
        return newAccount;
    }
}
