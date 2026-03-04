package ru.tuganov.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.tuganov.entity.AppUser;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByTelegramId(Long telegramId);

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByTelegramId(Long telegramId);
}
