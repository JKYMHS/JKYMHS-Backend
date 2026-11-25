package com.jkymhs.backend.domain.user.entity;

import com.jkymhs.backend.global.web.CodeEnum;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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
    private final String label;
    private final String description;

    @Override
    public String code() {
        return code;
    }
}
