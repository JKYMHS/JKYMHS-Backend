package com.jkymhs.backend.domain.content.client;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 테스트용 TourAPI 응답 본문.
 */
public final class TourApiResponses {

    public static final String QUOTA_XML = """
            <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
            <returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg>
            <returnReasonCode>22</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>
            """;

    private TourApiResponses() {
    }

    /** items가 배열인 한 페이지 (totalCount = 항목 수) */
    public static String page(String... items) {
        return page(1, items.length, items);
    }

    public static String page(int pageNo, int totalCount, String... items) {
        String body = items.length == 0 ? "\"\"" : "{\"item\":[" + String.join(",", items) + "]}";
        return """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                 "body":{"items":%s,"numOfRows":1000,"pageNo":%d,"totalCount":%d}}}
                """.formatted(body, pageNo, totalCount);
    }

    /** "key", "value", ... → {"key":"value",...} */
    public static String item(String... keyValues) {
        return "{" + Arrays.stream(pairs(keyValues)).collect(Collectors.joining(",")) + "}";
    }

    private static String[] pairs(String[] keyValues) {
        String[] pairs = new String[keyValues.length / 2];
        for (int i = 0; i < keyValues.length; i += 2) {
            pairs[i / 2] = "\"" + keyValues[i] + "\":\"" + keyValues[i + 1] + "\"";
        }
        return pairs;
    }
}
