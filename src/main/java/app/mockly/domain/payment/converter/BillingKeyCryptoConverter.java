package app.mockly.domain.payment.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class BillingKeyCryptoConverter implements AttributeConverter<String, String> {

    public BillingKeyCryptoConverter() {
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return BillingKeyCryptoHolder.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return BillingKeyCryptoHolder.decrypt(dbData);
    }
}
