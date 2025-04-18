/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2024 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.option;

import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.Module;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import picocli.CommandLine;

import javax.xml.namespace.QName;
import java.util.*;

public class TypeNameOptions implements Option {
    @CommandLine.Option(names = {"-t", "--type-name"}, split = ",", paramLabel = "<[prefix:]name>", required = true,
            description = "Names of the top-level city objects to process.")
    private String[] typeNames;

    @CommandLine.Option(names = "--namespace", split = ",", paramLabel = "<prefix=name>",
            description = "Prefix-to-namespace mappings.")
    private Map<String, String> namespaces;

    public Set<QName> getTypeNames(CityGMLContext context) {
        Set<QName> names = new HashSet<>();
        if (typeNames != null) {
            Map<String, List<String>> defaultNamespaces = getDefaultNamespaces();
            for (String typeName : typeNames) {
                String[] parts = typeName.split(":");
                if (parts.length == 2) {
                    String candidate = namespaces != null ? namespaces.get(parts[0]) : null;
                    if (candidate != null) {
                        names.add(new QName(candidate, parts[1]));
                    } else {
                        defaultNamespaces.getOrDefault(parts[0], Collections.emptyList()).stream()
                                .map(namespace -> new QName(namespace, parts[1]))
                                .forEach(names::add);
                    }
                } else {
                    defaultNamespaces.values().stream()
                            .flatMap(Collection::stream)
                            .filter(namespace -> context.getXMLObjects().getBuilder(namespace, parts[0]) != null)
                            .map(namespace -> new QName(namespace, parts[0]))
                            .forEach(names::add);
                }
            }
        }

        return names;
    }

    private Map<String, List<String>> getDefaultNamespaces() {
        Map<String, List<String>> namespaces = new HashMap<>();
        for (CityGMLModules context : CityGMLModules.all()) {
            for (Module module : context.getModules()) {
                namespaces.computeIfAbsent(module.getNamespacePrefix(), v -> new ArrayList<>())
                        .add(module.getNamespaceURI());
            }
        }

        return namespaces;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (typeNames != null) {
            for (String typeName : typeNames) {
                String[] parts = typeName.split(":");
                if (parts.length == 0 || parts.length > 2) {
                    throw new CommandLine.ParameterException(commandLine,
                            "A type name must be in [PREFIX:]NAME format but was '" + typeName + "'");
                }
            }
        }
    }
}
