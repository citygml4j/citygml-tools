/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command;

import org.citygml4j.tools.ExecutionException;
import picocli.CommandLine;

import java.util.concurrent.Callable;

public interface Command extends Callable<Integer> {
    @Override
    Integer call() throws ExecutionException;

    default void preprocess(CommandLine commandLine) throws Exception {
    }
}
