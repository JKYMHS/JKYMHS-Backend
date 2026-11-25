package com.jkymhs.backend.global.config;

import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class ExecutorFactory {

    private static final int AWAIT_TERMINATION_SECONDS = 10;

    private final boolean asyncEnabled;

    public ExecutorFactory(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

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
