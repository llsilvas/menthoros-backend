package com.menthoros.converter;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class FloatListToVectorConverter implements AttributeConverter<List<Float>, PGvector> {

    @Override
    public PGvector convertToDatabaseColumn(List<Float> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        float[] floatArray = new float[attribute.size()];
        for (int i = 0; i < attribute.size(); i++) {
            floatArray[i] = attribute.get(i);
        }
        return new PGvector(floatArray);
    }

    @Override
    public List<Float> convertToEntityAttribute(PGvector dbData) {
        if (dbData == null) return null;

        float[] array = dbData.toArray();
        List<Float> floatList = new java.util.ArrayList<>();
        for (float f : array) {
            floatList.add(f);
        }
        return floatList;
    }
}
