package com.example.chat.repository;

import com.example.chat.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    /** Creates the user if missing; safe under concurrent calls for the same username. */
    @Modifying
    @Query(value = """
            INSERT INTO users (username, created_at) VALUES (:username, now())
            ON CONFLICT (username) DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(String username);
}
