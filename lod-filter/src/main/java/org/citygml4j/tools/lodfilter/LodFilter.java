package org.citygml4j.tools.lodfilter;

import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.util.walker.FeatureWalker;

public class LodFilter {

    public AbstractCityObject filterLod(AbstractCityObject cityObject) {

        cityObject.accept(new FeatureWalker() {
            public void visit(AbstractCityObject cityObject) {
                super.visit(cityObject);
            }
        });

        return cityObject;
    }

}
