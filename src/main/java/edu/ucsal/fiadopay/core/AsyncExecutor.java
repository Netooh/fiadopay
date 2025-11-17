package edu.ucsal.fiadopay.core;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

@Component
public class AsyncExecutor {

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    private final ExecutorService workerPool;
    private final Thread dispatcher;

    public AsyncExecutor() {
        // pool size can be tuned later
        this.workerPool = Executors.newFixedThreadPool(4);
        this.dispatcher = new Thread(this::dispatchLoop, "async-executor-dispatcher");
        this.dispatcher.setDaemon(true);
        this.dispatcher.start();
    }

    private void dispatchLoop() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Runnable task = queue.take(); // blocks until task available
                try {
                    workerPool.submit(task);
                } catch (RejectedExecutionException rex) {
                    // fallback: re-enqueue or log
                    System.err.println("Task rejected by worker pool: " + rex.getMessage());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    public boolean enqueue(Runnable task) {
        return queue.offer(task);
    }

    public int queueSize() {
        return queue.size();
    }

    @PreDestroy
    public void shutdown() {
        dispatcher.interrupt();
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
