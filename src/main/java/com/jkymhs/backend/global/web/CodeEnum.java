package com.jkymhs.backend.global.web;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * FE와 주고받는 값(code)이 Java 상수 이름(name)과 다른 enum이 구현한다. 예: {@code Companion.KIDS} ↔ {@code "kids"}.
 *
 * <ul>
 *   <li>JSON 응답·요청 본문: {@code @JsonValue}라서 Jackson이 code로 쓰고 code로 읽는다.
 *       (Jackson은 인터페이스 메서드에 붙은 애너테이션도 구현 enum에 적용한다)</li>
 *   <li>쿼리 파라미터({@code @RequestParam}, {@code @ModelAttribute}): {@link CodeEnumConverterFactory}가 code로 바꾼다.</li>
 *   <li>DB: 엔티티에서 {@code @Enumerated(STRING)}이라 대문자 name()이 저장된다. code를 바꿔도 DB 값은 그대로다.</li>
 * </ul>
 */
public interface CodeEnum {

    @JsonValue
    String code();
}
