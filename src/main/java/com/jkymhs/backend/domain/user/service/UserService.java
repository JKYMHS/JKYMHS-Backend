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

    /**
     * 회원 행을 잠그고(SELECT ... FOR UPDATE) 가져온다. 호출한 쪽의 트랜잭션이 끝날 때까지 같은 회원의 다른 잠금 요청은 기다린다.
     * 같은 회원의 요청이 동시에 와도 "확인 → 변경"이 겹치지 않아야 하는 곳에서 쓴다 (취향 저장, 탈퇴, AI 추천 횟수 등).
     * 바깥 트랜잭션 안에서 불러야 의미가 있다. 이 메서드만의 트랜잭션이면 반환하는 순간 잠금이 풀린다.
     */
    @Transactional
    public User getByIdForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESOURCE_NOT_FOUND, "존재하지 않는 회원"));
    }

    // AI 추천 요청 때 고른 동행을 기억한다. 다음 추천 화면의 기본값이 된다
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

    /**
     * 마이페이지에서 로그인한 회원에게 구글 계정을 연결한다.
     * 회원 행을 잠근 뒤 다시 확인해서, 같은 회원이 서로 다른 구글 계정을 동시에 연결해 한쪽이 덮어써지는 일을 막는다.
     * 다른 회원이 같은 구글 계정을 동시에 연결하면 google_sub UNIQUE 위반이 flush 시점에 나서 호출한 쪽이 409로 바꾼다.
     */
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

    // 탈퇴의 마지막 단계. 이 회원을 참조하는 행(FK)은 호출한 쪽이 먼저 지워야 한다
    @Transactional
    public void delete(Long userId) {
        userRepository.deleteById(userId);
    }
}
