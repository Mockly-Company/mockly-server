package app.mockly.domain.product.repository;

import app.mockly.domain.product.entity.SubscriptionProduct;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionProductRepository extends JpaRepository<SubscriptionProduct, Integer> {

}
