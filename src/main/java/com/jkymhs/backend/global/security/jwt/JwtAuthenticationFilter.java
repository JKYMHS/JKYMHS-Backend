package com.jkymhs.backend.global.security.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Authorization: Bearer {액세스 토큰} 헤더를 읽어서 로그인한 회원으로 인증 정보를 채우는 필터.
 *
 * 토큰이 없거나 잘못돼도 여기서 바로 401을 주지 않고 다음 필터로 넘긴다.
 * 로그인 없이 쓰는 API(/api/auth/login 등)는 그대로 통과해야 하기 때문이다.
 * 인증이 필요한 API라면 뒤에서 {@link JwtAuthenticationEntryPoint}가 401을 내려준다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 토큰이 왜 실패했는지(만료/위조) EntryPoint에 넘겨주기 위한 요청 속성 이름
    public static final String ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".ERROR";
    public static final String EXPIRED_TOKEN = "만료된 액세스 토큰";
    public static final String INVALID_TOKEN = "유효하지 않은 액세스 토큰";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            authenticate(token, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Long userId = jwtProvider.parseUserId(token);
            // principal에 회원 ID를 넣는다. 컨트롤러에서 @AuthenticationPrincipal Long userId로 꺼내 쓴다
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException e) {
            request.setAttribute(ERROR_ATTRIBUTE, EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            request.setAttribute(ERROR_ATTRIBUTE, INVALID_TOKEN);
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
