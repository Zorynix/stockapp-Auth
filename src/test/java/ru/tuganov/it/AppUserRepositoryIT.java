package ru.tuganov.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import ru.tuganov.entity.AppUser;
import ru.tuganov.repository.AppUserRepository;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class AppUserRepositoryIT extends BaseIntegrationTest {

    @Autowired
    AppUserRepository repo;

    @Test
    void saveAndFindByEmail() {
        AppUser saved = repo.save(AppUser.fromEmail("alice@test.com", "hash"));

        assertThat(repo.findByEmail("alice@test.com"))
                .isPresent()
                .get()
                .extracting(AppUser::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void saveAndFindByTelegramId() {
        AppUser saved = repo.save(AppUser.fromTelegram(111222333L, 111222333L));

        assertThat(repo.findByTelegramId(111222333L))
                .isPresent()
                .get()
                .extracting(AppUser::getId)
                .isEqualTo(saved.getId());
    }

    @Test
    void existsByEmail_trueAndFalse() {
        repo.save(AppUser.fromEmail("bob@test.com", "hash"));

        assertThat(repo.existsByEmail("bob@test.com")).isTrue();
        assertThat(repo.existsByEmail("nobody@test.com")).isFalse();
    }

    @Test
    void existsByTelegramId_trueAndFalse() {
        repo.save(AppUser.fromTelegram(999L, 999L));

        assertThat(repo.existsByTelegramId(999L)).isTrue();
        assertThat(repo.existsByTelegramId(888L)).isFalse();
    }

    @Test
    void duplicateEmail_throwsDataIntegrityViolation() {
        repo.saveAndFlush(AppUser.fromEmail("dup@test.com", "hash"));

        assertThatThrownBy(() -> repo.saveAndFlush(AppUser.fromEmail("dup@test.com", "other")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void duplicateTelegramId_throwsDataIntegrityViolation() {
        repo.saveAndFlush(AppUser.fromTelegram(777L, 777L));

        assertThatThrownBy(() -> repo.saveAndFlush(AppUser.fromTelegram(777L, 777L)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findById_absent_returnsEmpty() {
        assertThat(repo.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void telegramUser_hasNullEmail() {
        AppUser saved = repo.saveAndFlush(AppUser.fromTelegram(42L, 42L));

        AppUser found = repo.findById(saved.getId()).orElseThrow();
        assertThat(found.getEmail()).isNull();
        assertThat(found.isTelegramLinked()).isTrue();
    }

    @Test
    void emailUser_hasNullTelegramId() {
        AppUser saved = repo.saveAndFlush(AppUser.fromEmail("email@test.com", "hash"));

        AppUser found = repo.findById(saved.getId()).orElseThrow();
        assertThat(found.getTelegramId()).isNull();
        assertThat(found.isTelegramLinked()).isFalse();
        assertThat(found.isEmailConfirmed()).isFalse();
    }
}
