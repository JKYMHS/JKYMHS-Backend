package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 다른 도메인(auth 등)은 UserRepository를 직접 쓰지 않고 이 서비스를 통해 회원 데이터에 접근한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findByGoogleSub(String googleSub) {
        return userRepository.findByGoogleSub(googleSub);
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 회원"));
    }

    @Transactional
    public User register(User user) {
        return userRepository.save(user);
    }

    /**
     * 회원에게 구글 계정을 연결한다.
     * 바로 flush해서 google_sub UNIQUE 위반(같은 구글 계정을 동시에 다른 회원에게 연결)을 커밋 시점이 아니라 여기서
     * {@code DataIntegrityViolationException}으로 알 수 있게 한다. 그래야 호출한 쪽이 409로 바꿔서 응답할 수 있다.
     */
    @Transactional
    public void linkGoogle(User user, String googleSub, String googleEmail) {
        user.linkGoogle(googleSub, googleEmail);
        userRepository.saveAndFlush(user);
    }
}
