package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.web.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 온보딩·마이페이지에서 고르는 여행 스타일 7개 (1~3개 선택).
 * 값이 고정이고 운영 중에 바뀔 일이 없어서 테이블 대신 enum으로 둔다. FE 값은 소문자 code("rest")이고,
 * DB(user_travel_styles.style_code)에는 대문자 name()이 저장된다.
 * label·description은 FE 목업(mocks/data/member.ts)과 같은 문구라서 FE가 이 목록을 그대로 그린다.
 */
@Getter
@RequiredArgsConstructor
public enum TravelStyle implements CodeEnum {

    REST("rest", "휴식", "카페·스파처럼 여유롭게"),
    ACTIVITY("activity", "액티비티", "체험·레저 위주"),
    CULTURE("culture", "문화·역사", "고궁·박물관·전시"),
    FOOD("food", "미식", "맛집·시장·로컬 음식"),
    NATURE("nature", "자연", "산책로·공원·전망"),
    SHOPPING("shopping", "쇼핑", "거리·시장·편집숍"),
    FESTIVAL("festival", "축제·공연", "지역 축제·공연·행사");

    private final String code;
    // Enum.name()과 헷갈리지 않게 화면 이름은 label로 부른다 (응답 JSON에서는 name)
    private final String label;
    private final String description;

    @Override
    public String code() {
        return code;
    }
}
