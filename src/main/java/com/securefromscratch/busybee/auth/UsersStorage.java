package com.securefromscratch.busybee.auth;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Service
public class UsersStorage {
    private static final Logger LOGGER = LoggerFactory.getLogger(UsersStorage.class);
    private final Map<String, UserAccount> m_users = new HashMap<>();

    public Optional<UserAccount> findByUsername(String username) {
        return Optional.ofNullable(m_users.get(username));
    }

    public UserAccount createUser(String username, String password , String[] roles) {
        // i have added an error so you wont be able to create the same user twice.
        if (m_users.containsKey(username)) { 
            // Avoid logging the username (PII). A generic reason is enough.
            LOGGER.warn("Register rejected: username already exists");
            throw new IllegalArgumentException("username: already exists");
        }
        UserAccount newAccount = new UserAccount(username, password, roles);
        m_users.put(username, newAccount);
        return newAccount;
    }
}
