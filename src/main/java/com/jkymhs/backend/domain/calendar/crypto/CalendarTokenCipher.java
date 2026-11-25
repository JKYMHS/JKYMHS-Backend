package com.jkymhs.backend.domain.calendar.crypto;

import com.jkymhs.backend.domain.calendar.client.google.GoogleCalendarProperties;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
public class CalendarTokenCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final GoogleCalendarProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CalendarTokenCipher(GoogleCalendarProperties properties) {
        this.properties = properties;
    }

    public void checkKey() {
        key();
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            log.error("캘린더 토큰 암호화 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "캘린더 토큰 암호화 실패");
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            if (all.length <= IV_BYTES) {
                throw new IllegalArgumentException("too short");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.error("캘린더 토큰 복호화 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "캘린더 토큰 복호화 실패");
        }
    }

    private SecretKey key() {
        String encoded = properties.tokenEncryptionKey();
        if (encoded == null || encoded.isBlank()) {
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "캘린더 토큰 암호화 키 없음");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "캘린더 토큰 암호화 키가 base64가 아님");
        }
        if (bytes.length != KEY_BYTES) {
            throw new CustomException(ErrorCode.SERVICE_UNAVAILABLE, "캘린더 토큰 암호화 키 길이가 32바이트가 아님");
        }
        return new SecretKeySpec(bytes, "AES");
    }
}
