/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2026 Claus Nagel <claus.nagel@gmail.com>
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
import org.xmlobjects.copy.*;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.base.AbstractAssociation;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.primitives.AbstractSurface;
import org.xmlobjects.gml.util.id.DefaultIdCreator;

import java.util.List;

public class GeometryCopyBuilder {
    private static final String ID = "id";
    private final Copier copier = CopierBuilder.newInstance()
            .withCloner(GeometryProperty.class, new GeometryPropertyCloner())
            .withCloner(AbstractGeometry.class, new AbstractGeometryCloner())
            .withCloner(GeometryReference.class, new GeometryReferenceCloner())
            .withCloner(RingReference.class, new RingReferenceCloner())
            .build();

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

    public CopySession createSession() {
        return copier.createSession();
    }

    public AbstractGeometry copy(AbstractGeometry geometry, GMLObject target, CopySession session) {
        return copy(geometry, getTopLevelObject(target), session);
    }

    public AbstractGeometry copy(AbstractGeometry geometry, CopySession session) {
        return copy(geometry, null, session);
    }

    private AbstractGeometry copy(AbstractGeometry geometry, AbstractCityObject topLevelObject, CopySession session) {
        AbstractGeometry clone = copier.deepCopy(geometry, session);
        if (copyAppearance && topLevelObject != null) {
            clone.accept(new AppearanceCopier(topLevelObject, session));
        }

        return clone;
    }

    private AbstractCityObject getTopLevelObject(GMLObject object) {
        GMLObject parent = object;
        while ((parent = parent.getParent(AbstractCityObject.class)) != null) {
            object = parent;
        }

        return object instanceof AbstractCityObject ? (AbstractCityObject) object : null;
    }

    private class AppearanceCopier extends ObjectWalker {
        private final AbstractCityObject topLevelObject;
        private final CopySession session;

        AppearanceCopier(AbstractCityObject topLevelObject, CopySession session) {
            this.topLevelObject = topLevelObject;
            this.session = session;
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
                    if (!session.hasClone(property)) {
                        ParameterizedTexture texture = getOrCreateSurfaceData(property, ParameterizedTexture.class);
                        TextureAssociationProperty clone = copier.deepCopy(property, session);
                        texture.getTextureParameterizations().add(clone);
                    }
                }
            }

            List<GeometryReference> references = helper.getGeoreferencedTextures(geometryId);
            if (references != null) {
                for (GeometryReference reference : references) {
                    if (!session.hasClone(reference)) {
                        GeoreferencedTexture texture = getOrCreateSurfaceData(reference, GeoreferencedTexture.class);
                        GeometryReference clone = copier.deepCopy(reference, session);
                        texture.getTargets().add(clone);
                    }
                }
            }

            references = helper.getMaterials(geometryId);
            if (references != null) {
                for (GeometryReference reference : references) {
                    if (!session.hasClone(reference)) {
                        X3DMaterial material = getOrCreateSurfaceData(reference, X3DMaterial.class);
                        GeometryReference clone = copier.deepCopy(reference, session);
                        material.getTargets().add(clone);
                    }
                }
            }
        }

        private <T extends AbstractSurfaceData> T getOrCreateSurfaceData(AbstractAssociation<?> association, Class<T> type) {
            T source = association.getParent(type);
            T surfaceData = session.lookupClone(source, type);
            if (surfaceData != null) {
                return surfaceData;
            }

            surfaceData = copier.shallowCopy(source, session);
            surfaceData.setId(null);

            if (surfaceData instanceof ParameterizedTexture) {
                ((ParameterizedTexture) surfaceData).setTextureParameterizations(null);
            } else if (surfaceData instanceof X3DMaterial) {
                ((X3DMaterial) surfaceData).setTargets(null);
            } else if (surfaceData instanceof GeoreferencedTexture) {
                ((GeoreferencedTexture) surfaceData).setTargets(null);
            }

            Appearance appearance = getOrCreateAppearance(source.getParent(Appearance.class));
            appearance.getSurfaceData().add(new AbstractSurfaceDataProperty(surfaceData));

            return surfaceData;
        }

        private Appearance getOrCreateAppearance(Appearance source) {
            Appearance appearance = session.lookupClone(source, Appearance.class);
            if (appearance != null) {
                return appearance;
            }

            appearance = copier.shallowCopy(source, session);
            appearance.setId(null);
            appearance.setSurfaceData(null);
            topLevelObject.getAppearances().add(new AbstractAppearanceProperty(appearance));

            return appearance;
        }
    }

    @SuppressWarnings("rawtypes")
    private static class GeometryPropertyCloner extends TypeCloner<GeometryProperty> {
        @Override
        protected void deepCopy(GeometryProperty src, GeometryProperty dest, CopyContext context) {
            AbstractGeometry clone = context.lookupClone(src.getObject(), AbstractGeometry.class);
            if (clone != null) {
                dest.setReferencedObjectIfValid(clone);
                dest.setHref("#" + clone.getId());
            } else {
                clone = context.deepCopy(src.getObject());
                dest.setInlineObjectIfValid(clone);
                dest.setHref(null);
            }
        }
    }

    private static class AbstractGeometryCloner extends TypeCloner<AbstractGeometry> {
        @Override
        protected void deepCopy(AbstractGeometry src, AbstractGeometry dest, CopyContext context) {
            context.deepCopyFields(src, dest);
            dest.getLocalProperties().set(ID, dest.getId());
            dest.setId(DefaultIdCreator.getInstance().createId());
        }
    }

    private static class GeometryReferenceCloner extends TypeCloner<GeometryReference> {
        @Override
        protected void deepCopy(GeometryReference src, GeometryReference dest, CopyContext context) {
            context.deepCopyFields(src, dest);
            dest.setHref(dest.isSetReferencedObject() ?
                    "#" + dest.getReferencedObject().getId() :
                    null);
        }
    }

    private static class RingReferenceCloner extends TypeCloner<RingReference> {
        @Override
        protected void deepCopy(RingReference src, RingReference dest, CopyContext context) {
            context.deepCopyFields(src, dest);
            dest.setHref(dest.isSetReferencedObject() ?
                    "#" + dest.getReferencedObject().getId() :
                    null);
        }
    }
}
