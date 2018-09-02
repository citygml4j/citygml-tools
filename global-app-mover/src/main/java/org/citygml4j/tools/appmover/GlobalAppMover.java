package org.citygml4j.tools.appmover;

import org.citygml4j.builder.copy.CopyBuilder;
import org.citygml4j.builder.copy.ShallowCopyBuilder;
import org.citygml4j.model.citygml.appearance.AbstractSurfaceData;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.appearance.AppearanceMember;
import org.citygml4j.model.citygml.appearance.AppearanceProperty;
import org.citygml4j.model.citygml.appearance.GeoreferencedTexture;
import org.citygml4j.model.citygml.appearance.ParameterizedTexture;
import org.citygml4j.model.citygml.appearance.SurfaceDataProperty;
import org.citygml4j.model.citygml.appearance.TextureAssociation;
import org.citygml4j.model.citygml.appearance.X3DMaterial;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.citygml.core.ImplicitGeometry;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.model.gml.feature.FeatureProperty;
import org.citygml4j.model.gml.geometry.AbstractGeometry;
import org.citygml4j.util.child.ChildInfo;
import org.citygml4j.util.gmlid.DefaultGMLIdManager;
import org.citygml4j.util.walker.FeatureWalker;
import org.citygml4j.util.walker.GMLWalker;
import org.citygml4j.util.xlink.XLinkResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class GlobalAppMover {
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, List<AbstractSurfaceData>> targets;
    private final CopyBuilder copyBuilder;
    private final String ID = "id";
    private final CityModel cityModel;
    private final ResultStatistic resultStatistic;

    private LocalAppTarget localAppTarget = LocalAppTarget.TOP_LEVEL_FEATURE;

    public GlobalAppMover(List<Appearance> appearances) {
        targets = new HashMap<>();
        copyBuilder = new ShallowCopyBuilder();
        cityModel = new CityModel();
        resultStatistic = new ResultStatistic();

        initialize(appearances);
    }

    public GlobalAppMover(Appearance... appearances) {
        this(Arrays.asList(appearances));
    }

    public AbstractCityObject moveGlobalApps(AbstractCityObject cityObject) {
        ChildInfo childInfo = new ChildInfo();

        cityObject.accept(new GMLWalker() {
            @Override
            public void visit(AbstractGeometry geometry) {
                if (geometry.isSetId()) {
                    List<AbstractSurfaceData> surfaceDatas = targets.get(geometry.getId());
                    if (surfaceDatas == null)
                        return;

                    AbstractFeature target;
                    if (childInfo.getParentCityGML(geometry, ImplicitGeometry.class) != null)
                        target = cityModel;
                    else
                        target = localAppTarget == LocalAppTarget.NESTED_FEATURE ?
                                childInfo.getParentCityObject(geometry) : cityObject;

                    for (AbstractSurfaceData surfaceData : surfaceDatas) {
                        Appearance globalApp = childInfo.getParentFeature(surfaceData, Appearance.class);
                        AbstractSurfaceData copy;

                        // changes to the city model have to be synchronized
                        if (target == cityModel)
                            lock.lock();

                        try {
                            Appearance localApp = getOrCreateAppearance(target, globalApp);
                            copy = getOrCreateSurfaceData(localApp, surfaceData);

                            if (copy instanceof ParameterizedTexture) {
                                ParameterizedTexture texture = (ParameterizedTexture) surfaceData;
                                for (TextureAssociation association : texture.getTarget()) {
                                    if (association.isSetUri() &&
                                            clipGMLId(association.getUri()).equals(geometry.getId())) {
                                        ((ParameterizedTexture) copy).addTarget(association);
                                        break;
                                    }
                                }
                            } else if (copy instanceof GeoreferencedTexture)
                                ((GeoreferencedTexture) copy).addTarget("#" + geometry.getId());
                            else if (copy instanceof X3DMaterial)
                                ((X3DMaterial) copy).addTarget("#" + geometry.getId());
                        } finally {
                            if (lock.isHeldByCurrentThread())
                                lock.unlock();
                        }
                    }
                }

                super.visit(geometry);
            }
        });

        resultStatistic.increment(AbstractCityObject.class);
        return cityObject;
    }

    public LocalAppTarget getLocalAppTarget() {
        return localAppTarget;
    }

    public void setLocalAppTarget(LocalAppTarget localAppTarget) {
        this.localAppTarget = localAppTarget;
    }

    public boolean hasRemainingGlobalApps() {
        return cityModel.isSetAppearanceMember();
    }

    public List<Appearance> getRemainingGlobalApps() {
        return cityModel.getAppearanceMember().stream()
                .map(AppearanceProperty::getAppearance)
                .collect(Collectors.toList());
    }

    public ResultStatistic getResultStatistic() {
        return resultStatistic;
    }

    private Appearance getOrCreateAppearance(AbstractFeature feature, Appearance appearance) {
        boolean isCityObject = feature instanceof AbstractCityObject;
        List<? extends AppearanceProperty> properties = isCityObject ?
                ((AbstractCityObject) feature).getAppearance() : ((CityModel) feature).getAppearanceMember();

        for (AppearanceProperty property : properties) {
            if (property.isSetAppearance()
                    && appearance.getLocalProperty(ID) == property.getAppearance().getLocalProperty(ID))
                return property.getAppearance();
        }

        Appearance copy = (Appearance) appearance.copy(copyBuilder);
        copy.setId(DefaultGMLIdManager.getInstance().generateUUID());
        copy.setSurfaceDataMember(new ArrayList<>());
        copy.setLocalProperty(ID, appearance.getLocalProperty(ID));

        if (isCityObject)
            ((AbstractCityObject) feature).addAppearance(new AppearanceProperty(copy));
        else
            ((CityModel) feature).addAppearanceMember(new AppearanceMember(copy));

        resultStatistic.increment(Appearance.class);
        return copy;
    }

    private AbstractSurfaceData getOrCreateSurfaceData(Appearance appearance, AbstractSurfaceData surfaceData) {
        String id = (String) surfaceData.getLocalProperty(ID);
        for (SurfaceDataProperty property : appearance.getSurfaceDataMember()) {
            if (property.isSetSurfaceData()
                    && surfaceData.getLocalProperty(ID) == property.getSurfaceData().getLocalProperty(ID))
                return property.getSurfaceData();
        }

        AbstractSurfaceData copy = (AbstractSurfaceData) surfaceData.copy(copyBuilder);
        appearance.addSurfaceDataMember(new SurfaceDataProperty(copy));

        copy.setId(DefaultGMLIdManager.getInstance().generateUUID());
        copy.setLocalProperty(ID, id);

        if (surfaceData instanceof ParameterizedTexture)
            ((ParameterizedTexture) copy).setTarget(new ArrayList<>());
        else if (surfaceData instanceof GeoreferencedTexture)
            ((GeoreferencedTexture) copy).setTarget(new ArrayList<>());
        else if (surfaceData instanceof X3DMaterial)
            ((X3DMaterial) copy).setTarget(new ArrayList<>());

        resultStatistic.increment(surfaceData.getClass());
        return copy;
    }

    private void initialize(List<Appearance> appearances) {
        // resolve appearance xlinks
        XLinkResolver xLinkResolver = new XLinkResolver();
        for (Appearance appearance : appearances) {
            appearance.accept(new FeatureWalker() {
                @Override
                public <T extends AbstractFeature> void visit(FeatureProperty<T> property) {
                    if (property.isSetHref() && !property.isSetFeature()) {
                        for (Appearance target : appearances) {
                            if (target == appearance)
                                continue;

                            AbstractFeature feature = xLinkResolver.getFeature(property.getHref(), target);
                            if (property.getAssociableClass().isInstance(feature)) {
                                T copy = property.getAssociableClass().cast(feature.copy(copyBuilder));
                                property.setFeature(copy);
                                property.unsetHref();
                            }
                        }
                    }

                    super.visit(property);
                }
            });
        }

        // build targets map
        for (Appearance appearance : appearances) {
            appearance.setLocalProperty(ID, DefaultGMLIdManager.getInstance().generateUUID());

            appearance.accept(new FeatureWalker() {
                @Override
                public void visit(AbstractSurfaceData surfaceData) {
                    surfaceData.setLocalProperty(ID, DefaultGMLIdManager.getInstance().generateUUID());
                }

                @Override
                public void visit(ParameterizedTexture texture) {
                    for (TextureAssociation textureAssociation : texture.getTarget()) {
                        List<AbstractSurfaceData> surfaceDatas = targets.computeIfAbsent(
                                clipGMLId(textureAssociation.getUri()),
                                v -> new ArrayList<>());

                        surfaceDatas.add(texture);
                    }

                    super.visit(texture);
                }

                @Override
                public void visit(GeoreferencedTexture texture) {
                    for (String target : texture.getTarget()) {
                        List<AbstractSurfaceData> surfaceDatas = targets.computeIfAbsent(
                                clipGMLId(target),
                                v -> new ArrayList<>());

                        surfaceDatas.add(texture);
                    }

                    super.visit(texture);
                }

                @Override
                public void visit(X3DMaterial material) {
                    for (String target : material.getTarget()) {
                        List<AbstractSurfaceData> surfaceDatas = targets.computeIfAbsent(
                                clipGMLId(target),
                                v -> new ArrayList<>());

                        surfaceDatas.add(material);
                    }

                    super.visit(material);
                }
            });
        }
    }

    private String clipGMLId(String target) {
        return target.replaceAll("^.*?#+?", "");
    }
}
