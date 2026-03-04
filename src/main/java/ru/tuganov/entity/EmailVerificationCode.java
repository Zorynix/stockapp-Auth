package ru.tuganov.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "email_verification_codes")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerificationCode {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used = false;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isExhausted() {
        return attempts >= MAX_ATTEMPTS;
    }

    public void incrementAttempts() {
        this.attempts++;
    }

    public boolean isValid() {
        return !used && !isExpired() && !isExhausted();
    }

    public static EmailVerificationCode create(AppUser user, String code, int ttlMinutes) {
        var otp = new EmailVerificationCode();
        otp.user = user;
        otp.code = code;
        otp.expiresAt = Instant.now().plusSeconds((long) ttlMinutes * 60);
        return otp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EmailVerificationCode that = (EmailVerificationCode) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
