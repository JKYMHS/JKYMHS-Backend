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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
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
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    /*
     * 세션을 안 쓰는(STATELESS) 설정에서 스프링 시큐리티는 인증 정보를 요청 속성(request attribute)에서 다시 읽는다.
     * 비동기 응답(Callable, SseEmitter 등)은 요청이 한 번 더 디스패치되는데, 이 필터는 OncePerRequestFilter라 두 번째에는 돌지 않는다.
     * 그래서 여기에 저장해 두지 않으면 두 번째 디스패치에서 로그인 안 한 사용자로 보여 401이 나간다.
     */
    private final SecurityContextRepository securityContextRepository = new RequestAttributeSecurityContextRepository();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            authenticate(token, request, response);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request, HttpServletResponse response) {
        try {
            Long userId = jwtProvider.parseUserId(token);
            // principal에 회원 ID를 넣는다. 컨트롤러에서 @AuthenticationPrincipal Long userId로 꺼내 쓴다
            var authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, AuthorityUtils.createAuthorityList("ROLE_USER"));
            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, request, response);
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
