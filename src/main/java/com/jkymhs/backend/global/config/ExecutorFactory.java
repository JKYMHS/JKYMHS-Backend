package com.jkymhs.backend.global.config;

import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 도메인별 비동기 실행기를 만든다. 사용 예 (각 도메인의 설정 클래스):
 * <pre>{@code
 * @Bean
 * public TaskExecutor recommendationExecutor(ExecutorFactory factory) {
 *     return factory.create("recommendation", 2, 4, 50);
 * }
 * // 사용하는 쪽: @Async("recommendationExecutor")
 * }</pre>
 *
 * 반드시 {@code @Bean} 메서드의 반환값으로 등록한다. 스레드 풀은 스프링이 빈을 초기화할 때 만들어지고
 * 서버가 내려갈 때 정리된다 (여기서 직접 initialize()하면 스프링이 한 번 더 초기화해서 풀이 두 개 생긴다).
 *
 * {@code async.enabled=false}(테스트)이면 {@link SyncTaskExecutor}를 돌려준다. 작업이 호출한 스레드에서 바로 끝나므로
 * 테스트가 "비동기 작업이 끝날 때까지 기다리기" 없이 결과를 확인할 수 있다.
 */
public class ExecutorFactory {

    // 서버 종료 시 실행 중인 작업이 끝나기를 기다리는 최대 시간
    private static final int AWAIT_TERMINATION_SECONDS = 10;

    private final boolean asyncEnabled;

    public ExecutorFactory(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    /**
     * @param name          스레드 이름 앞부분 (로그에서 어느 도메인 작업인지 보이게)
     * @param coreSize      평소 유지하는 스레드 수
     * @param maxSize       대기열이 가득 찼을 때 늘릴 수 있는 최대 스레드 수
     * @param queueCapacity 대기열 크기. 대기열과 최대 스레드가 모두 차면 TaskRejectedException이 난다
     */
    public TaskExecutor create(String name, int coreSize, int maxSize, int queueCapacity) {
        if (!asyncEnabled) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(name + "-");
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
        return executor;
    }
}
