/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.io;

import org.citygml4j.tools.concurrent.CountLatch;
import org.citygml4j.tools.concurrent.ExecutorHelper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class FileCopier implements AutoCloseable {
    private final ExecutorService service;
    private final CountLatch countLatch = new CountLatch();
    private final Set<String> targetDirs = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();

    private volatile boolean shouldRun = true;
    private Exception exception;

    public FileCopier() {
        this(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    public FileCopier(int nThreads) {
        service = ExecutorHelper.newFixedAndBlockingThreadPool(nThreads, 100000);
    }

    public void copy(Path source, Path target) throws Exception {
        if (shouldRun) {
            countLatch.increment();
            service.submit(() -> {
                try {
                    if (!targetDirs.contains(target.getParent().toString())) {
                        synchronized (lock) {
                            if (targetDirs.add(target.getParent().toString())
                                    && !Files.exists(target.getParent())) {
                                Files.createDirectories(target.getParent());
                            }
                        }
                    }

                    FileHelper.copy(source, target);
                } catch (Exception e) {
                    shouldRun = false;
                    exception = e;
                } finally {
                    countLatch.decrement();
                }
            });
        } else if (exception != null) {
            throw exception;
        }
    }

    @Override
    public void close() throws Exception {
        countLatch.await();
        service.shutdown();
        targetDirs.clear();

        if (exception != null) {
            throw exception;
        }
    }
}
