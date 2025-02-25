/**
 * Autogenerated by Thrift Compiler (0.17.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.bendb.thrifty.test.gen;

@SuppressWarnings({"cast", "rawtypes", "serial", "unchecked", "unused"})
public class TheEmptyUnion extends org.apache.thrift.TUnion<TheEmptyUnion, TheEmptyUnion._Fields> {
    private static final org.apache.thrift.protocol.TStruct STRUCT_DESC =
            new org.apache.thrift.protocol.TStruct("TheEmptyUnion");

    /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
    public enum _Fields implements org.apache.thrift.TFieldIdEnum {
        ;

        private static final java.util.Map<java.lang.String, _Fields> byName =
                new java.util.HashMap<java.lang.String, _Fields>();

        static {
            for (_Fields field : java.util.EnumSet.allOf(_Fields.class)) {
                byName.put(field.getFieldName(), field);
            }
        }

        /**
         * Find the _Fields constant that matches fieldId, or null if its not found.
         */
        @org.apache.thrift.annotation.Nullable
        public static _Fields findByThriftId(int fieldId) {
            switch (fieldId) {
                default:
                    return null;
            }
        }

        /**
         * Find the _Fields constant that matches fieldId, throwing an exception
         * if it is not found.
         */
        public static _Fields findByThriftIdOrThrow(int fieldId) {
            _Fields fields = findByThriftId(fieldId);
            if (fields == null) throw new java.lang.IllegalArgumentException("Field " + fieldId + " doesn't exist!");
            return fields;
        }

        /**
         * Find the _Fields constant that matches name, or null if its not found.
         */
        @org.apache.thrift.annotation.Nullable
        public static _Fields findByName(java.lang.String name) {
            return byName.get(name);
        }

        private final short _thriftId;
        private final java.lang.String _fieldName;

        _Fields(short thriftId, java.lang.String fieldName) {
            _thriftId = thriftId;
            _fieldName = fieldName;
        }

        @Override
        public short getThriftFieldId() {
            return _thriftId;
        }

        @Override
        public java.lang.String getFieldName() {
            return _fieldName;
        }
    }

    public static final java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;

    static {
        java.util.Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap =
                new java.util.EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
        metaDataMap = java.util.Collections.unmodifiableMap(tmpMap);
        org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(TheEmptyUnion.class, metaDataMap);
    }

    public TheEmptyUnion() {
        super();
    }

    public TheEmptyUnion(_Fields setField, java.lang.Object value) {
        super(setField, value);
    }

    public TheEmptyUnion(TheEmptyUnion other) {
        super(other);
    }

    @Override
    public TheEmptyUnion deepCopy() {
        return new TheEmptyUnion(this);
    }

    @Override
    protected void checkType(_Fields setField, java.lang.Object value) throws java.lang.ClassCastException {
        switch (setField) {
            default:
                throw new java.lang.IllegalArgumentException("Unknown field id " + setField);
        }
    }

    @Override
    protected java.lang.Object standardSchemeReadValue(
            org.apache.thrift.protocol.TProtocol iprot, org.apache.thrift.protocol.TField field)
            throws org.apache.thrift.TException {
        _Fields setField = _Fields.findByThriftId(field.id);
        if (setField != null) {
            switch (setField) {
                default:
                    throw new java.lang.IllegalStateException(
                            "setField wasn't null, but didn't match any of the case statements!");
            }
        } else {
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, field.type);
            return null;
        }
    }

    @Override
    protected void standardSchemeWriteValue(org.apache.thrift.protocol.TProtocol oprot)
            throws org.apache.thrift.TException {
        switch (setField_) {
            default:
                throw new java.lang.IllegalStateException("Cannot write union with unknown field " + setField_);
        }
    }

    @Override
    protected java.lang.Object tupleSchemeReadValue(org.apache.thrift.protocol.TProtocol iprot, short fieldID)
            throws org.apache.thrift.TException {
        _Fields setField = _Fields.findByThriftId(fieldID);
        if (setField != null) {
            switch (setField) {
                default:
                    throw new java.lang.IllegalStateException(
                            "setField wasn't null, but didn't match any of the case statements!");
            }
        } else {
            throw new org.apache.thrift.protocol.TProtocolException("Couldn't find a field with field id " + fieldID);
        }
    }

    @Override
    protected void tupleSchemeWriteValue(org.apache.thrift.protocol.TProtocol oprot)
            throws org.apache.thrift.TException {
        switch (setField_) {
            default:
                throw new java.lang.IllegalStateException("Cannot write union with unknown field " + setField_);
        }
    }

    @Override
    protected org.apache.thrift.protocol.TField getFieldDesc(_Fields setField) {
        switch (setField) {
            default:
                throw new java.lang.IllegalArgumentException("Unknown field id " + setField);
        }
    }

    @Override
    protected org.apache.thrift.protocol.TStruct getStructDesc() {
        return STRUCT_DESC;
    }

    @Override
    protected _Fields enumForId(short id) {
        return _Fields.findByThriftIdOrThrow(id);
    }

    @org.apache.thrift.annotation.Nullable
    @Override
    public _Fields fieldForId(int fieldId) {
        return _Fields.findByThriftId(fieldId);
    }

    public boolean equals(java.lang.Object other) {
        if (other instanceof TheEmptyUnion) {
            return equals((TheEmptyUnion) other);
        } else {
            return false;
        }
    }

    public boolean equals(TheEmptyUnion other) {
        return other != null
                && getSetField() == other.getSetField()
                && getFieldValue().equals(other.getFieldValue());
    }

    @Override
    public int compareTo(TheEmptyUnion other) {
        int lastComparison = org.apache.thrift.TBaseHelper.compareTo(getSetField(), other.getSetField());
        if (lastComparison == 0) {
            return org.apache.thrift.TBaseHelper.compareTo(getFieldValue(), other.getFieldValue());
        }
        return lastComparison;
    }

    @Override
    public int hashCode() {
        java.util.List<java.lang.Object> list = new java.util.ArrayList<java.lang.Object>();
        list.add(this.getClass().getName());
        org.apache.thrift.TFieldIdEnum setField = getSetField();
        if (setField != null) {
            list.add(setField.getThriftFieldId());
            java.lang.Object value = getFieldValue();
            if (value instanceof org.apache.thrift.TEnum) {
                list.add(((org.apache.thrift.TEnum) getFieldValue()).getValue());
            } else {
                list.add(value);
            }
        }
        return list.hashCode();
    }

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
        try {
            write(new org.apache.thrift.protocol.TCompactProtocol(
                    new org.apache.thrift.transport.TIOStreamTransport(out)));
        } catch (org.apache.thrift.TException te) {
            throw new java.io.IOException(te);
        }
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
        try {
            read(new org.apache.thrift.protocol.TCompactProtocol(
                    new org.apache.thrift.transport.TIOStreamTransport(in)));
        } catch (org.apache.thrift.TException te) {
            throw new java.io.IOException(te);
        }
    }
}
