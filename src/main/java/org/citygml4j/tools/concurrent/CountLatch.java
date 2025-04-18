/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
