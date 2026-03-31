/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.option;

import picocli.CommandLine;

import java.util.Set;

public class IdOptions implements Option {
    @CommandLine.Option(names = {"-i", "--id"}, split = ",", paramLabel = "<id>",
            description = "Only process city objects with a matching gml:id.")
    private Set<String> ids;

    public Set<String> getIds() {
        return ids;
    }
}
