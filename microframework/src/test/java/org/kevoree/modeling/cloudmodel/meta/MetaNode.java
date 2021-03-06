package org.kevoree.modeling.cloudmodel.meta;

import org.kevoree.modeling.extrapolation.impl.DiscreteExtrapolation;
import org.kevoree.modeling.meta.*;
import org.kevoree.modeling.meta.impl.MetaAttribute;
import org.kevoree.modeling.meta.impl.MetaClass;
import org.kevoree.modeling.meta.impl.MetaOperation;
import org.kevoree.modeling.meta.impl.MetaRelation;

public class MetaNode extends MetaClass {

    private static MetaNode INSTANCE = null;

    public static MetaNode getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MetaNode();
        }
        return INSTANCE;
    }

    public static final KMetaAttribute ATT_NAME = new MetaAttribute("name", 0, 5, true, KPrimitiveTypes.STRING.id(), DiscreteExtrapolation.instance());

    public static final KMetaAttribute ATT_VALUE = new MetaAttribute("value", 1, 5, false, KPrimitiveTypes.STRING.id(), DiscreteExtrapolation.instance());

    public static final KMetaRelation REF_CHILDREN = new MetaRelation("children", 2, true, 0, "op_children", 0, -1);

    public static final KMetaRelation REF_OP_CHILDREN = new MetaRelation("op_children", 3, true, 0, "children", 0, -1);

    public static final KMetaRelation REF_ELEMENT = new MetaRelation("element", 4, true, 1, "op_element", 0, -1);

    public static final KMetaOperation OP_TRIGGER = new MetaOperation("trigger", 5, 0, new int[]{}, -1, new boolean[]{}, false);

    public MetaNode() {
        super("org.kevoree.modeling.microframework.test.cloud.Node", 0, null, new int[]{});
        KMeta[] temp = new KMeta[6];
        temp[0] = ATT_NAME;
        temp[1] = ATT_VALUE;
        temp[2] = REF_CHILDREN;
        temp[3] = REF_OP_CHILDREN;
        temp[4] = REF_ELEMENT;
        temp[5] = OP_TRIGGER;
        init(temp);
    }

}
