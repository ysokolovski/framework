package org.kevoree.modeling.traversal.query;

import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.KObject;

public interface KQueryEngine {

    void eval(String query, KObject[] origins, KCallback<Object[]> callback);

    void traverse(String query, KObject[] origins, KCallback<KObject[]> callback);

}