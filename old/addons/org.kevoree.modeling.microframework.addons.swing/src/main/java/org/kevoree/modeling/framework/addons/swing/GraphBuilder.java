package org.kevoree.modeling.framework.addons.swing;

import org.graphstream.graph.Edge;
import org.graphstream.graph.Graph;
import org.graphstream.graph.Node;
import org.graphstream.graph.implementations.SingleGraph;
import org.kevoree.modeling.KCallback;
import org.kevoree.modeling.abs.AbstractKObject;
import org.kevoree.modeling.memory.manager.internal.KInternalDataManager;
import org.kevoree.modeling.meta.KMeta;
import org.kevoree.modeling.traversal.visitor.KModelVisitor;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.traversal.visitor.KVisitResult;
import org.kevoree.modeling.memory.chunk.KObjectChunk;
import org.kevoree.modeling.meta.KMetaRelation;

/**
 * Created by duke on 7/1/14.
 */
public class GraphBuilder {

    public static void graphFrom(KObject modelRoot, KCallback<Graph> cb) {
        final Graph graph = new SingleGraph("Model_" + modelRoot.metaClass().metaName());
        graph.setStrict(false);
        createNode(graph, modelRoot);
        modelRoot.visit(new KModelVisitor() {
            @Override
            public KVisitResult visit(KObject elem) {
                createNode(graph, elem);
                return KVisitResult.CONTINUE;
            }
        }, new KCallback<Throwable>() {
            @Override
            public void on(Throwable throwable) {
                modelRoot.visit(new KModelVisitor() {
                    @Override
                    public KVisitResult visit(KObject elem) {
                        createEdges(graph, elem);
                        return KVisitResult.CONTINUE;
                    }
                }, new KCallback<Throwable>() {
                    @Override
                    public void on(Throwable throwable) {
                        createEdges(graph, modelRoot);
                        graph.addAttribute("ui.antialias");
                        graph.addAttribute("ui.quality");
                        graph.addAttribute("ui.stylesheet", styleSheet);
                        cb.on(graph);
                    }
                });
            }
        });
    }

    private static void createNode(Graph graph, KObject elem) {
        Node n = graph.addNode(elem.uuid() + "");
        n.addAttribute("ui.label", elem.uuid() + ":" + elem.metaClass().metaName());
    }

    private static void createEdges(Graph graph, KObject elem) {
        KObjectChunk rawPayload = ((KInternalDataManager)elem.manager()).preciseChunk(elem.universe(), elem.now(), elem.uuid(), elem.metaClass(), ((AbstractKObject)elem).previousResolved());
        for (KMeta meta : elem.metaClass().metaElements()) {
            if (meta instanceof KMetaRelation) {
                KMetaRelation metaRef = (KMetaRelation) meta;
                long[] relatedElems = rawPayload.getLongArray(metaRef.index(), elem.metaClass());
                if (relatedElems != null) {
                    for (int i = 0; i < relatedElems.length; i++) {
                        Edge e = graph.addEdge(elem.uuid() + "_" + relatedElems[i] + "_" + metaRef.metaName(), elem.uuid() + "", relatedElems[i] + "");
                        if (e != null) {
                            e.addAttribute("ui.label", metaRef.metaName());
                        }
                    }
                }
            }
        }
    }

    protected static String styleSheet = "graph { padding: 100px; stroke-width: 2px; }"
            + "node { fill-color: orange;  fill-mode: dyn-plain; }"
            + "edge { fill-color: grey; }"
            + "edge .containmentReference { fill-color: blue; }"
            + "node:selected { fill-color: red;  fill-mode: dyn-plain; }"
            + "node:clicked  { fill-color: blue; fill-mode: dyn-plain; }"
            + "node .modelRoot        { fill-color: grey, yellow, purple; fill-mode: dyn-plain; }";

}
