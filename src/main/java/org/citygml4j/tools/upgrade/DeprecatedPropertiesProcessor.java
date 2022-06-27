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

package org.citygml4j.tools.upgrade;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.bridge.*;
import org.citygml4j.core.model.building.AbstractBuilding;
import org.citygml4j.core.model.building.BuildingFurniture;
import org.citygml4j.core.model.building.BuildingInstallation;
import org.citygml4j.core.model.building.BuildingRoom;
import org.citygml4j.core.model.cityfurniture.CityFurniture;
import org.citygml4j.core.model.common.GeometryInfo;
import org.citygml4j.core.model.construction.AbstractFillingSurface;
import org.citygml4j.core.model.construction.RoofSurface;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.AbstractSpace;
import org.citygml4j.core.model.core.AbstractSpaceBoundaryProperty;
import org.citygml4j.core.model.core.AbstractThematicSurface;
import org.citygml4j.core.model.deprecated.bridge.*;
import org.citygml4j.core.model.deprecated.building.DeprecatedPropertiesOfAbstractBuilding;
import org.citygml4j.core.model.deprecated.building.DeprecatedPropertiesOfBuildingFurniture;
import org.citygml4j.core.model.deprecated.building.DeprecatedPropertiesOfBuildingInstallation;
import org.citygml4j.core.model.deprecated.building.DeprecatedPropertiesOfBuildingRoom;
import org.citygml4j.core.model.deprecated.cityfurniture.DeprecatedPropertiesOfCityFurniture;
import org.citygml4j.core.model.deprecated.construction.DeprecatedPropertiesOfAbstractFillingSurface;
import org.citygml4j.core.model.deprecated.core.DeprecatedPropertiesOfAbstractThematicSurface;
import org.citygml4j.core.model.deprecated.generics.DeprecatedPropertiesOfGenericOccupiedSpace;
import org.citygml4j.core.model.deprecated.transportation.DeprecatedPropertiesOfAbstractTransportationSpace;
import org.citygml4j.core.model.deprecated.tunnel.DeprecatedPropertiesOfAbstractTunnel;
import org.citygml4j.core.model.deprecated.tunnel.DeprecatedPropertiesOfHollowSpace;
import org.citygml4j.core.model.deprecated.tunnel.DeprecatedPropertiesOfTunnelFurniture;
import org.citygml4j.core.model.deprecated.tunnel.DeprecatedPropertiesOfTunnelInstallation;
import org.citygml4j.core.model.deprecated.vegetation.DeprecatedPropertiesOfPlantCover;
import org.citygml4j.core.model.deprecated.vegetation.DeprecatedPropertiesOfSolitaryVegetationObject;
import org.citygml4j.core.model.deprecated.waterbody.DeprecatedPropertiesOfWaterBody;
import org.citygml4j.core.model.generics.GenericOccupiedSpace;
import org.citygml4j.core.model.relief.AbstractReliefComponent;
import org.citygml4j.core.model.relief.AbstractReliefComponentProperty;
import org.citygml4j.core.model.relief.ReliefFeature;
import org.citygml4j.core.model.transportation.AbstractTransportationSpace;
import org.citygml4j.core.model.tunnel.AbstractTunnel;
import org.citygml4j.core.model.tunnel.HollowSpace;
import org.citygml4j.core.model.tunnel.TunnelFurniture;
import org.citygml4j.core.model.tunnel.TunnelInstallation;
import org.citygml4j.core.model.vegetation.PlantCover;
import org.citygml4j.core.model.vegetation.SolitaryVegetationObject;
import org.citygml4j.core.model.waterbody.WaterBody;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.lodfilter.LodFilter;
import org.citygml4j.tools.lodfilter.LodFilterMode;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.util.CityObjects;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSolidProperty;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurfaceProperty;
import org.xmlobjects.gml.model.geometry.primitives.*;
import org.xmlobjects.gml.util.reference.ReferenceResolver;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.Collection;

public class DeprecatedPropertiesProcessor {
    private final UpgradeOptions upgradeOptions;
    private final LodFilter lodFilter;
    private final DeprecatedPropertiesWalker deprecatedPropertiesWalker;
    private final ReferenceResolver referenceResolver;

    private DeprecatedPropertiesProcessor(UpgradeOptions upgradeOptions, LodFilter lodFilter) {
        this.upgradeOptions = upgradeOptions;
        this.lodFilter = lodFilter;
        deprecatedPropertiesWalker = new DeprecatedPropertiesWalker();
        referenceResolver = DefaultReferenceResolver.newInstance();
    }

    public static DeprecatedPropertiesProcessor newInstance(UpgradeOptions upgradeOptions) {
        return new DeprecatedPropertiesProcessor(upgradeOptions, LodFilter.newInstance()
                .withMode(LodFilterMode.REMOVE)
                .keepEmptyObjects(false)
                .collectRemovedFeatureIds(false));
    }

    public Collection<Appearance> getGlobalAppearances() {
        return lodFilter.getGlobalAppearances();
    }

    public DeprecatedPropertiesProcessor withGlobalAppearances(Collection<Appearance> globalAppearances) {
        lodFilter.withGlobalAppearances(globalAppearances);
        return this;
    }

    public void process(AbstractFeature feature) {
        if (upgradeOptions.isUseLod4AsLod3()) {
            GeometryInfo geometryInfo = feature.getGeometryInfo(true);
            if (geometryInfo.hasGeometries(4) || geometryInfo.hasImplicitGeometries(4)) {
                if (geometryInfo.hasGeometries(3) || geometryInfo.hasImplicitGeometries(3)) {
                    lodFilter.withLods(3).apply(feature);
                }
            }
        }

        referenceResolver.resolveReferences(feature);
        feature.accept(deprecatedPropertiesWalker);
        lodFilter.withLods(4).apply(feature);
    }

    private class DeprecatedPropertiesWalker extends ObjectWalker {
        private final Logger log = Logger.getInstance();
        private final CopyBuilder copyBuilder = new CopyBuilder().failOnError(true);

        @Override
        public void visit(AbstractBridge bridge) {
            if (bridge.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfAbstractBridge properties = bridge.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4MultiCurve() != null) {
                        bridge.setLod3MultiCurve(properties.getLod4MultiCurve());
                        properties.setLod4MultiCurve(null);
                    }

                    if (properties.getLod4MultiSurface() != null) {
                        bridge.setLod3MultiSurface(properties.getLod4MultiSurface());
                        properties.setLod4MultiSurface(null);
                    }

                    if (properties.getLod4Solid() != null) {
                        bridge.setLod3Solid(properties.getLod4Solid());
                        properties.setLod4Solid(null);
                    }

                    if (properties.getLod4TerrainIntersectionCurve() != null) {
                        bridge.setLod3TerrainIntersectionCurve(properties.getLod4TerrainIntersectionCurve());
                        properties.setLod4TerrainIntersectionCurve(null);
                    }
                }
            }

            super.visit(bridge);
        }

        @Override
        public void visit(AbstractBuilding building) {
            if (building.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfAbstractBuilding properties = building.getDeprecatedProperties();

                if (properties.getLod0RoofEdge() != null) {
                    RoofSurface roofSurface = new RoofSurface();
                    roofSurface.setLod0MultiSurface(properties.getLod0RoofEdge());
                    building.addBoundary(new AbstractSpaceBoundaryProperty(roofSurface));
                }

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4MultiCurve() != null) {
                        building.setLod3MultiCurve(properties.getLod4MultiCurve());
                        properties.setLod4MultiCurve(null);
                    }

                    if (properties.getLod4MultiSurface() != null) {
                        building.setLod3MultiSurface(properties.getLod4MultiSurface());
                        properties.setLod4MultiSurface(null);
                    }

                    if (properties.getLod4Solid() != null) {
                        building.setLod3Solid(properties.getLod4Solid());
                        properties.setLod4Solid(null);
                    }

                    if (properties.getLod4TerrainIntersectionCurve() != null) {
                        building.setLod3TerrainIntersectionCurve(properties.getLod4TerrainIntersectionCurve());
                        properties.setLod4TerrainIntersectionCurve(null);
                    }
                }
            }

            super.visit(building);
        }

        @Override
        public void visit(AbstractFillingSurface fillingSurface) {
            if (fillingSurface.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfAbstractFillingSurface properties = fillingSurface.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()
                        && properties.getLod4ImplicitRepresentation() != null) {
                    log.warn(CityObjects.getObjectSignature(fillingSurface) + ": " +
                            "Skipping unsupported LoD4 implicit geometry representation.");
                }
            }

            super.visit(fillingSurface);
        }

        @Override
        public void visit(AbstractThematicSurface thematicSurface) {
            if (thematicSurface.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfAbstractThematicSurface properties = thematicSurface.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()
                        && properties.getLod4MultiSurface() != null) {
                    thematicSurface.setLod3MultiSurface(properties.getLod4MultiSurface());
                    properties.setLod4MultiSurface(null);
                }
            }

            super.visit(thematicSurface);
        }

        @Override
        public void visit(AbstractTransportationSpace transportationSpace) {
            if (transportationSpace.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfAbstractTransportationSpace properties = transportationSpace.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()
                        && properties.getLod4MultiSurface() != null) {
                    transportationSpace.setLod3MultiSurface(properties.getLod4MultiSurface());
                    properties.setLod4MultiSurface(null);
                }
            }

            super.visit(transportationSpace);
        }

        @Override
        public void visit(AbstractTunnel tunnel) {
            if (tunnel.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfAbstractTunnel properties = tunnel.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4MultiCurve() != null) {
                        tunnel.setLod3MultiCurve(properties.getLod4MultiCurve());
                        properties.setLod4MultiCurve(null);
                    }

                    if (properties.getLod4MultiSurface() != null) {
                        tunnel.setLod3MultiSurface(properties.getLod4MultiSurface());
                        properties.setLod4MultiSurface(null);
                    }

                    if (properties.getLod4Solid() != null) {
                        tunnel.setLod3Solid(properties.getLod4Solid());
                        properties.setLod4Solid(null);
                    }

                    if (properties.getLod4TerrainIntersectionCurve() != null) {
                        tunnel.setLod3TerrainIntersectionCurve(properties.getLod4TerrainIntersectionCurve());
                        properties.setLod4TerrainIntersectionCurve(null);
                    }
                }
            }

            super.visit(tunnel);
        }

        @Override
        public void visit(BridgeConstructiveElement bridgeConstructiveElement) {
            if (bridgeConstructiveElement.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfBridgeConstructiveElement properties = bridgeConstructiveElement.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), bridgeConstructiveElement);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4TerrainIntersectionCurve() != null) {
                        bridgeConstructiveElement.setLod3TerrainIntersectionCurve(properties.getLod4TerrainIntersectionCurve());
                        properties.setLod4TerrainIntersectionCurve(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        bridgeConstructiveElement.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(bridgeConstructiveElement);
        }

        @Override
        public void visit(BridgeFurniture bridgeFurniture) {
            if (bridgeFurniture.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfBridgeFurniture properties = bridgeFurniture.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), bridgeFurniture);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        bridgeFurniture.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(bridgeFurniture);
        }

        @Override
        public void visit(BridgeInstallation bridgeInstallation) {
            if (bridgeInstallation.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfBridgeInstallation properties = bridgeInstallation.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), bridgeInstallation);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        bridgeInstallation.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(bridgeInstallation);
        }

        @Override
        public void visit(BridgeRoom bridgeRoom) {
            if (bridgeRoom.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfBridgeRoom properties = bridgeRoom.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Solid() != null) {
                        bridgeRoom.setLod3Solid(properties.getLod4Solid());
                        properties.setLod4Solid(null);
                    }

                    if (properties.getLod4MultiSurface() != null) {
                        bridgeRoom.setLod3MultiSurface(properties.getLod4MultiSurface());
                        properties.setLod4MultiSurface(null);
                    }
                }
            }

            super.visit(bridgeRoom);
        }

        @Override
        public void visit(BuildingFurniture buildingFurniture) {
            if (buildingFurniture.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfBuildingFurniture properties = buildingFurniture.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), buildingFurniture);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        buildingFurniture.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(buildingFurniture);
        }

        @Override
        public void visit(BuildingInstallation buildingInstallation) {
            if (buildingInstallation.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfBuildingInstallation properties = buildingInstallation.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), buildingInstallation);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        buildingInstallation.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(buildingInstallation);
        }

        @Override
        public void visit(BuildingRoom buildingRoom) {
            if (buildingRoom.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfBuildingRoom properties = buildingRoom.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Solid() != null) {
                        buildingRoom.setLod3Solid(properties.getLod4Solid());
                        properties.setLod4Solid(null);
                    }

                    if (properties.getLod4MultiSurface() != null) {
                        buildingRoom.setLod3MultiSurface(properties.getLod4MultiSurface());
                        properties.setLod4MultiSurface(null);
                    }
                }
            }

            super.visit(buildingRoom);
        }

        @Override
        public void visit(CityFurniture cityFurniture) {
            if (cityFurniture.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfCityFurniture properties = cityFurniture.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), cityFurniture);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4TerrainIntersectionCurve() != null) {
                        cityFurniture.setLod3TerrainIntersectionCurve(properties.getLod4TerrainIntersectionCurve());
                        properties.setLod4TerrainIntersectionCurve(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        cityFurniture.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(cityFurniture);
        }

        @Override
        public void visit(GenericOccupiedSpace genericOccupiedSpace) {
            if (genericOccupiedSpace.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfGenericOccupiedSpace properties = genericOccupiedSpace.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), genericOccupiedSpace);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4TerrainIntersectionCurve() != null) {
                        genericOccupiedSpace.setLod3TerrainIntersectionCurve(properties.getLod4TerrainIntersectionCurve());
                        properties.setLod4TerrainIntersectionCurve(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        genericOccupiedSpace.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(genericOccupiedSpace);
        }

        @Override
        public void visit(HollowSpace hollowSpace) {
            if (hollowSpace.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfHollowSpace properties = hollowSpace.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Solid() != null) {
                        hollowSpace.setLod3Solid(properties.getLod4Solid());
                        properties.setLod4Solid(null);
                    }

                    if (properties.getLod4MultiSurface() != null) {
                        hollowSpace.setLod3MultiSurface(properties.getLod4MultiSurface());
                        properties.setLod4MultiSurface(null);
                    }
                }
            }

            super.visit(hollowSpace);
        }

        @Override
        public void visit(PlantCover plantCover) {
            if (plantCover.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfPlantCover properties = plantCover.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4MultiSurface() != null) {
                        plantCover.setLod3MultiSurface(properties.getLod4MultiSurface());
                        properties.setLod4MultiSurface(null);
                    }

                    if (properties.getLod4MultiSolid() != null) {
                        MultiSolidProperty property = properties.getLod4MultiSolid();
                        if (property.getObject() != null && property.getObject().getSolidMember().size() == 1) {
                            plantCover.setLod3Solid(property.getObject().getSolidMember().get(0));
                        } else {
                            moveAbstractGeometry(property, plantCover);
                        }

                        properties.setLod4MultiSolid(null);
                    }
                }
            }

            super.visit(plantCover);
        }

        @Override
        public void visit(ReliefFeature reliefFeature) {
            int lod = reliefFeature.getLod();
            for (AbstractReliefComponentProperty property : reliefFeature.getReliefComponents()) {
                if (property.getObject() != null) {
                    AbstractReliefComponent reliefComponent = property.getObject();
                    if (upgradeOptions.isUseLod4AsLod3() && reliefComponent.getLod() >= 4) {
                        reliefComponent.setLod(3);
                    }

                    lod = Math.max(lod, reliefComponent.getLod());
                }
            }

            reliefFeature.setLod(Math.min(lod, 3));
            super.visit(reliefFeature);
        }

        @Override
        public void visit(SolitaryVegetationObject solitaryVegetationObject) {
            if (solitaryVegetationObject.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfSolitaryVegetationObject properties = solitaryVegetationObject.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), solitaryVegetationObject);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        solitaryVegetationObject.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(solitaryVegetationObject);
        }

        @Override
        public void visit(TunnelFurniture tunnelFurniture) {
            if (tunnelFurniture.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfTunnelFurniture properties = tunnelFurniture.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), tunnelFurniture);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        tunnelFurniture.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(tunnelFurniture);
        }

        @Override
        public void visit(TunnelInstallation tunnelInstallation) {
            if (tunnelInstallation.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfTunnelInstallation properties = tunnelInstallation.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()) {
                    if (properties.getLod4Geometry() != null) {
                        moveAbstractGeometry(properties.getLod4Geometry(), tunnelInstallation);
                        properties.setLod4Geometry(null);
                    }

                    if (properties.getLod4ImplicitRepresentation() != null) {
                        tunnelInstallation.setLod3ImplicitRepresentation(properties.getLod4ImplicitRepresentation());
                        properties.setLod4ImplicitRepresentation(null);
                    }
                }
            }

            super.visit(tunnelInstallation);
        }

        @Override
        public void visit(WaterBody waterBody) {
            if (waterBody.hasDeprecatedProperties()) {
                DeprecatedPropertiesOfWaterBody properties = waterBody.getDeprecatedProperties();

                if (upgradeOptions.isUseLod4AsLod3()
                        && properties.getLod4Solid() != null) {
                    waterBody.setLod3Solid(properties.getLod4Solid());
                    properties.setLod4Solid(null);
                }
            }

            super.visit(waterBody);
        }

        private void moveAbstractGeometry(GeometryProperty<?> property, AbstractSpace target) {
            if (property.getObject() instanceof AbstractSolid) {
                target.setLod3Solid(copy(property, new SolidProperty(), GeometryProperty.class));
            } else if (property.getObject() instanceof MultiSurface) {
                target.setLod3MultiSurface(copy(property, new MultiSurfaceProperty(), GeometryProperty.class));
            } else if (property.getObject() != null) {
                MultiSurface multiSurface = new MultiSurface();
                property.getObject().accept(new ObjectWalker() {
                    @Override
                    public void visit(AbstractSurface surface) {
                        multiSurface.getSurfaceMember().add(new SurfaceProperty(surface));
                        super.visit(surface);
                    }

                    @Override
                    public void visit(Rectangle rectangle) {
                        Polygon polygon = new Polygon();
                        polygon.setExterior(rectangle.getExterior());
                        visit((AbstractSurface) polygon);
                    }

                    @Override
                    public void visit(PolygonPatch polygonPatch) {
                        Polygon polygon = new Polygon();
                        polygon.setExterior(polygonPatch.getExterior());
                        polygon.setInterior(polygon.getInterior());
                        visit((AbstractSurface) polygon);
                    }

                    @Override
                    public void visit(Triangle triangle) {
                        Polygon polygon = new Polygon();
                        polygon.setExterior(triangle.getExterior());
                        visit((AbstractSurface) polygon);
                    }
                });

                if (multiSurface.isSetSurfaceMember()) {
                    target.setLod3MultiSurface(new MultiSurfaceProperty(multiSurface));
                }
            }
        }

        public <S extends T, D extends T, T> D copy(S src, D dest, Class<T> template) {
            return copyBuilder.shallowCopy(src, dest, template);
        }
    }
}
