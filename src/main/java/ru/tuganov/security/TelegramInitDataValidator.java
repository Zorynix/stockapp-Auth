package ru.tuganov.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
public class TelegramInitDataValidator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String WEBAPPDATA = "WebAppData";

    private final String botToken;
    private final long authDateMaxAgeSeconds;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramInitDataValidator(
            @Value("${telegram.bot.token:}") String botToken,
            @Value("${telegram.init-data.max-age-seconds:86400}") long authDateMaxAgeSeconds) {
        this.botToken = botToken;
        this.authDateMaxAgeSeconds = authDateMaxAgeSeconds;
    }

    public boolean validate(String initData) {
        if (botToken == null || botToken.isBlank()) {
            log.warn("Telegram bot token not configured — skipping initData signature validation");
            return true;
        }

        try {
            Map<String, String> params = parseQueryString(initData);
            String receivedHash = params.remove("hash");
            if (receivedHash == null) {
                log.warn("initData missing 'hash' field");
                return false;
            }

            String authDateStr = params.get("auth_date");
            if (authDateStr == null) return false;
            long authDate = Long.parseLong(authDateStr);
            long age = Instant.now().getEpochSecond() - authDate;
            if (age > authDateMaxAgeSeconds) {
                log.warn("Telegram initData expired: age={}s", age);
                return false;
            }

            String dataCheckString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            byte[] secretKey = hmacSha256(botToken.getBytes(StandardCharsets.UTF_8),
                    WEBAPPDATA.getBytes(StandardCharsets.UTF_8));
            byte[] expectedHash = hmacSha256(
                    dataCheckString.getBytes(StandardCharsets.UTF_8), secretKey);

            String expectedHex = bytesToHex(expectedHash);
            boolean valid = MessageDigest.isEqual(
                    expectedHex.getBytes(StandardCharsets.UTF_8),
                    receivedHash.toLowerCase().getBytes(StandardCharsets.UTF_8));

            if (!valid) {
                log.warn("initData HMAC mismatch");
            }
            return valid;

        } catch (Exception e) {
            log.error("Failed to validate Telegram initData: {}", e.getMessage());
            return false;
        }
    }

    public Optional<Long> extractUserId(String initData) {
        try {
            Map<String, String> params = parseQueryString(initData);
            String userJson = params.get("user");
            if (userJson == null) return Optional.empty();

            JsonNode userNode = objectMapper.readTree(userJson);
            if (userNode.has("id")) {
                return Optional.of(userNode.get("id").asLong());
            }
        } catch (Exception e) {
            log.error("Failed to extract userId from initData: {}", e.getMessage());
        }
        return Optional.empty();
    }

    private Map<String, String> parseQueryString(String initData) {
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : initData.split("&")) {
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String key = URLDecoder.decode(pair.substring(0, eqIdx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eqIdx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
        return mac.doFinal(data);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
