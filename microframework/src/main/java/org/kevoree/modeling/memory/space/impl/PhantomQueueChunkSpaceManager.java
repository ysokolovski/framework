package org.kevoree.modeling.memory.space.impl;

import org.kevoree.modeling.KConfig;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.abs.AbstractKObject;
import org.kevoree.modeling.memory.resolver.KResolver;
import org.kevoree.modeling.memory.space.KChunkSpace;
import org.kevoree.modeling.scheduler.KScheduler;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @ignore ts
 */
public class PhantomQueueChunkSpaceManager extends AbstractCountingChunkSpaceManager implements Runnable {

    /* This is the very first GC collector for KMF, thanks Floreal :-) */

    private final ReferenceQueue<KObject> referenceQueue;
    private final AtomicReference<KObjectPhantomReference> headPhantom;

    public PhantomQueueChunkSpaceManager() {
        headPhantom = new AtomicReference<KObjectPhantomReference>();
        referenceQueue = new ReferenceQueue<KObject>();
        Thread cleanupThread = new Thread(this);
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    @Override
    public void register(KObject kobj) {
        if (_metaModel == null) {
            _metaModel = kobj.manager().model().metaModel();
        }
        KObjectPhantomReference newRef = new KObjectPhantomReference(kobj);
        do {
            newRef.next = headPhantom.get();
        } while (!headPhantom.compareAndSet(newRef.next, newRef));
        if (newRef.next != null) {
            newRef.next.previous = newRef;
        }
    }

    @Override
    public void registerAll(KObject[] kobjects) {
        for (int i = 0; i < kobjects.length; i++) {
            if (kobjects[i] != null) {
                register(kobjects[i]);
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            KObjectPhantomReference kobj;
            try {
                kobj = (KObjectPhantomReference) referenceQueue.remove();
                if (kobj.previous == null) {
                    if (!headPhantom.compareAndSet(kobj, kobj.next)) {
                        //ouch should try to remove from the previous
                        KObjectPhantomReference nextRef = kobj.next;
                        KObjectPhantomReference previousRef = kobj.previous;
                        if (nextRef != null) {
                            nextRef.previous = previousRef;
                        }
                        if (previousRef != null) {
                            previousRef.next = nextRef;
                        }
                    }
                } else {
                    KObjectPhantomReference nextRef = kobj.next;
                    KObjectPhantomReference previousRef = kobj.previous;
                    if (nextRef != null) {
                        nextRef.previous = previousRef;
                    }
                    previousRef.next = nextRef;
                }

                long[] previousResolved;
                do {
                    previousResolved = kobj.previousResolved.get();
                } while (!kobj.previousResolved.compareAndSet(previousResolved, null));
                if (previousResolved != null) {
                    unmark(previousResolved[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], previousResolved[AbstractKObject.TIME_PREVIOUS_INDEX], kobj.obj);
                    unmark(previousResolved[AbstractKObject.UNIVERSE_PREVIOUS_INDEX], KConfig.NULL_LONG, kobj.obj);
                    unmark(KConfig.NULL_LONG, KConfig.NULL_LONG, kobj.obj);
                    unmark(KConfig.NULL_LONG, KConfig.NULL_LONG, KConfig.NULL_LONG);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    class KObjectPhantomReference extends PhantomReference<KObject> {

        public long obj;
        public AtomicReference<long[]> previousResolved;
        private KObjectPhantomReference next;
        private KObjectPhantomReference previous;

        public KObjectPhantomReference(KObject referent) {
            super(referent, referenceQueue);
            this.obj = referent.uuid();
            previousResolved = ((AbstractKObject) referent).previousResolved();
        }
    }

}
