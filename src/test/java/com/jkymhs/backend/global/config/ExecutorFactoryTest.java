package com.jkymhs.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorFactoryTest {

    @Test
    @DisplayName("비동기가 켜져 있으면 이름·크기를 지정한 스레드 풀을 만들고, 작업은 그 이름의 스레드에서 돈다")
    void threadPool() throws Exception {
        TaskExecutor executor = new ExecutorFactory(true).create("behavior", 2, 4, 100);

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        assertThat(pool.getCorePoolSize()).isEqualTo(2);
        assertThat(pool.getMaxPoolSize()).isEqualTo(4);
        assertThat(pool.getQueueCapacity()).isEqualTo(100);

        // 스프링 빈이면 스프링이 초기화한다. 여기서는 빈이 아니라 직접 초기화한다
        pool.initialize();
        try {
            CompletableFuture<String> threadName = new CompletableFuture<>();
            pool.execute(() -> threadName.complete(Thread.currentThread().getName()));
            assertThat(threadName.get(5, TimeUnit.SECONDS)).startsWith("behavior-");
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("비동기가 꺼져 있으면 호출한 스레드에서 바로 실행한다")
    void sync() {
        TaskExecutor executor = new ExecutorFactory(false).create("behavior", 2, 4, 100);
        Thread caller = Thread.currentThread();
        Thread[] ran = new Thread[1];

        executor.execute(() -> ran[0] = Thread.currentThread());

        assertThat(executor).isInstanceOf(SyncTaskExecutor.class);
        assertThat(ran[0]).isSameAs(caller);
    }
}
