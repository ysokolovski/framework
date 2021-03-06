package org.kevoree.modeling.cloudmodel;

import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.operation.KOperationStrategy;

/**
 * Created by duke on 10/9/14.
 */
public interface Node extends KObject {

    String getName();

    Node setName(String name);

    String getValue();

    Node setValue(String name);

    Node addChildren(Node obj);

    Node removeChildren(Node obj);

    void getChildren(KCallback<Node[]> callback);

    Node addElement(Element obj);

    void getElement(KCallback<Element> obj);

    void trigger(String param, KOperationStrategy strategy, KCallback<String> callback);

}
