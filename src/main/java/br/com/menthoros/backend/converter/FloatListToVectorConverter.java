package br.com.menthoros.backend.converter;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Converter(autoApply = false)
public class FloatListToVectorConverter implements AttributeConverter<List<Float>, Object> {

    @Override
    public Object convertToDatabaseColumn(List<Float> attribute) {
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
    public List<Float> convertToEntityAttribute(Object dbData) {
        if (dbData == null) return null;

        if (dbData instanceof PGvector pgVector) {
            float[] array = pgVector.toArray();
            List<Float> floatList = new java.util.ArrayList<>();
            for (float f : array) {
                floatList.add(f);
            }
            return floatList;
        }

        return null;
    }
}
