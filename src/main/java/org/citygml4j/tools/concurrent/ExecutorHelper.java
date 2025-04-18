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
