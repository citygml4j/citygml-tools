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

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractCityObject;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.base.AbstractAssociation;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractReference;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.primitives.AbstractSurface;
import org.xmlobjects.gml.util.id.DefaultIdCreator;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GeometryCopyBuilder {
    private final AppearanceProcessor appearanceProcessor = new AppearanceProcessor();
    private final CopyBuilder copyBuilder = new CopyBuilder().failOnError(true);
    private final String ID = "id";

    private AppearanceHelper globalAppearanceHelper;
    private AppearanceHelper localAppearanceHelper;
    private boolean copyAppearance;

    private GeometryCopyBuilder() {
    }

    public static GeometryCopyBuilder newInstance() {
        return new GeometryCopyBuilder();
    }

    public GeometryCopyBuilder withGlobalAppearanceHelper(AppearanceHelper globalAppearanceHelper) {
        this.globalAppearanceHelper = globalAppearanceHelper;
        return this;
    }

    public GeometryCopyBuilder withLocalAppearanceHelper(AppearanceHelper localAppearanceHelper) {
        this.localAppearanceHelper = localAppearanceHelper;
        return this;
    }

    public GeometryCopyBuilder copyAppearance(boolean copyAppearance) {
        this.copyAppearance = copyAppearance;
        return this;
    }

    public AbstractGeometry copy(AbstractGeometry geometry, GMLObject target) {
        return copy(geometry, getTopLevelObject(target));
    }

    public AbstractGeometry copy(AbstractGeometry geometry) {
        return copy(geometry, null);
    }

    private AbstractGeometry copy(AbstractGeometry geometry, AbstractCityObject topLevelObject) {
        AbstractGeometry copy = copyBuilder.deepCopy(geometry);

        IdHelper idHelper = new IdHelper();
        idHelper.updateIds(copy, copyAppearance);

        if (copyAppearance && topLevelObject != null) {
            appearanceProcessor.process(copy, topLevelObject, idHelper);
        }

        return copy;
    }

    private AbstractCityObject getTopLevelObject(GMLObject object) {
        GMLObject topLevelObject = object;
        GMLObject parent = object;
        while ((parent = parent.getParent(AbstractCityObject.class)) != null) {
            topLevelObject = parent;
        }

        return topLevelObject instanceof AbstractCityObject ?
                (AbstractCityObject) topLevelObject :
                null;
    }

    private class AppearanceProcessor extends ObjectWalker {
        private AbstractCityObject topLevelObject;
        private IdHelper idHelper;
        private int id;

        public void process(AbstractGeometry geometry, AbstractCityObject topLevelObject, IdHelper idHelper) {
            this.topLevelObject = topLevelObject;
            this.idHelper = idHelper;
            geometry.accept(this);
        }

        @Override
        public void visit(AbstractSurface surface) {
            process(surface);
            super.visit(surface);
        }

        @Override
        public void visit(MultiSurface multiSurface) {
            process(multiSurface);
            super.visit(multiSurface);
        }

        private void process(AbstractGeometry geometry) {
            if (localAppearanceHelper != null && localAppearanceHelper.hasAppearances()) {
                process(geometry, localAppearanceHelper);
            }

            if (globalAppearanceHelper != null && globalAppearanceHelper.hasAppearances()) {
                process(geometry, globalAppearanceHelper);
            }
        }

        private void process(AbstractGeometry geometry, AppearanceHelper helper) {
            String geometryId = geometry.getLocalProperties().get(ID, String.class);

            List<TextureAssociationProperty> properties = helper.getParameterizedTextures(geometryId);
            if (properties != null) {
                for (TextureAssociationProperty property : properties) {
                    ParameterizedTexture texture = getOrCreateSurfaceData(property, ParameterizedTexture.class);
                    TextureAssociationProperty copy = copyBuilder.deepCopy(property);
                    idHelper.visit(copy);
                    texture.getTextureParameterizations().add(copy);
                }
            }

            List<GeometryReference> references = helper.getGeoreferencedTextures(geometryId);
            if (references != null) {
                for (GeometryReference reference : references) {
                    GeoreferencedTexture texture = getOrCreateSurfaceData(reference, GeoreferencedTexture.class);
                    texture.getTargets().add(new GeometryReference("#" + idHelper.getId(reference.getHref())));
                }
            }

            references = helper.getMaterials(geometryId);
            if (references != null) {
                for (GeometryReference reference : references) {
                    X3DMaterial material = getOrCreateSurfaceData(reference, X3DMaterial.class);
                    material.getTargets().add(new GeometryReference("#" + idHelper.getId(reference.getHref())));
                }
            }
        }

        private <T extends AbstractSurfaceData> T getOrCreateSurfaceData(AbstractAssociation<?> association, Class<T> type) {
            T source = association.getParent(type);

            Appearance appearance = getOrCreateAppearance(source.getParent(Appearance.class));
            for (AbstractSurfaceDataProperty property : appearance.getSurfaceData()) {
                if (type.isInstance(property.getObject())) {
                    T surfaceData = type.cast(property.getObject());
                    if (surfaceData != source
                            && surfaceData.hasLocalProperties()
                            && source.hasLocalProperties()
                            && source.getLocalProperties().getAndCompare(ID, surfaceData.getLocalProperties().get(ID))) {
                        return surfaceData;
                    }
                }
            }

            T surfaceData = copyBuilder.shallowCopy(source);
            surfaceData.setId(null);
            surfaceData.getLocalProperties().set(ID, source.getLocalProperties().getOrSet(ID, Integer.class, () -> id++));
            appearance.getSurfaceData().add(new AbstractSurfaceDataProperty(surfaceData));

            if (surfaceData instanceof ParameterizedTexture) {
                ((ParameterizedTexture) surfaceData).setTextureParameterizations(null);
            } else if (surfaceData instanceof X3DMaterial) {
                ((X3DMaterial) surfaceData).setTargets(null);
            } else if (surfaceData instanceof GeoreferencedTexture) {
                ((GeoreferencedTexture) surfaceData).setTargets(null);
            }

            return surfaceData;
        }

        private Appearance getOrCreateAppearance(Appearance source) {
            for (AbstractAppearanceProperty property : topLevelObject.getAppearances()) {
                if (property.getObject() instanceof Appearance appearance) {
                    if (appearance != source
                            && appearance.hasLocalProperties()
                            && source.hasLocalProperties()
                            && source.getLocalProperties().getAndCompare(ID, appearance.getLocalProperties().get(ID))) {
                        return appearance;
                    }
                }
            }

            Appearance appearance = copyBuilder.shallowCopy(source);
            appearance.setId(null);
            appearance.setSurfaceData(null);
            appearance.getLocalProperties().set(ID, source.getLocalProperties().getOrSet(ID, Integer.class, () -> id++));
            topLevelObject.getAppearances().add(new AbstractAppearanceProperty(appearance));

            return appearance;
        }
    }

    private class IdHelper extends ObjectWalker {
        private final Map<String, String> ids = new HashMap<>();
        private boolean keepOriginalIds;

        public void updateIds(AbstractGeometry geometry, boolean keepOriginalIds) {
            this.keepOriginalIds = keepOriginalIds;
            geometry.accept(this);
        }

        public String getId(String reference) {
            return ids.get(CityObjects.getIdFromReference(reference));
        }

        @Override
        public void visit(AbstractGML object) {
            if (object.getId() != null) {
                String id = DefaultIdCreator.getInstance().createId();
                if (keepOriginalIds) {
                    object.getLocalProperties().set(ID, object.getId());
                    ids.put(object.getId(), id);
                }

                object.setId(id);
            }
        }

        @Override
        public void visit(AbstractReference<?> reference) {
            reference.setHref("#" + getId(reference.getHref()));
        }
    }
}
