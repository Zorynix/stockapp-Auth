package ru.tuganov.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import ru.tuganov.entity.AppUser;
import ru.tuganov.entity.EmailVerificationCode;
import ru.tuganov.repository.AppUserRepository;
import ru.tuganov.repository.EmailVerificationCodeRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class EmailVerificationCodeRepositoryIT extends BaseIntegrationTest {

    @Autowired
    AppUserRepository userRepo;
    @Autowired
    EmailVerificationCodeRepository codeRepo;

    private AppUser user;

    @BeforeEach
    void createUser() {
        user = userRepo.save(AppUser.fromEmail("otp@test.com", "pw"));
    }

    @Test
    void findValidCode_returnsLatestValid() {
        codeRepo.save(EmailVerificationCode.create(user, "111111", 15));
        codeRepo.save(EmailVerificationCode.create(user, "222222", 15));
        codeRepo.flush();

        assertThat(codeRepo.findValidCodeForUser(user, Instant.now()))
                .isPresent()
                .get()
                .extracting(EmailVerificationCode::getCode)
                .isEqualTo("222222");
    }

    @Test
    void findValidCode_ignoresUsedCode() {
        EmailVerificationCode otp = EmailVerificationCode.create(user, "333333", 15);
        otp.setUsed(true);
        codeRepo.save(otp);

        assertThat(codeRepo.findValidCodeForUser(user, Instant.now())).isEmpty();
    }

    @Test
    void findValidCode_ignoresExpiredCode() {
        EmailVerificationCode otp = EmailVerificationCode.create(user, "444444", 15);
        otp.setExpiresAt(Instant.now().minusSeconds(60));
        codeRepo.save(otp);

        assertThat(codeRepo.findValidCodeForUser(user, Instant.now())).isEmpty();
    }

    @Test
    void findValidCode_ignoresExhaustedCode() {
        EmailVerificationCode otp = EmailVerificationCode.create(user, "555555", 15);
        for (int i = 0; i < 5; i++) {
            otp.incrementAttempts();
        }
        codeRepo.save(otp);

        assertThat(codeRepo.findValidCodeForUser(user, Instant.now())).isEmpty();
    }

    @Test
    void findValidCode_returnsEmptyForDifferentUser() {
        AppUser other = userRepo.save(AppUser.fromEmail("other@test.com", "pw"));
        codeRepo.save(EmailVerificationCode.create(other, "666666", 15));

        assertThat(codeRepo.findValidCodeForUser(user, Instant.now())).isEmpty();
    }

    @Test
    void invalidateAllForUser_marksAllAsUsed() {
        codeRepo.save(EmailVerificationCode.create(user, "777777", 15));
        codeRepo.save(EmailVerificationCode.create(user, "888888", 15));
        codeRepo.flush();

        codeRepo.invalidateAllForUser(user);
        codeRepo.flush();

        assertThat(codeRepo.findValidCodeForUser(user, Instant.now())).isEmpty();
    }

    @Test
    void invalidateAllForUser_doesNotAffectOtherUser() {
        AppUser other = userRepo.save(AppUser.fromEmail("safe@test.com", "pw"));
        codeRepo.save(EmailVerificationCode.create(other, "999999", 15));

        codeRepo.invalidateAllForUser(user);
        codeRepo.flush();

        assertThat(codeRepo.findValidCodeForUser(other, Instant.now())).isPresent();
    }

    @Test
    void deleteExpiredBefore_removesExpiredCodes() {
        EmailVerificationCode expired = EmailVerificationCode.create(user, "000001", 15);
        expired.setExpiresAt(Instant.now().minusSeconds(3600));
        EmailVerificationCode valid = EmailVerificationCode.create(user, "000002", 15);
        codeRepo.save(expired);
        codeRepo.save(valid);
        codeRepo.flush();

        codeRepo.deleteExpiredBefore(Instant.now());
        codeRepo.flush();

        assertThat(codeRepo.count()).isEqualTo(1);
        assertThat(codeRepo.findAll().get(0).getCode()).isEqualTo("000002");
    }
}
