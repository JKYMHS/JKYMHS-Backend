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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtProvider jwtProvider,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) throws Exception {
        http
                // JWT를 Authorization 헤더로 보내는 API 서버라서 세션·쿠키 기반 기능은 끈다
                .csrf(AbstractHttpConfigurer::disable)
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

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
