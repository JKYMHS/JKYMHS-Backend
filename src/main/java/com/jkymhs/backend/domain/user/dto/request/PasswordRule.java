package com.jkymhs.backend.domain.user.dto.request;

/**
 * 비밀번호 규칙. 가입(SignupRequest)과 같은 규칙이고, 비밀번호 변경·설정 요청이 같이 쓴다.
 * 애너테이션 속성에는 컴파일 시점 상수만 넣을 수 있어서 static final String으로 둔다.
 */
final class PasswordRule {

    // 영문과 숫자를 하나 이상씩 포함한 8~20자. 키보드 기본 문자(ASCII 33~126: 영문·숫자·특수문자)만 허용하고 공백·한글·이모지는 막는다.
    // BCrypt는 72바이트까지만 저장할 수 있는데, 한글(3바이트)·이모지(4바이트)를 허용하면 20자로도 72바이트를 넘어 저장할 때 500이 난다
    static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]{8,20}$";
    static final String MESSAGE = "비밀번호는 영문과 숫자를 포함해 8~20자로 입력해주세요.";

    private PasswordRule() {
    }
}
