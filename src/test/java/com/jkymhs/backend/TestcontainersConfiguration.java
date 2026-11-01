package com.jkymhs.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 MySQL·Redis 컨테이너.
 * 로컬 docker compose DB를 건드리지 않도록 테스트 전용 컨테이너를 띄운다.
 * {@code @ServiceConnection}이 컨테이너의 주소·포트를 spring.datasource / spring.data.redis 설정에 자동으로 넣어준다.
 *
 * 컨테이너를 static으로 한 번만 띄워서, 테스트마다 @MockitoBean이 달라 스프링 컨텍스트가 여러 개 생겨도 같은 컨테이너를 같이 쓴다.
 * destroyMethod = ""는 컨텍스트 하나가 닫힐 때 다른 컨텍스트가 쓰는 컨테이너까지 꺼버리지 않게 하려는 설정이다.
 * (컨테이너는 테스트 JVM이 끝나면 Testcontainers가 정리한다)
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    static {
        Startables.deepStart(MYSQL, REDIS).join();
    }

    @Bean(destroyMethod = "")
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return MYSQL;
    }

    @Bean(destroyMethod = "")
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return REDIS;
    }
}
