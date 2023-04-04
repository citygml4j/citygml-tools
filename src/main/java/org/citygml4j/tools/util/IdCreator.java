/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2023 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.util;

import org.xmlobjects.gml.util.id.DefaultIdCreator;

import java.util.UUID;

public class IdCreator implements org.xmlobjects.gml.util.id.IdCreator {
    private final String prefix = DefaultIdCreator.getInstance().getDefaultPrefix();
    private long index;

    @Override
    public String createId() {
        String id = "citygml-tools-" + index++;
        return prefix + UUID.nameUUIDFromBytes(id.getBytes());
    }
}
