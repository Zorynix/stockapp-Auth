package ru.tuganov.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Минимальная JPA-сущность для tracked_instruments.
 * AuthService использует её только для чтения счётчиков и миграции при объединении аккаунтов.
 */
@Entity
@Table(name = "tracked_instruments")
@Getter
@Setter
@NoArgsConstructor
public class TrackedInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String figi;

    @Column(name = "instrument_name", nullable = false)
    private String instrumentName;

    @Column(name = "sell_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal sellPrice;

    @Column(name = "buy_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal buyPrice;

    @Column(name = "buy_alert_sent", nullable = false)
    private boolean buyAlertSent = false;

    @Column(name = "sell_alert_sent", nullable = false)
    private boolean sellAlertSent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser user;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrackedInstrument that = (TrackedInstrument) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
