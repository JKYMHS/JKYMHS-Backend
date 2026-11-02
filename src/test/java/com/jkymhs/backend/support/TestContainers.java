package com.jkymhs.backend.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 MySQL·Redis 컨테이너. 로컬 docker compose DB를 건드리지 않도록 테스트 전용 컨테이너를 쓴다.
 *
 * 테스트 JVM에서 딱 한 번(static) 띄우고 모든 테스트가 같이 쓴다. 컨테이너는 JVM이 끝날 때 Testcontainers가 정리한다.
 * 컨테이너를 스프링 빈(@Bean + @ServiceConnection)으로 등록하지 않은 이유:
 * 빈으로 등록하면 그 스프링 컨텍스트가 닫힐 때(@DirtiesContext 등) 컨테이너도 같이 꺼져서,
 * 같은 컨테이너를 쓰던 다른 테스트가 갑자기 DB·Redis 연결 실패로 깨진다.
 */
public final class TestContainers {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    static {
        Startables.deepStart(MYSQL, REDIS).join();
    }

    private TestContainers() {
    }

    /**
     * 테스트 클래스의 {@code @DynamicPropertySource}에서 불러서 DB·Redis 주소를 컨테이너 주소로 바꾼다.
     */
    public static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
