package ru.tuganov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.EmailVerificationCode;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, UUID> {

    @Query("""
        SELECT e FROM EmailVerificationCode e
        WHERE e.user = :user AND e.used = false AND e.expiresAt > :now AND e.attempts < 5
        ORDER BY e.createdAt DESC LIMIT 1
        """)
    Optional<EmailVerificationCode> findValidCodeForUser(AppUser user, Instant now);

    @Modifying
    @Query("UPDATE EmailVerificationCode e SET e.used = true WHERE e.user = :user AND e.used = false")
    void invalidateAllForUser(AppUser user);

    @Modifying
    @Query("DELETE FROM EmailVerificationCode e WHERE e.expiresAt < :cutoff")
    void deleteExpiredBefore(Instant cutoff);
}
