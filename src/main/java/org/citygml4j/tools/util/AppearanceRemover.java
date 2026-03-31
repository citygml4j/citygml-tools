/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.primitives.AbstractSurface;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class AppearanceRemover {
    private final AppearanceHelper helper;
    private final TargetProcessor targetProcessor = new TargetProcessor();
    private final Set<String> surfaceDataIds = new HashSet<>();

    private AppearanceRemover(AppearanceHelper helper) {
        this.helper = helper;
    }

    public static AppearanceRemover of(AppearanceHelper appearanceHelper) {
        return new AppearanceRemover(appearanceHelper);
    }

    public static AppearanceRemover of(List<Appearance> appearances) {
        return of(AppearanceHelper.of(appearances));
    }

    public static AppearanceRemover of(AbstractFeature feature) {
        return of(AppearanceHelper.of(feature));
    }

    public List<Appearance> getAppearances() {
        return helper.getAppearances();
    }

    public boolean hasAppearances() {
        return helper.hasAppearances();
    }

    public void removeTarget(AbstractGeometry geometry) {
        if (helper.hasAppearances()) {
            geometry.accept(targetProcessor);
        }
    }

    public void removeTarget(AbstractInlineOrByReferenceProperty<?> property) {
        if (helper.hasAppearances()) {
            targetProcessor.visit(property);
        }
    }

    public void postprocess() {
        helper.clear();

        if (!surfaceDataIds.isEmpty()) {
            for (Appearance appearance : helper.getAppearances()) {
                appearance.getSurfaceData().removeIf(property -> property.getHref() != null
                        && surfaceDataIds.contains(
                        CityObjects.getIdFromReference(property.getHref())));
            }

            surfaceDataIds.clear();
        }

        Iterator<Appearance> iterator = helper.getAppearances().iterator();
        while (iterator.hasNext()) {
            Appearance appearance = iterator.next();
            if (!appearance.isSetSurfaceData()) {
                iterator.remove();
                if (appearance.getParent() != null && appearance.getParent().getParent() instanceof GMLObject parent) {
                    parent.unsetProperty(appearance.getParent(), true);
                }
            }
        }
    }

    private class TargetProcessor extends ObjectWalker {
        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetInlineObject()) {
                super.visit(property);
            }
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
            if (geometry.getId() != null) {
                List<TextureAssociationProperty> properties = helper.getAndRemoveParameterizedTextures(geometry.getId());
                if (properties != null) {
                    for (TextureAssociationProperty property : properties) {
                        ParameterizedTexture texture = property.getParent(ParameterizedTexture.class);
                        texture.getTextureParameterizations().remove(property);
                        if (!texture.isSetTextureParameterizations()) {
                            removeSurfaceData(texture);
                        }
                    }
                }

                List<GeometryReference> references = helper.getAndRemoveGeoreferencedTextures(geometry.getId());
                if (references != null) {
                    for (GeometryReference reference : references) {
                        GeoreferencedTexture texture = reference.getParent(GeoreferencedTexture.class);
                        texture.getTargets().remove(reference);
                        if (!texture.isSetTargets()) {
                            removeSurfaceData(texture);
                        }
                    }
                }

                references = helper.getAndRemoveMaterials(geometry.getId());
                if (references != null) {
                    for (GeometryReference reference : references) {
                        X3DMaterial material = reference.getParent(X3DMaterial.class);
                        material.getTargets().remove(reference);
                        if (!material.isSetTargets()) {
                            removeSurfaceData(material);
                        }
                    }
                }
            }
        }

        private void removeSurfaceData(AbstractSurfaceData surfaceData) {
            Appearance appearance = surfaceData.getParent(Appearance.class);
            if (appearance.getSurfaceData().remove(surfaceData.getParent(AbstractSurfaceDataProperty.class))
                    && surfaceData.getId() != null) {
                surfaceDataIds.add(surfaceData.getId());
            }
        }
    }
}
