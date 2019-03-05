/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.lodfilter;

import org.citygml4j.model.citygml.appearance.AbstractSurfaceData;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.appearance.AppearanceMember;
import org.citygml4j.model.citygml.appearance.AppearanceProperty;
import org.citygml4j.model.citygml.appearance.GeoreferencedTexture;
import org.citygml4j.model.citygml.appearance.ParameterizedTexture;
import org.citygml4j.model.citygml.appearance.X3DMaterial;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.common.base.ModelObject;
import org.citygml4j.model.gml.base.AbstractGML;
import org.citygml4j.model.gml.base.AssociationByRepOrRef;
import org.citygml4j.model.gml.feature.FeatureProperty;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.model.gml.geometry.aggregates.MultiSurface;
import org.citygml4j.model.gml.geometry.primitives.AbstractSurface;
import org.citygml4j.util.walker.FeatureWalker;
import org.citygml4j.util.walker.GMLWalker;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class AppearanceCleaner {

    void cleanupAppearances(AbstractCityObject cityObject, Set<String> candidates) {
        cityObject.accept(new FeatureWalker() {
            public void visit(AbstractCityObject nested) {
                if (nested.isSetAppearance()) {
                    List<Appearance> appearances = nested.getAppearance().stream()
                            .filter(AppearanceProperty::isSetAppearance)
                            .map(AppearanceProperty::getAppearance)
                            .collect(Collectors.toList());

                    cleanupAppearances(appearances, candidates);

                    cityObject.unsetAppearance();
                    for (Appearance appearance : appearances)
                        cityObject.addAppearance(new AppearanceMember(appearance));
                }

                super.visit(nested);
            }
        });
    }

    synchronized void cleanupAppearances(List<Appearance> appearances, Set<String> candidates) {
        FeatureWalker walker = new FeatureWalker() {
            public void visit(ParameterizedTexture texture) {
                if (texture.isSetTarget()) {
                    boolean removed = texture.getTarget().removeIf(t -> candidates.contains(t.getUri()));
                    if (removed && !texture.isSetTarget())
                        removeSurfaceData(texture);
                }
            }

            public void visit(GeoreferencedTexture texture) {
                if (texture.isSetTarget()) {
                    boolean removed = texture.getTarget().removeIf(candidates::contains);
                    if (removed && !texture.isSetTarget())
                        removeSurfaceData(texture);
                }
            }

            public void visit(X3DMaterial material) {
                if (material.isSetTarget()) {
                    boolean removed = material.getTarget().removeIf(candidates::contains);
                    if (removed && !material.isSetTarget())
                        removeSurfaceData(material);
                }
            }

            private void removeSurfaceData(AbstractSurfaceData surfaceData) {
                ModelObject parent = surfaceData.getParent();
                if (parent instanceof FeatureProperty<?>)
                    ((FeatureProperty<?>) parent).unsetFeature();
            }
        };

        appearances.forEach(walker::visit);

        appearances.removeIf(appearance -> {
            if (appearance.isSetSurfaceDataMember())
                appearance.getSurfaceDataMember().removeIf(property -> !property.isSetSurfaceData() && !property.isSetHref());

            return !appearance.isSetSurfaceDataMember();
        });
    }

    Set<String> getCandidateTargets(List<AssociationByRepOrRef<? extends AbstractGML>> properties) {
        Set<String> candidates = new HashSet<>();

        if (properties != null) {
            GMLWalker walker = new GMLWalker() {
                public void visit(AbstractSurface surface) {
                    addCandidate(surface);
                    super.visit(surface);
                }

                public void visit(MultiSurface surface) {
                    addCandidate(surface);
                    super.visit(surface);
                }

                private void addCandidate(AbstractGeometry geometry) {
                    if (geometry.isSetId())
                        candidates.add("#" + geometry.getId());
                }
            };

            properties.forEach(walker::visit);
        }

        return candidates;
    }

}
