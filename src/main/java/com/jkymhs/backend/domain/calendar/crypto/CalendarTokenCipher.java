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

/**
 * 구글 리프레시 토큰 암호화 (AES-256-GCM).
 *
 * 리프레시 토큰은 사용자가 없을 때도 그 사람 캘린더에 일정을 쓸 수 있는 열쇠라서 평문으로 저장하지 않는다.
 * GCM은 암호화와 함께 위·변조 검사(인증 태그 128비트)를 해서, DB 값을 누가 바꾸면 복호화가 실패한다.
 *
 * 저장 형식: base64(IV 12바이트 ‖ 암호문+태그). IV는 암호화할 때마다 새로 뽑는다.
 * GCM은 같은 키로 같은 IV를 두 번 쓰면 안전성이 깨지기 때문이다 (12바이트 난수면 충돌 확률이 무시할 만큼 작다).
 *
 * 키(google-calendar.token-encryption-key)는 쓸 때 읽어서 검사한다. 비었거나 32바이트가 아니면
 * 서버는 뜨고 캘린더 연동·작업만 503이다 (캘린더를 안 쓰는 개발 환경에서도 서버가 떠야 해서). 키 값은 어디에도 로그로 남기지 않는다.
 */
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

    /** 키를 쓸 수 있는지 (연동 전에 미리 확인해서, 구글 인가 코드를 써버린 뒤 실패하지 않게) */
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

    /**
     * @throws CustomException 503 — 키가 없거나 다르다(키를 바꿨다), 값이 손상됐다
     */
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
