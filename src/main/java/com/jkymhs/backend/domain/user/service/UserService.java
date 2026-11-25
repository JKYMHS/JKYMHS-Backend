package com.jkymhs.backend.domain.user.service;

import com.jkymhs.backend.domain.user.entity.User;
import com.jkymhs.backend.domain.user.repository.UserRepository;
import com.jkymhs.backend.global.exception.CustomException;
import com.jkymhs.backend.global.exception.ErrorCode;
import com.jkymhs.backend.global.model.Companion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

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
    public User getByIdForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 회원"));
    }

    @Transactional
    public void rememberCompanion(Long userId, Companion companion) {
        getById(userId).rememberCompanion(companion);
    }

    public Optional<Companion> getLastCompanion(Long userId) {
        return Optional.ofNullable(getById(userId).getLastCompanion());
    }

    @Transactional
    public User register(User user) {
        return userRepository.save(user);
    }

    @Transactional
    public void linkGoogle(User user, String googleSub, String googleEmail) {
        user.linkGoogle(googleSub, googleEmail);
        userRepository.saveAndFlush(user);
    }

    @Transactional
    public User linkGoogle(Long userId, String googleSub, String googleEmail) {
        User user = getByIdForUpdate(userId);
        if (user.isGoogleLinked()) {
            throw new CustomException(ErrorCode.GOOGLE_ACCOUNT_ALREADY_LINKED, "이미 구글 계정이 연결된 회원");
        }
        user.linkGoogle(googleSub, googleEmail);
        userRepository.saveAndFlush(user);
        return user;
    }

    @Transactional
    public void delete(Long userId) {
        userRepository.deleteById(userId);
    }
}
