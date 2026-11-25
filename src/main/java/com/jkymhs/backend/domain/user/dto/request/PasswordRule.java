package com.jkymhs.backend.domain.user.dto.request;

public final class PasswordRule {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,20}$";
    public static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.";

    private PasswordRule() {
    }
}
