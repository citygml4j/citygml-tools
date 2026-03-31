/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.concurrent;

import java.util.concurrent.*;

public class ExecutorHelper {

    public static ThreadPoolExecutor newFixedAndBlockingThreadPool(int nThreads, int capacity, ThreadFactory factory) {
        BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(capacity) {
            @Override
            public boolean offer(Runnable o) {
                try {
                    put(o);
                    return true;
                } catch (InterruptedException e) {
                    return false;
                }
            }
        };

        return new ThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS, queue, factory);
    }

    public static ThreadPoolExecutor newFixedAndBlockingThreadPool(int nThreads, int capacity) {
        return newFixedAndBlockingThreadPool(nThreads, capacity, Executors.defaultThreadFactory());
    }

    public static ThreadPoolExecutor newFixedAndBlockingThreadPool(int nThreads) {
        return newFixedAndBlockingThreadPool(nThreads, nThreads * 2);
    }
}
