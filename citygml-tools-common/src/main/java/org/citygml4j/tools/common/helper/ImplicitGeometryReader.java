/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2020 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.common.helper;

import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.builder.jaxb.unmarshal.JAXBUnmarshaller;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.common.base.ModelObject;
import org.citygml4j.model.module.Modules;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.MissingADESchemaException;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ImplicitGeometryReader {
    private final CityGMLBuilder cityGMLBuilder;
    private final XMLInputFactory in;

    public ImplicitGeometryReader(CityGMLBuilder cityGMLBuilder) {
        this.cityGMLBuilder = cityGMLBuilder;

        in = XMLInputFactory.newInstance();
        in.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
    }

    public List<ImplicitGeometry> readImplicitGeometries(Path file) throws CityGMLReadException {
        try (FileReader reader = new FileReader(file.toFile())) {
            Unmarshaller unmarshaller = cityGMLBuilder.getJAXBContext().createUnmarshaller();
            JAXBUnmarshaller jaxbUnmarshaller = cityGMLBuilder.createJAXBUnmarshaller();
            XMLStreamReader streamReader = in.createXMLStreamReader(reader);

            List<ImplicitGeometry> implicitGeometries = new ArrayList<>();
            while (streamReader.hasNext()) {
                int event = streamReader.next();

                if (event == XMLStreamConstants.START_ELEMENT
                        && streamReader.getLocalName().equals("ImplicitGeometry")
                        && Modules.isCityGMLModuleNamespace(streamReader.getNamespaceURI())) {
                    ImplicitGeometry implicitGeometry = null;

                    Object object = unmarshaller.unmarshal(streamReader);
                    if (object != null) {
                        ModelObject modelObject = jaxbUnmarshaller.unmarshal(object);
                        if (modelObject instanceof ImplicitGeometry)
                            implicitGeometry = (ImplicitGeometry) modelObject;
                    }

                    if (implicitGeometry != null)
                        implicitGeometries.add(implicitGeometry);
                    else
                        throw new CityGMLReadException("Failed to unmarshal implicit geometry element.");
                }
            }

            return implicitGeometries;
        } catch (IOException | XMLStreamException | JAXBException | CityGMLBuilderException | MissingADESchemaException e) {
            throw new CityGMLReadException("Caused by: ", e);
        }
    }
}
