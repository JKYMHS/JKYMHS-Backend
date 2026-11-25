package com.jkymhs.backend.domain.auth.service;

public record AuthResult<T>(T body, String refreshToken) {
}
