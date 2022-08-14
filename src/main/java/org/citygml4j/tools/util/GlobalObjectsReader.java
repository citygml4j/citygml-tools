/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2022 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.*;
import org.xml.sax.SAXException;
import org.xmlobjects.stream.XMLReader;
import org.xmlobjects.stream.XMLReaderFactory;
import org.xmlobjects.util.xml.SAXBuffer;
import org.xmlobjects.util.xml.StAXStream2SAX;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.nio.file.Path;
import java.util.*;

public class GlobalObjectsReader {
    private final EnumSet<GlobalObjects.Type> types;
    private final ChunkOptions chunkOptions;

    private GlobalObjectsReader(EnumSet<GlobalObjects.Type> types) {
        this.types = types;
        chunkOptions = ChunkOptions.defaults();
    }

    public static GlobalObjectsReader defaults() {
        return new GlobalObjectsReader(EnumSet.allOf(GlobalObjects.Type.class));
    }

    public static GlobalObjectsReader of(GlobalObjects.Type... types) {
        return new GlobalObjectsReader(EnumSet.copyOf(Arrays.asList(types)));
    }

    public static GlobalObjectsReader onlyAppearances() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.APPEARANCE));
    }

    public static GlobalObjectsReader onlyCityObjectGroups() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.CITY_OBJECT_GROUP));
    }

    public static GlobalObjectsReader onlyImplicitGeometries() {
        return new GlobalObjectsReader(EnumSet.of(GlobalObjects.Type.IMPLICIT_GEOMETRY));
    }

    public GlobalObjects read(Path file, CityGMLContext context) throws ExecutionException {
        GlobalObjects globalObjects = new GlobalObjects();
        Deque<GroupProcessor> groupProcessors = new ArrayDeque<>();
        boolean checkForTopLevel = !Collections.disjoint(types, GlobalObjects.Type.TOP_LEVEL_TYPES);
        boolean isTopLevel = false;

        try (XMLReader reader = XMLReaderFactory.newInstance(context.getXMLObjects()).createReader(file)) {
            XMLStreamReader streamReader = reader.getStreamReader();
            while (streamReader.hasNext()) {
                int eventType = streamReader.next();
                if (eventType == XMLStreamConstants.START_ELEMENT) {
                    QName name = streamReader.getName();
                    if (checkForTopLevel
                            && !isTopLevel
                            && chunkOptions.containsProperty(name)) {
                        isTopLevel = true;
                    } else if (isTopLevel) {
                        if (types.contains(GlobalObjects.Type.APPEARANCE)
                                && "Appearance".equals(name.getLocalPart())
                                && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI())) {
                            globalObjects.add(reader.getObject(Appearance.class), name);
                        } else if (types.contains(GlobalObjects.Type.CITY_OBJECT_GROUP)
                                && "CityObjectGroup".equals(name.getLocalPart())
                                && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI())) {
                            CityGMLVersion version = CityGMLModules.getCityGMLVersion(name.getNamespaceURI());
                            if (version == CityGMLVersion.v3_0) {
                                globalObjects.add(reader.getObject(CityObjectGroup.class), name);
                            } else {
                                groupProcessors.push(new GroupProcessor(name));
                            }
                        }
                        isTopLevel = false;
                    } else if (types.contains(GlobalObjects.Type.IMPLICIT_GEOMETRY)
                            && "ImplicitGeometry".equals(name.getLocalPart())
                            && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI())) {
                        ImplicitGeometry implicitGeometry = reader.getObject(ImplicitGeometry.class);
                        if (implicitGeometry.getRelativeGeometry() != null
                                && implicitGeometry.getRelativeGeometry().isSetInlineObject()
                                && implicitGeometry.getRelativeGeometry().getObject().getId() != null) {
                            globalObjects.add(implicitGeometry, name);
                        }
                    }
                }

                if (!groupProcessors.isEmpty()) {
                    GroupProcessor groupProcessor = groupProcessors.peek();
                    groupProcessor.process(streamReader);
                    if (eventType == XMLStreamConstants.END_ELEMENT && groupProcessor.isComplete()) {
                        globalObjects.add(groupProcessor.build(context), groupProcessor.getName());
                        groupProcessors.pop();
                    }
                }
            }

            return globalObjects;
        } catch (Exception e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }

    private static final class GroupProcessor {
        private final QName name;
        private final StAXStream2SAX bridge = new StAXStream2SAX(new SAXBuffer());
        private final ChunkOptions groupProperties = ChunkOptions.empty().addGroupMemberProperties();

        private int depth = 0;
        private int skipFrom = Integer.MAX_VALUE;
        private boolean processEvent = true;

        GroupProcessor(QName name) {
            this.name = name;
        }

        QName getName() {
            return name;
        }

        void process(XMLStreamReader reader) throws SAXException {
            int eventType = reader.getEventType();
            if (eventType == XMLStreamConstants.START_ELEMENT) {
                depth++;
                if (groupProperties.containsProperty(reader.getName())) {
                    skipFrom = depth + 1;
                } else if (processEvent && depth > skipFrom) {
                    processEvent = false;
                }
            } else if (eventType == XMLStreamConstants.END_ELEMENT) {
                if (!processEvent && depth == skipFrom) {
                    processEvent = true;
                }
                depth--;
            }

            if (processEvent) {
                bridge.bridgeEvent(reader);
            }
        }

        boolean isComplete() {
            return depth == 0;
        }

        CityObjectGroup build(CityGMLContext context) throws CityGMLReadException {
            CityGMLInputFactory in = context.createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
            try (CityGMLReader reader = in.createCityGMLReader(
                    ((SAXBuffer) bridge.getContentHandler()).toXMLStreamReader(true))) {
                CityGMLChunk chunk = null;
                while (reader.hasNext()) {
                     chunk = reader.nextChunk();
                }

                if (chunk != null) {
                    AbstractFeature feature = chunk.build();
                    if (feature instanceof CityObjectGroup) {
                        return (CityObjectGroup) feature;
                    }
                }
            }

            throw new CityGMLReadException("Failed to read city object group.");
        }
    }
}
