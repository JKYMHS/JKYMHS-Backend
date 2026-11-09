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

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 회원"));
    }

    @Transactional
    public User register(User user) {
        return userRepository.save(user);
    }
}
