package app.mockly.domain.payment.converter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class BillingKeyCryptoInitializer {

    public BillingKeyCryptoInitializer(
            @Value("${payment.billing-key-encryption-secret}") String encryptionSecret
    ) {
        BillingKeyCryptoHolder.initialize(encryptionSecret);
    }
}
