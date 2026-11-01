package com.jkymhs.backend.support;

import com.jkymhs.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 서버 전체(스프링 컨텍스트 + 실제 MySQL·Redis)를 띄우는 통합 테스트의 부모 클래스.
 * 상속하면 MockMvc로 실제 API를 호출해볼 수 있다.
 *
 * 테스트가 끝날 때마다 모든 테이블과 Redis를 비워서 테스트끼리 데이터가 섞이지 않게 한다.
 * (@Transactional 롤백 방식은 MockMvc 요청 안에서 커밋되는 데이터나 Redis 데이터를 되돌리지 못한다)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUpDatabaseAndRedis() {
        truncateAllTables();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            flushRedis(connection);
            return null;
        });
    }

    // FOREIGN_KEY_CHECKS는 DB 연결(세션)마다 따로라서, 같은 연결 하나에서 끄고 → 비우고 → 다시 켠다
    private void truncateAllTables() {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                List<String> tables = new ArrayList<>();
                try (ResultSet rs = statement.executeQuery(
                        "SELECT table_name FROM information_schema.tables "
                                + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'")) {
                    while (rs.next()) {
                        tables.add(rs.getString(1));
                    }
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : tables) {
                    statement.execute("TRUNCATE TABLE `" + table + "`");
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
    }

    private static void flushRedis(RedisConnection connection) {
        connection.serverCommands().flushDb();
    }
}
