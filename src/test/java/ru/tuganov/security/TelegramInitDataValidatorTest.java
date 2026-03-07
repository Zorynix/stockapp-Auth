package ru.tuganov.security;

import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class TelegramInitDataValidatorTest {

    private static final String BOT_TOKEN = "1234567890:ABCdefGHIjklMNOpqrsTUVwxyz";

    @Test
    void validate_noBotToken_returnsTrue() {
        var validator = new TelegramInitDataValidator("", 86400);
        assertThat(validator.validate("any-data")).isTrue();
    }

    @Test
    void validate_missingHash_returnsFalse() {
        var validator = new TelegramInitDataValidator(BOT_TOKEN, 86400);
        assertThat(validator.validate("auth_date=1234567890&user={\"id\":123}")).isFalse();
    }

    @Test
    void validate_expiredAuthDate_returnsFalse() {
        var validator = new TelegramInitDataValidator(BOT_TOKEN, 60); // 60 seconds max age
        long expired = Instant.now().getEpochSecond() - 120; // 2 minutes ago
        String initData = buildInitData(BOT_TOKEN, expired);
        assertThat(validator.validate(initData)).isFalse();
    }

    @Test
    void validate_validData_returnsTrue() {
        var validator = new TelegramInitDataValidator(BOT_TOKEN, 86400);
        long now = Instant.now().getEpochSecond();
        String initData = buildInitData(BOT_TOKEN, now);
        assertThat(validator.validate(initData)).isTrue();
    }

    @Test
    void validate_wrongHash_returnsFalse() {
        var validator = new TelegramInitDataValidator(BOT_TOKEN, 86400);
        long now = Instant.now().getEpochSecond();
        String userJson = URLEncoder.encode("{\"id\":123,\"first_name\":\"Test\"}", StandardCharsets.UTF_8);
        String initData = "auth_date=" + now + "&user=" + userJson + "&hash=0000000000000000000000000000000000000000000000000000000000000000";
        assertThat(validator.validate(initData)).isFalse();
    }

    @Test
    void extractUserId_validData_returnsId() {
        var validator = new TelegramInitDataValidator(BOT_TOKEN, 86400);
        String userJson = URLEncoder.encode("{\"id\":777,\"first_name\":\"Test\"}", StandardCharsets.UTF_8);
        String initData = "user=" + userJson;
        Optional<Long> userId = validator.extractUserId(initData);
        assertThat(userId).contains(777L);
    }

    @Test
    void extractUserId_noUserField_returnsEmpty() {
        var validator = new TelegramInitDataValidator(BOT_TOKEN, 86400);
        Optional<Long> userId = validator.extractUserId("auth_date=123");
        assertThat(userId).isEmpty();
    }

    /** Builds a valid initData string with proper HMAC signature */
    private static String buildInitData(String botToken, long authDate) {
        try {
            String userJson = URLEncoder.encode("{\"id\":123,\"first_name\":\"Test\"}", StandardCharsets.UTF_8);
            String authDateStr = String.valueOf(authDate);

            // Build data check string (sorted by key, newline separated, without hash)
            String dataCheckString = "auth_date=" + authDateStr + "\n" + "user=" + "{\"id\":123,\"first_name\":\"Test\"}";

            // Calculate HMAC
            byte[] secretKey = hmacSha256(botToken.getBytes(StandardCharsets.UTF_8), "WebAppData".getBytes(StandardCharsets.UTF_8));
            byte[] hash = hmacSha256(dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);
            String hashHex = bytesToHex(hash);

            return "auth_date=" + authDateStr + "&user=" + userJson + "&hash=" + hashHex;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
