package app.mockly.domain.product.repository;

import app.mockly.domain.product.entity.SubscriptionProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SubscriptionProductRepository extends JpaRepository<SubscriptionProduct, Integer> {
    @Query("""
            SELECT DISTINCT sp FROM SubscriptionProduct sp
                LEFT JOIN FETCH sp.subscriptionPlans
            WHERE sp.isActive = true
            """)
    List<SubscriptionProduct> findAllActiveWithPlans();
}
