package com.epam.rd.autotasks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadUnionImpl implements ThreadUnion {
    private final String name;
    private final AtomicInteger threadCount = new AtomicInteger(0);
    private final List<Thread> threads = Collections.synchronizedList(new ArrayList<>());
    private final List<FinishedThreadResult> finishedResults = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public ThreadUnionImpl(String name) {
        this.name = name;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        if (shutdown.get()) {
            throw new IllegalStateException("ThreadUnion is shut down. Cannot create new threads.");
        }

        int threadNumber = threadCount.getAndIncrement();
        Thread thread = new Thread(() -> {
            Throwable thrown = null;
            try {
                runnable.run();
            } catch (Throwable t) {
                thrown = t;
            } finally {
                finishedResults.add(new FinishedThreadResult(Thread.currentThread().getName(), thrown));
            }
        });

        thread.setName(name + "-worker-" + threadNumber);

        synchronized (threads) {
            threads.add(thread); // Ahora se agrega antes de iniciar el thread
        }

        return thread;
    }

    @Override
    public int totalSize() {
        return threadCount.get();
    }

    @Override
    public int activeSize() {
        synchronized (threads) {
            return (int) threads.stream().filter(Thread::isAlive).count();
        }
    }

    @Override
    public void shutdown() {
        shutdown.set(true);
        synchronized (threads) {
            for (Thread thread : threads) {
                thread.interrupt();
            }
        }
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public void awaitTermination() {
        synchronized (threads) {
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    @Override
    public boolean isFinished() {
        return isShutdown() && activeSize() == 0;
    }

    @Override
    public List<FinishedThreadResult> results() {
        return new ArrayList<>(finishedResults);
    }

    public static ThreadUnion newInstance(String name) {
        return new ThreadUnionImpl(name);
    }
}