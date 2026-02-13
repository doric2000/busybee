package com.securefromscratch.busybee.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UsernamePasswordDetailsService implements UserDetailsService {
    private final UsersStorage m_usersStorage;

    public UsernamePasswordDetailsService(UsersStorage usersStorage) {
        this.m_usersStorage = usersStorage;
    }

@Override
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    UserAccount user = m_usersStorage.findByUsername(username)
        .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    return org.springframework.security.core.userdetails.User
        .withUsername(user.getUsername())
        .password(user.getHashedPassword())
        .roles(user.getRoles())
        .disabled(!user.isEnabled())
        .build();
}

    public void createUser(String username, String password,String[] roles) {
        m_usersStorage.createUser(username, password,roles);
    }
}
