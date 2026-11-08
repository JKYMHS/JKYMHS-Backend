package com.jkymhs.backend.domain.user.dto.request;

/**
 * 비밀번호 규칙. 가입(SignupRequest)과 같은 규칙이고, 비밀번호 변경·설정 요청이 같이 쓴다.
 * 애너테이션 속성에는 컴파일 시점 상수만 넣을 수 있어서 static final String으로 둔다.
 */
final class PasswordRule {

    // 영문과 숫자를 하나 이상씩 포함한 8~20자, 공백 불가 (그 밖의 특수문자는 허용)
    static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)\\S{8,20}$";
    static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.";

    private PasswordRule() {
    }
}
