package com.jkymhs.backend.global.security;

import com.jkymhs.backend.global.security.jwt.JwtAccessDeniedHandler;
import com.jkymhs.backend.global.security.jwt.JwtAuthenticationEntryPoint;
import com.jkymhs.backend.global.security.jwt.JwtAuthenticationFilter;
import com.jkymhs.backend.global.security.jwt.JwtProperties;
import com.jkymhs.backend.global.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthCookieProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver,
            UrlBasedCorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                // CORS 필터가 인증보다 앞에서 동작한다. 사전 요청(OPTIONS)에는 Authorization 헤더가 없어서, 뒤로 넘어가면 401이 된다
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                /*
                 * CSRF는 브라우저가 쿠키를 자동으로 붙이는 점을 노린 공격이다. 리프레시 토큰이 쿠키지만 아래 이유로 CSRF 보호를 꺼도 된다.
                 * - 일반 API는 쿠키가 아니라 Authorization 헤더로 인증한다. 다른 사이트는 이 헤더를 붙일 수 없다
                 * - 쿠키는 Path=/api/auth라서 재발급·로그아웃 요청에만 붙는다
                 * - 기본값 SameSite=Lax라서 다른 사이트에서 보낸 POST에는 쿠키가 붙지 않는다
                 * 단, SameSite=None으로 배포하면 세 번째 보호가 사라져서 Origin 검증이 필요하다. 이 코드에는 구현돼 있지 않다.
                 * (위 CORS 필터가 허용되지 않은 Origin이 붙은 요청을 막아주지만, Origin 헤더가 없는 요청은 통과한다)
                 */
                .csrf(AbstractHttpConfigurer::disable)
                // 세션을 쓰지 않는 JWT 서버라서 세션 기반 기능도 끈다
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // 로그아웃은 로그인한 사용자만 할 수 있다. 아래 /api/auth/** 보다 먼저 와야 한다 (위에서부터 처음 맞는 규칙 적용)
                        .requestMatchers("/api/auth/logout").authenticated()
                        .requestMatchers("/api/auth/**").permitAll()
                        // 컨트롤러에서 예외가 나면 스프링이 /error로 다시 보내는데, 여기를 막으면 원래 에러 대신 401이 나간다
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )

                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(new JwtAuthenticationEntryPoint(handlerExceptionResolver))
                        .accessDeniedHandler(new JwtAccessDeniedHandler(handlerExceptionResolver))
                )

                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * FE와 API의 주소가 다르면 브라우저가 CORS 규칙으로 응답을 막는다. 여기 적은 Origin만 응답을 읽고 쿠키를 주고받을 수 있다.
     */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        // 리프레시 토큰 쿠키를 주고받으려면 켜야 한다 (FE도 credentials: "include"로 호출해야 한다)
        configuration.setAllowCredentials(true);
        // 브라우저가 사전 요청 결과를 1시간 동안 재사용한다
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
