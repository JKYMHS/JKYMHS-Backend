package com.jkymhs.backend.domain.recommendation.client.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;

@Slf4j
public class HttpAiCourseClient implements AiCourseClient {
    static final String PATH = "/v1/courses";

    private final RestClient restClient;

    public HttpAiCourseClient(RestClient.Builder builder, AiProperties properties) {
        RestClient.Builder configured = builder.baseUrl(properties.baseUrl());
        if (properties.hasApiKey()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        this.restClient = configured.build();
    }

    @Override
    public AiCourseResponse compose(AiCourseRequest request) {
        try {
            AiCourseResponse response = restClient.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiCourseResponse.class);
            if (response == null || response.courses() == null) {
                throw new AiCourseException("AI 응답 본문 없음");
            }
            return response;
        } catch (ResourceAccessException e) {
            if (isTimeout(e)) {
                throw new AiTimeoutException("AI 응답 시간 초과", e);
            }
            throw new AiCourseException("AI 서버 연결 실패: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // 본문에 내부 정보가 있을 수 있어 메시지만 남김
            log.warn("AI 서버 호출 실패: {}", e.getMessage());
            throw new AiCourseException("AI 서버 호출 실패: " + e.getMessage(), e);
        }
    }

    private static boolean isTimeout(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
