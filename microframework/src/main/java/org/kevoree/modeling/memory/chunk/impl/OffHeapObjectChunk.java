package org.kevoree.modeling.memory.chunk.impl;

import org.kevoree.modeling.KConfig;
import org.kevoree.modeling.format.json.JsonString;
import org.kevoree.modeling.memory.KChunkFlags;
import org.kevoree.modeling.memory.KOffHeapChunk;
import org.kevoree.modeling.memory.chunk.KObjectChunk;
import org.kevoree.modeling.memory.space.KChunkSpace;
import org.kevoree.modeling.memory.space.KChunkTypes;
import org.kevoree.modeling.memory.space.impl.press.PressOffHeapChunkSpace;
import org.kevoree.modeling.meta.*;
import org.kevoree.modeling.meta.impl.MetaAttribute;
import org.kevoree.modeling.meta.impl.MetaRelation;
import org.kevoree.modeling.util.Base64;
import sun.misc.Unsafe;

import java.io.UnsupportedEncodingException;

/**
 * @ignore ts
 * OffHeap implementation of KObjectChunk: all fields are long (8 byte) fields:
 * http://mail.openjdk.java.net/pipermail/hotspot-compiler-dev/2015-July/018383.html
 * -
 * - Memory structure: | meta class index | counter | flags | raw |
 * -
 */
public class OffHeapObjectChunk implements KObjectChunk, KOffHeapChunk {
    private static final Unsafe UNSAFE = UnsafeUtil.getUnsafe();

    private PressOffHeapChunkSpace _space;
    private long _universe, _time, _obj;

    // native pointer to the start of the memory chunk
    private volatile long _start_address;
    //private int _allocated_segments = 0;

    // constants for off-heap memory layout
    private static final long LEN_META_CLASS_INDEX = 8;
    private static final long ATT_COUNTER_LEN = 8;
    private static final long ATT_FLAGS_LEN = 8;

    private static final long OFFSET_META_CLASS_INDEX = 0;
    private static final long OFFSET_COUNTER = OFFSET_META_CLASS_INDEX + LEN_META_CLASS_INDEX;
    private static final long OFFSET_FLAGS = OFFSET_COUNTER + ATT_COUNTER_LEN;
    private static final long OFFSET_RAW = OFFSET_FLAGS + ATT_FLAGS_LEN;

    private static final long BASE_SEGMENT_SIZE = LEN_META_CLASS_INDEX + ATT_COUNTER_LEN + ATT_FLAGS_LEN;

    private static final long BYTE = 8;

    /**
     * Creates a new OffHeapObjectChunk object.
     *
     * @param _mem_addr  memory address or -1 if a new memory space should be allocated
     * @param p_universe
     * @param p_time
     * @param p_obj
     * @param p_space
     */
    public OffHeapObjectChunk(long _mem_addr, long p_universe, long p_time, long p_obj, PressOffHeapChunkSpace p_space) {
        super();
        this._space = p_space;
        this._universe = p_universe;
        this._time = p_time;
        this._obj = p_obj;

        if (_mem_addr == -1) {
            this._start_address = UNSAFE.allocateMemory(BASE_SEGMENT_SIZE);

            UNSAFE.putLong(this._start_address + OFFSET_COUNTER, 0);
            UNSAFE.putLong(this._start_address + OFFSET_META_CLASS_INDEX, -1);
        } else {
            this._start_address = _mem_addr;
        }
    }

//    private long sizeOfRawSegment(KMetaClass p_metaClass) {
//        long rawSegment = 0;
//
//        for (int i = 0; i < p_metaClass.metaElements().length; i++) {
//            KMeta meta = p_metaClass.metaElements()[i];
//            rawSegment += sizeOf(meta.index(), p_metaClass);
//        }
//        return rawSegment;
//    }

//    private int sizeOf(int p_index, KMetaClass p_metaClass) {
//        KMeta meta = p_metaClass.meta(p_index);
//
//        int size = 0;
//        if (meta.metaType().equals(MetaType.ATTRIBUTE)) {
//            KMetaAttribute metaAttribute = (KMetaAttribute) meta;
//            int attributeTypeId = metaAttribute.attributeTypeId();
//            switch (attributeTypeId) {
//                case KPrimitiveTypes.STRING_ID:
//                    size = 8; // reserve space for a native pointer
//                    break;
//                case KPrimitiveTypes.LONG_ID:
//                    size = 8;
//                    break;
//                case KPrimitiveTypes.INT_ID:
//                    size = 4;
//                    break;
//                case KPrimitiveTypes.BOOL_ID:
//                    size = 1;
//                    break;
//                case KPrimitiveTypes.DOUBLE_ID:
//                    size = 8;
//                    break;
//                case KPrimitiveTypes.CONTINUOUS_ID:
//                    size = 8; // native pointer to the double[]
//                    break;
//                default:
//                    size = 4;
//                    break;
//            }
//        } else if (meta.metaType().equals(MetaType.RELATION)) {
//            size = 8;
//        }
//
//        return size;
//    }

    private long rawPointerForIndex(int p_index, KMetaClass p_metaClass) {
        int offset = 0;
        for (int i = 0; i < p_metaClass.metaElements().length; i++) {
            KMeta meta = p_metaClass.metaElements()[i];

            if (meta.index() < p_index) {
                if (meta.metaType().equals(MetaType.ATTRIBUTE) || meta.metaType().equals(MetaType.RELATION)) {
                    offset += 8;
                }
            }
        }
        return _start_address + OFFSET_RAW + offset;
    }

    @Override
    public final KObjectChunk clone(long p_universe, long p_time, long p_obj, KMetaModel p_metaModel) {
        // TODO for now it is a deep copy, in the future a shallow copy would be more efficient (attention for the free)
        KMetaClass metaClass = p_metaModel.metaClass(UNSAFE.getInt(_start_address + OFFSET_META_CLASS_INDEX));

        OffHeapObjectChunk clonedEntry = new OffHeapObjectChunk(this._start_address, p_universe, p_time, p_obj, this._space);
        long baseSegment = BASE_SEGMENT_SIZE;
        //int modifiedIndexSegment = metaClass.metaElements().length;
        long rawSegment = metaClass.metaElements().length * 8;
//        int cloneBytes = baseSegment + modifiedIndexSegment + rawSegment;
        long cloneBytes = baseSegment + rawSegment;

        long _clone_start_address = UNSAFE.allocateMemory(cloneBytes);
//        clonedEntry._allocated_segments++;
        clonedEntry._start_address = _clone_start_address;
        UNSAFE.copyMemory(this._start_address, clonedEntry._start_address, cloneBytes);

        // strings and references
        for (int i = 0; i < metaClass.metaElements().length; i++) {
            KMeta meta = metaClass.metaElements()[i];
            if (meta.metaType().equals(MetaType.ATTRIBUTE)) {
                KMetaAttribute metaAttribute = (KMetaAttribute) meta;
                if (metaAttribute.attributeTypeId() == KPrimitiveTypes.STRING_ID) {
                    long clone_ptr = clonedEntry.rawPointerForIndex(metaAttribute.index(), metaClass);
                    if (UNSAFE.getLong(clone_ptr) != 0) {
                        long clone_ptr_str_segment = UNSAFE.getLong(clone_ptr);
                        if (clone_ptr_str_segment != 0) {
                            // copy the chunk
                            long str_size = UNSAFE.getLong(clone_ptr_str_segment);
                            long bytes = 4 + str_size * BYTE;
                            long new_ref_segment = UNSAFE.allocateMemory(bytes);
//                            clonedEntry._allocated_segments++;
                            UNSAFE.copyMemory(clone_ptr_str_segment, new_ref_segment, bytes);

                            UNSAFE.putLong(clone_ptr, new_ref_segment); // update ptr
                        }
                    }
                }
                if (metaAttribute.attributeTypeId() == KPrimitiveTypes.CONTINUOUS_ID) {
                    long clone_ptr = clonedEntry.rawPointerForIndex(metaAttribute.index(), metaClass);
                    if (UNSAFE.getLong(clone_ptr) != 0) {
                        long clone_ptr_str_segment = UNSAFE.getLong(clone_ptr);
                        if (clone_ptr_str_segment != 0) {
                            // copy the chunk
                            long str_size = UNSAFE.getLong(clone_ptr_str_segment);
                            long bytes = 4 + str_size * BYTE;
                            long new_ref_segment = UNSAFE.allocateMemory(bytes);
//                            clonedEntry._allocated_segments++;
                            UNSAFE.copyMemory(clone_ptr_str_segment, new_ref_segment, bytes);
                            UNSAFE.putLong(clone_ptr, new_ref_segment); // update ptr
                        }
                    }
                }

            } else if (meta.metaType().equals(MetaType.RELATION)) {
                KMetaRelation metaReference = (KMetaRelation) meta;
                long clone_ptr = clonedEntry.rawPointerForIndex(metaReference.index(), metaClass);
                if (UNSAFE.getLong(clone_ptr) != 0) {
                    long clone_ptr_ref_segment = UNSAFE.getLong(clone_ptr);
                    if (clone_ptr_ref_segment != 0) {
                        // copy the chunk
                        long size = UNSAFE.getLong(clone_ptr_ref_segment);
                        long bytes = 4 + size * BYTE;
                        long new_ref_segment = UNSAFE.allocateMemory(bytes);
//                        clonedEntry._allocated_segments++;
                        UNSAFE.copyMemory(clone_ptr_ref_segment, new_ref_segment, bytes);
                        UNSAFE.putLong(clone_ptr, new_ref_segment); // update ptr
                    }
                }
            }
        }

        // dirty
//        clonedEntry.setDirty();

        return clonedEntry;
    }

    private void setDirty() {
        if (_space != null) {
            if ((UNSAFE.getLong(this._start_address + OFFSET_FLAGS) & KChunkFlags.DIRTY_BIT) == KChunkFlags.DIRTY_BIT) {
                //the synchronization risk is minimal here, at worse the object will be saved twice for the next iteration
                setFlags(KChunkFlags.DIRTY_BIT, 0);
                _space.declareDirty(this);
            }
        } else {
            setFlags(KChunkFlags.DIRTY_BIT, 0);
        }
    }


    @Override
    public final void setPrimitiveType(int p_index, Object p_content, KMetaClass p_metaClass) {
        internal_setPrimitiveType(p_index, p_content, p_metaClass, true);
    }

    private void internal_setPrimitiveType(int p_index, Object p_content, KMetaClass p_metaClass, boolean p_setDirty) {
        try {
            MetaType type = p_metaClass.meta(p_index).metaType();
            long ptr = rawPointerForIndex(p_index, p_metaClass);

            // primitive types
            if (type.equals(MetaType.ATTRIBUTE)) {

                if (p_content instanceof String) {
                    String s = (String) p_content;
                    int size = s.length();
                    long newSegment = UNSAFE.allocateMemory(4 + size * BYTE); // size + the actual string
//                    _allocated_segments++;
                    byte[] bytes = s.getBytes("UTF-8");
                    UNSAFE.putInt(newSegment, size);
                    for (int i = 0; i < bytes.length; i++) {
                        UNSAFE.putByte(newSegment + 4 + i * BYTE, bytes[i]);
                    }
                    UNSAFE.putLong(ptr, newSegment);

                } else if (p_content instanceof Long) {
                    UNSAFE.putLong(ptr, (Long) p_content);
                } else if (p_content instanceof Integer) {
                    UNSAFE.putInt(ptr, (Integer) p_content);
                } else if (p_content instanceof Boolean) {
                    UNSAFE.putByte(ptr, (byte) (((boolean) p_content) ? 1 : 0));
                } else if (p_content instanceof Short) {
                    UNSAFE.putShort(ptr, (Short) p_content);
                } else if (p_content instanceof Double) {
                    UNSAFE.putDouble(ptr, (Double) p_content);
                } else if (p_content instanceof Float) {
                    UNSAFE.putFloat(ptr, (Float) p_content);
                }

                if (p_setDirty) {
                    setDirty();
                }
            }

        } catch (
                UnsupportedEncodingException e
                )

        {
            throw new RuntimeException(e);
        }

    }

    @Override
    public final long[] getLongArray(int p_index, KMetaClass p_metaClass) {
        long[] result = null;

        KMeta meta = p_metaClass.meta(p_index);
        long ptr = rawPointerForIndex(p_index, p_metaClass);

        if (meta.metaType().equals(MetaType.RELATION)) {
            long ptr_ref_segment = UNSAFE.getLong(ptr);
            if (ptr_ref_segment != 0) {
                int size = UNSAFE.getInt(ptr_ref_segment);
                result = new long[size];
                for (int i = 0; i < size; i++) {
                    result[i] = UNSAFE.getLong(ptr_ref_segment + 4 + i * BYTE);
                }
            }
        }

        return result;
    }

    @Override
    public final boolean addLongToArray(int p_index, long p_newRef, KMetaClass p_metaClass) {
        return internal_addLongToArray(p_index, p_newRef, p_metaClass, true);
    }

    boolean internal_addLongToArray(int p_index, long p_newRef, KMetaClass p_metaClass, boolean p_setDirty) {
        boolean result = false;

        KMeta meta = p_metaClass.meta(p_index);
        long ptr = rawPointerForIndex(p_index, p_metaClass);

        if (meta.metaType().equals(MetaType.RELATION)) {
            long ptr_ref_segment = UNSAFE.getLong(ptr);
            long new_ref_ptr;
            if (ptr_ref_segment != 0) {
                int newSize = UNSAFE.getInt(ptr_ref_segment) + 1;
                new_ref_ptr = UNSAFE.reallocateMemory(ptr_ref_segment, 4 + newSize * BYTE);
                UNSAFE.putInt(new_ref_ptr, newSize); // size
                UNSAFE.putLong(new_ref_ptr + 4 + (newSize - 1) * BYTE, p_newRef); // content

            } else {
                new_ref_ptr = UNSAFE.allocateMemory(4 + 8);
//                _allocated_segments++;
                UNSAFE.putInt(new_ref_ptr, 1); // size
                UNSAFE.putLong(new_ref_ptr + 4, p_newRef); // content
            }
            UNSAFE.putLong(ptr, new_ref_ptr); // update ptr

            if (p_setDirty) {
                setDirty();
            }

            result = true;
        }
        return result;
    }

    @Override
    public final boolean removeLongToArray(int p_index, long p_ref, KMetaClass p_metaClass) {
        boolean result = false;

        KMeta meta = p_metaClass.meta(p_index);
        long ptr = rawPointerForIndex(p_index, p_metaClass);

        if (meta.metaType().equals(MetaType.RELATION)) {
            long ptr_ref_segment = UNSAFE.getLong(ptr);
            if (ptr_ref_segment != 0) {
                int size = UNSAFE.getInt(ptr_ref_segment);
                if (size > 1) {
                    long new_ref_ptr = UNSAFE.allocateMemory((size - 1) * BYTE);
//                    _allocated_segments++;
                    int j = 0;
                    for (int i = 0; i < size; i++) {
                        long value = UNSAFE.getLong(ptr_ref_segment + 4 + i * BYTE);
                        if (value != p_ref) {
                            UNSAFE.putLong(new_ref_ptr + 4 + j * BYTE, value);
                            j++;
                        }
                    }
                    UNSAFE.putInt(new_ref_ptr, j); // setPrimitiveType the new size
                    UNSAFE.freeMemory(ptr_ref_segment); // release the old memory zone
//                    _allocated_segments--;
                    UNSAFE.putLong(ptr, new_ref_ptr); // update pointer

                } else {
                    UNSAFE.freeMemory(ptr_ref_segment); // release the old memory zone
//                    _allocated_segments--;
                    UNSAFE.putLong(ptr, 0);
                }
                setDirty();
                result = true;
            }
        }

        return result;
    }

    @Override
    public final void clearLongArray(int p_index, KMetaClass p_metaClass) {
        KMeta meta = p_metaClass.meta(p_index);
        long ptr = rawPointerForIndex(p_index, p_metaClass);

        if (meta.metaType().equals(MetaType.RELATION)) {
            long ptr_ref_segment = UNSAFE.getLong(ptr);
            if (ptr_ref_segment != 0) {
                UNSAFE.freeMemory(ptr_ref_segment);
//                _allocated_segments--;

                UNSAFE.putLong(ptr, 0);
            }
        }
    }

    @Override
    public final double[] getDoubleArray(int p_index, KMetaClass p_metaClass) {
        double[] infer = null;
        long ptr = rawPointerForIndex(p_index, p_metaClass);
        long ptr_segment = UNSAFE.getLong(ptr);
        if (ptr_segment != 0) {
            int size = UNSAFE.getInt(ptr_segment);
            infer = new double[size];
            for (int i = 0; i < size; i++) {
                infer[i] = UNSAFE.getDouble(ptr_segment + 4 + i * BYTE);
            }
        }
        return infer;
    }

    @Override
    public final int getDoubleArraySize(int p_index, KMetaClass p_metaClass) {
        int size = 0;
        double[] infer = getDoubleArray(p_index, p_metaClass);
        if (infer != null) {
            size = infer.length;
        }
        return size;
    }

    @Override
    public final double getDoubleArrayElem(int p_index, int p_arrayIndex, KMetaClass p_metaClass) {
        return getDoubleArray(p_index, p_metaClass)[p_arrayIndex];
    }

    @Override
    public final void setDoubleArrayElem(int p_index, int p_arrayIndex, double p_valueToInsert, KMetaClass p_metaClass) {
        internal_setDoubleArrayElem(p_index, p_arrayIndex, p_valueToInsert, p_metaClass, true);
    }

    private void internal_setDoubleArrayElem(int p_index, int p_arrayIndex, double valueToInsert, KMetaClass p_metaClass, boolean p_setDirty) {
        long ptr = rawPointerForIndex(p_index, p_metaClass);
        long ptr_segment = UNSAFE.getLong(ptr);

        if (ptr_segment == 0) {
            throw new IndexOutOfBoundsException();
        }
        int size = UNSAFE.getInt(ptr_segment);
        if (p_index > size) {
            throw new IndexOutOfBoundsException();
        }

        UNSAFE.putDouble(ptr_segment + 4 + p_arrayIndex * BYTE, valueToInsert);
        if (p_setDirty) {
            setDirty();
        }
    }

    @Override
    public final void extendDoubleArray(int p_index, int p_newSize, KMetaClass p_metaClass) {
        internal_extendDoubleArray(p_index, p_newSize, p_metaClass, true);
    }

    @Override
    public void clearDoubleArray(int p_index, KMetaClass p_metaClass) {
        KMeta meta = p_metaClass.meta(p_index);
        //TODO check for INFER, INPUT, OUTPUT, DEPENDENCY
        long ptr = rawPointerForIndex(p_index, p_metaClass);
        long ptr_ref_segment = UNSAFE.getLong(ptr);
        if (ptr_ref_segment != 0) {
            UNSAFE.freeMemory(ptr_ref_segment);
            UNSAFE.putLong(ptr, 0);
        }
    }

    private void internal_extendDoubleArray(int p_index, int p_newSize, KMetaClass p_metaClass, boolean p_setDirty) {
        long ptr = rawPointerForIndex(p_index, p_metaClass);
        long ptr_segment = UNSAFE.getLong(ptr);

        long new_ptr_segment;
        if (ptr_segment != 0) {
            new_ptr_segment = UNSAFE.reallocateMemory(ptr_segment, 4 + p_newSize * BYTE);
        } else {
            new_ptr_segment = UNSAFE.allocateMemory(4 + p_newSize * BYTE);
//            _allocated_segments++;
        }
        UNSAFE.putInt(new_ptr_segment, p_newSize); // update size
        UNSAFE.putLong(ptr, new_ptr_segment); // update pointer

        if (p_setDirty) {
            setDirty();
        }
    }

    @Override
    public final Object getPrimitiveType(int p_index, KMetaClass p_metaClass) {
        Object result = null;

        try {
            KMeta meta = p_metaClass.meta(p_index);
            long ptr = rawPointerForIndex(p_index, p_metaClass);

            if (meta.metaType().equals(MetaType.ATTRIBUTE)) {
                KMetaAttribute metaAttribute = (KMetaAttribute) meta;
                if (metaAttribute.attributeTypeId() == KPrimitiveTypes.STRING_ID) {
                    long ptr_str_segment = UNSAFE.getLong(ptr);
                    if (ptr_str_segment != 0) {
                        int size = UNSAFE.getInt(ptr_str_segment);
                        byte[] bytes = new byte[size];
                        for (int i = 0; i < size; i++) {
                            bytes[i] = UNSAFE.getByte(ptr_str_segment + 4 + i * BYTE);
                        }
                        result = new String(bytes, "UTF-8");
                    }

                } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.LONG_ID) {
                    result = UNSAFE.getLong(ptr);
                } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.INT_ID) {
                    result = UNSAFE.getInt(ptr);
                } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.BOOL_ID) {
                    result = UNSAFE.getByte(ptr) != 0;
                } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.DOUBLE_ID) {
                    result = UNSAFE.getDouble(ptr);
                } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.CONTINUOUS_ID) {
                    result = getDoubleArray(p_index, p_metaClass);
                }
            }

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private final void initMetaClass(KMetaClass p_metaClass) {
        long baseSegment = BASE_SEGMENT_SIZE;
        long modifiedIndexSegment = p_metaClass.metaElements().length;
        long rawSegment = p_metaClass.metaElements().length * 8;

        long bytes = baseSegment + modifiedIndexSegment + rawSegment;

        _start_address = UNSAFE.allocateMemory(bytes);
//        _allocated_segments++;
        UNSAFE.setMemory(_start_address, bytes, (byte) 0);
        UNSAFE.putInt(_start_address + OFFSET_META_CLASS_INDEX, p_metaClass.index());

        if (this._space != null) {
            this._space.notifyRealloc(_start_address, this._universe, this._time, this._obj);
        }
    }


    @Override
    public final int metaClassIndex() {
        return UNSAFE.getInt(_start_address + OFFSET_META_CLASS_INDEX);
    }


    @Override
    public final String toJSON(KMetaModel p_metaModel) {
        KMetaClass metaClass = p_metaModel.metaClass(metaClassIndex());
        StringBuilder builder = new StringBuilder();
        builder.append("{");
        boolean isFirst = true;
        KMeta[] metaElements = metaClass.metaElements();
        if (_start_address != 0 && metaElements != null) {
            for (int i = 0; i < metaElements.length; i++) {
                KMeta meta = metaElements[i];
                if (meta.metaType().equals(MetaType.ATTRIBUTE)) {
                    MetaAttribute metaAttribute = (MetaAttribute) meta;
                    if (metaAttribute.attributeTypeId() != KPrimitiveTypes.CONTINUOUS_ID) {
                        Object o = getPrimitiveType(meta.index(), metaClass);
                        if (o != null) {
                            if (isFirst) {
                                builder.append("\"");
                                isFirst = false;
                            } else {
                                builder.append(",\"");
                            }
                            builder.append(metaAttribute.metaName());
                            builder.append("\":");

                            if (o instanceof String) {
                                builder.append("\"");
                                builder.append(JsonString.encode((String) o));
                                builder.append("\"");
                            } else {
                                builder.append(o.toString());
                            }
                        }

                    } else {
                        double[] o = getDoubleArray(meta.index(), metaClass);
                        if (o != null) {
                            builder.append(",\"");
                            builder.append(metaAttribute.metaName());
                            builder.append("\":");

                            builder.append("[");
                            double[] castedArr = (double[]) o;
                            for (int j = 0; j < castedArr.length; j++) {
                                if (j != 0) {
                                    builder.append(",");
                                }
                                builder.append(castedArr[j]);
                            }
                            builder.append("]");
                        }
                    }

                } else if (meta.metaType().equals(MetaType.RELATION)) {
                    MetaRelation metaReference = (MetaRelation) meta;
                    long[] o = getLongArray(metaReference.index(), metaClass);
                    if (o != null) {
                        builder.append(",\"");
                        builder.append(metaElements[i].metaName());
                        builder.append("\":");

                        builder.append("[");
                        long[] castedArr = (long[]) o;
                        for (int j = 0; j < castedArr.length; j++) {
                            if (j != 0) {
                                builder.append(",");
                            }
                            builder.append(castedArr[j]);
                        }
                        builder.append("]");
                    }
                }
            }
        }
        builder.append("}");
        return builder.toString();
    }

    @Override
    public String serialize(KMetaModel p_metaModel) {
        KMetaClass metaClass = p_metaModel.metaClass(metaClassIndex());
        StringBuilder builder = new StringBuilder();
        boolean isFirst = true;
        KMeta[] metaElements = metaClass.metaElements();
        if (_start_address != 0 && metaElements != null) {
            for (int i = 0; i < metaElements.length; i++) {
                KMeta meta = metaElements[i];
                if (metaElements[i].metaType() == MetaType.ATTRIBUTE) {
                    KMetaAttribute metaAttribute = (KMetaAttribute) metaElements[i];
                    Object o = getPrimitiveType(meta.index(), metaClass);
                    if (o != null) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            builder.append(KConfig.CHUNK_ELEM_SEP);
                        }
                        Base64.encodeStringToBuffer(metaElements[i].metaName(), builder);
                        builder.append(KConfig.CHUNK_VAL_SEP);
                        if (metaAttribute.attributeTypeId() == KPrimitiveTypes.STRING_ID) {
                            Base64.encodeStringToBuffer((String) o, builder);
                        } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.LONG_ID) {
                            Base64.encodeLongToBuffer((long) o, builder);
                        } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.CONTINUOUS_ID) {
                            double[] castedArr = (double[]) o;
                            Base64.encodeIntToBuffer(castedArr.length, builder);
                            for (int j = 0; j < castedArr.length; j++) {
                                builder.append(KConfig.CHUNK_VAL_SEP);
                                Base64.encodeDoubleToBuffer(castedArr[j], builder);
                            }
                        } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.BOOL_ID) {
                            if ((boolean) o) {
                                builder.append("1");
                            } else {
                                builder.append("0");
                            }
                        } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.DOUBLE_ID) {
                            Base64.encodeDoubleToBuffer((double) o, builder);
                        } else if (metaAttribute.attributeTypeId() == KPrimitiveTypes.INT_ID) {
                            Base64.encodeIntToBuffer((int) o, builder);
                        } else if (KPrimitiveTypes.isEnum(metaAttribute.attributeTypeId())) {
                            Base64.encodeIntToBuffer((int) o, builder);
                        }
                    }
                } else if (metaElements[i].metaType() == MetaType.RELATION) {
                    long[] o = getLongArray(meta.index(), metaClass);
                    if (o != null) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            builder.append(KConfig.CHUNK_ELEM_SEP);
                        }
                        Base64.encodeStringToBuffer(metaElements[i].metaName(), builder);
                        builder.append(KConfig.CHUNK_VAL_SEP);
                        Base64.encodeIntToBuffer(o.length, builder);
                        for (int j = 0; j < o.length; j++) {
                            builder.append(KConfig.CHUNK_VAL_SEP);
                            Base64.encodeLongToBuffer(o[j], builder);
                        }
                    }
                } else if (metaElements[i].metaType() == MetaType.DEPENDENCIES || metaElements[i].metaType() == MetaType.INPUT || metaElements[i].metaType() == MetaType.OUTPUT) {
                    double[] o = getDoubleArray(meta.index(), metaClass);
                    if (o != null) {
                        if (isFirst) {
                            isFirst = false;
                        } else {
                            builder.append(KConfig.CHUNK_ELEM_SEP);
                        }
                        Base64.encodeStringToBuffer(metaElements[i].metaName(), builder);
                        builder.append(KConfig.CHUNK_VAL_SEP);
                        Base64.encodeIntToBuffer(o.length, builder);
                        for (int j = 0; j < o.length; j++) {
                            builder.append(KConfig.CHUNK_VAL_SEP);
                            Base64.encodeDoubleToBuffer(o[j], builder);
                        }
                    }
                }
            }
        }
        return builder.toString();
    }

    private final Object loadObject(KMetaAttribute metaAttribute, String p_payload, int p_start, int p_end) {
        int metaAttId = metaAttribute.attributeTypeId();
        switch (metaAttId) {
            case KPrimitiveTypes.STRING_ID:
                return Base64.decodeToStringWithBounds(p_payload, p_start, p_end);
            case KPrimitiveTypes.LONG_ID:
                return Base64.decodeToLongWithBounds(p_payload, p_start, p_end);
            case KPrimitiveTypes.INT_ID:
                return Base64.decodeToIntWithBounds(p_payload, p_start, p_end);
            case KPrimitiveTypes.BOOL_ID:
                if (p_payload.charAt(p_start) == '1') {
                    return true;
                } else {
                    return false;
                }
            case KPrimitiveTypes.DOUBLE_ID:
                return Base64.decodeToDoubleWithBounds(p_payload, p_start, p_end);
            default:
                return null;
        }
    }

    @Override
    public final void init(String p_payload, KMetaModel p_metaModel, int p_metaClassIndex) {
        // check if we have an old value stored (init not called for the first time)
//        if (this._start_address != -1 && p_metaClassIndex == -1) {
//            p_metaClassIndex = UNSAFE.getInt(this._start_address + OFFSET_META_CLASS_INDEX);
//        }
//
//        if (p_metaClassIndex == -1) {
//            return;
//        }

        if (UNSAFE.getInt(this._start_address + OFFSET_META_CLASS_INDEX) == -1) {
            UNSAFE.putInt(this._start_address + OFFSET_META_CLASS_INDEX, p_metaClassIndex);
        }
        if (UNSAFE.getInt(this._start_address + OFFSET_META_CLASS_INDEX) == -1) {
            return;
        }

        if (p_payload != null) {
            KMetaClass metaClass = p_metaModel.metaClass(p_metaClassIndex);
            initMetaClass(metaClass);
            UNSAFE.putInt(_start_address + OFFSET_META_CLASS_INDEX, p_metaClassIndex);

            int i = 0;
            final int payloadSize = p_payload.length();
            KMeta previousMeta = null;
            int previousValStart = 0;
            double[] doubleArray = null;
            long[] longArray = null;
            int currentArrayIndex = -1;
            while (i < payloadSize) {
                if (p_payload.charAt(i) == KConfig.CHUNK_ELEM_SEP) {
                    if (previousMeta != null) {
                        if (previousMeta.metaType().equals(MetaType.ATTRIBUTE) && ((KMetaAttribute) previousMeta).attributeTypeId() != KPrimitiveTypes.CONTINUOUS_ID) {
                            internal_setPrimitiveType(previousMeta.index(), loadObject((KMetaAttribute) previousMeta, p_payload, previousValStart, i), metaClass, false);
                        } else if (previousMeta.metaType().equals(MetaType.RELATION) && longArray != null) {
                            longArray[currentArrayIndex] = Base64.decodeToLongWithBounds(p_payload, previousValStart, i);
                            for (int k = 0; k < longArray.length; k++) {
                                internal_addLongToArray(previousMeta.index(), longArray[k], metaClass, false);
                            }
                            longArray = null;
                        } else if (doubleArray != null) {
                            doubleArray[currentArrayIndex] = Base64.decodeToDoubleWithBounds(p_payload, previousValStart, i);
                            internal_extendDoubleArray(previousMeta.index(), doubleArray.length, metaClass, false);
                            for (int k = 0; k < doubleArray.length; k++) {
                                internal_setDoubleArrayElem(previousMeta.index(), k, doubleArray[k], metaClass, false);
                            }
                            doubleArray = null;
                        }
                    }
                    previousMeta = null;
                    previousValStart = i + 1;
                } else if (p_payload.charAt(i) == KConfig.CHUNK_VAL_SEP) {
                    if (previousMeta == null) {
                        previousMeta = metaClass.metaByName(Base64.decodeToStringWithBounds(p_payload, previousValStart, i));
                    } else {
                        if (previousMeta.metaType().equals(MetaType.RELATION)) {
                            if (longArray == null) {
                                longArray = new long[Base64.decodeToIntWithBounds(p_payload, previousValStart, i)];
                                currentArrayIndex = 0;
                            } else {
                                longArray[currentArrayIndex] = Base64.decodeToLongWithBounds(p_payload, previousValStart, i);
                                currentArrayIndex++;
                            }
                        } else {
                            //DEPENDENCY, INPUT or OUTPUT, or ATT CONTINUOUS , => double[]
                            if (doubleArray == null) {
                                doubleArray = new double[Base64.decodeToIntWithBounds(p_payload, previousValStart, i)];
                                currentArrayIndex = 0;
                            } else {
                                doubleArray[currentArrayIndex] = Base64.decodeToDoubleWithBounds(p_payload, previousValStart, i);
                                currentArrayIndex++;
                            }
                        }
                    }
                    previousValStart = i + 1;
                }
                i++;
            }
            if (previousMeta != null) {
                if (previousMeta.metaType().equals(MetaType.ATTRIBUTE) && ((KMetaAttribute) previousMeta).attributeTypeId() != KPrimitiveTypes.CONTINUOUS_ID) {
                    internal_setPrimitiveType(previousMeta.index(), loadObject((KMetaAttribute) previousMeta, p_payload, previousValStart, i), metaClass, false);
                } else if (previousMeta.metaType().equals(MetaType.RELATION) && longArray != null) {
                    longArray[currentArrayIndex] = Base64.decodeToLongWithBounds(p_payload, previousValStart, i);
                    for (int k = 0; k < longArray.length; k++) {
                        internal_addLongToArray(previousMeta.index(), longArray[k], metaClass, false);
                    }
                } else if (doubleArray != null) {
                    doubleArray[currentArrayIndex] = Base64.decodeToDoubleWithBounds(p_payload, previousValStart, i);
                    internal_extendDoubleArray(previousMeta.index(), doubleArray.length, metaClass, false);
                    for (int k = 0; k < doubleArray.length; k++) {
                        internal_setDoubleArrayElem(previousMeta.index(), k, doubleArray[k], metaClass, false);
                    }
                }
            }

        }


        // should not be dirty  after unserialization
//        UNSAFE.putByte(_start_address + OFFSET_DIRTY, (byte) 0);

    }

    @Override
    public final int counter() {
        return UNSAFE.getInt(_start_address + OFFSET_COUNTER);
    }

    @Override
    public final int inc() {
        return UNSAFE.getAndAddInt(null, this._start_address + OFFSET_COUNTER, +1) + 1;
    }

    @Override
    public final int dec() {
        return UNSAFE.getAndAddInt(null, this._start_address + OFFSET_COUNTER, -1) - 1;
    }

    @Override
    public final void free(KMetaModel p_metaModel) {
        if (this._start_address != 0) {
            KMetaClass metaClass = p_metaModel.metaClass(UNSAFE.getInt(_start_address + OFFSET_META_CLASS_INDEX));

            for (int i = 0; i < metaClass.metaElements().length; i++) {
                KMeta meta = metaClass.metaElements()[i];

                if (meta.metaType().equals(MetaType.ATTRIBUTE)) {
                    KMetaAttribute metaAttribute = (KMetaAttribute) meta;
                    if (metaAttribute.attributeTypeId() == KPrimitiveTypes.STRING_ID) {
                        long ptr = rawPointerForIndex(metaAttribute.index(), metaClass);
                        long ptr_str_segment = UNSAFE.getLong(ptr);
                        if (ptr_str_segment != 0) {
                            UNSAFE.freeMemory(ptr_str_segment);
//                        _allocated_segments--;
                        }
                    }
                    if (metaAttribute.attributeTypeId() == KPrimitiveTypes.CONTINUOUS_ID) {
                        long ptr = rawPointerForIndex(metaAttribute.index(), metaClass);
                        long ptr_segment = UNSAFE.getLong(ptr);
                        if (ptr_segment != 0) {
                            UNSAFE.freeMemory(ptr_segment);
//                        _allocated_segments--;
                        }
                    }
                } else if (meta.metaType().equals(MetaType.RELATION)) {
                    KMetaRelation metaReference = (KMetaRelation) meta;
                    long ptr = rawPointerForIndex(metaReference.index(), metaClass);
                    long ptr_str_segment = UNSAFE.getLong(ptr);
                    if (ptr_str_segment != 0) {
                        UNSAFE.freeMemory(ptr_str_segment);
//                    _allocated_segments--;
                    }
                }
            }
        }

        UNSAFE.freeMemory(_start_address);
//        _allocated_segments--;

//        if (_allocated_segments != 0) {
//            throw new RuntimeException("OffHeap Memory Management Exception: more segments allocated than freed");
//        }
    }

    @Override
    public short type() {
        return KChunkTypes.OBJECT_CHUNK;
    }

    @Override
    public KChunkSpace space() {
        return this._space;
    }

    @Override
    public long getFlags() {
        return UNSAFE.getLong(this._start_address + OFFSET_FLAGS);
    }

    @Override
    public void setFlags(long p_bitsToEnable, long p_bitsToDisable) {
        long val;
        long nval;
        do {
            val = UNSAFE.getLong(this._start_address + OFFSET_FLAGS);
            nval = val & ~p_bitsToDisable | p_bitsToEnable;
        } while (!UNSAFE.compareAndSwapLong(null, _start_address + OFFSET_FLAGS, val, nval));
    }

    @Override
    public long universe() {
        return this._universe;
    }

    @Override
    public long time() {
        return this._time;
    }

    @Override
    public long obj() {
        return this._obj;
    }


    @Override
    public final int getLongArraySize(int p_index, KMetaClass p_metaClass) {
        int size = 0;
        long[] refs = getLongArray(p_index, p_metaClass);
        if (refs != null) {
            size = refs.length;
        }
        return size;
    }

    @Override
    public final long getLongArrayElem(int p_index, int p_refIndex, KMetaClass p_metaClass) {
        long elem = KConfig.NULL_LONG;
        long[] refs = getLongArray(p_index, p_metaClass);
        if (refs != null) {
            elem = refs[p_refIndex];
        }
        return elem;
    }

    @Override
    public final long memoryAddress() {
        return _start_address;
    }

    @Override
    public final void setMemoryAddress(long p_address) {
        this._start_address = p_address;
        if (this._space != null) {
            this._space.notifyRealloc(this._start_address, this._universe, this._time, this._obj);
        }
    }

}
