/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.concurrent;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

public class CountLatch {
    private final Synchronizer synchronizer;

    public CountLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Initial count must not be negative.");
        }

        synchronizer = new Synchronizer(count);
    }

    public CountLatch() {
        this(0);
    }

    public void increment() {
        synchronizer.releaseShared(1);
    }

    public int getCount() {
        return synchronizer.getCount();
    }

    public void decrement() {
        synchronizer.releaseShared(-1);
    }

    public void await() {
        synchronizer.acquireShared(1);
    }

    public void awaitInterruptibly() throws InterruptedException {
        synchronizer.acquireSharedInterruptibly(1);
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return synchronizer.tryAcquireSharedNanos(1, unit.toNanos(timeout));
    }

    private static final class Synchronizer extends AbstractQueuedSynchronizer {

        private Synchronizer(int count) {
            setState(count);
        }

        @Override
        protected int tryAcquireShared(int acquires) {
            return getState() == 0 ? 1 : -1;
        }

        @Override
        protected boolean tryReleaseShared(int updates) {
            while (true) {
                int count = getState();
                int next = count + updates;
                if (next < 0) {
                    return false;
                }

                if (compareAndSetState(count, next)) {
                    return next == 0;
                }
            }
        }

        int getCount() {
            return getState();
        }
    }
}
