package com.example.chat.service;

import com.example.chat.domain.User;
import com.example.chat.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1,50}");

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /** Returns the user, creating it if missing. Safe under concurrent calls for the same username. */
    @Transactional
    public User getOrCreate(String username) {
        validate(username);
        users.insertIfAbsent(username);
        return users.findByUsername(username).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Optional<User> find(String username) {
        validate(username);
        return users.findByUsername(username);
    }

    private static void validate(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Username must be 1-50 characters: letters, digits, '_', '.', '-'");
        }
    }
}
