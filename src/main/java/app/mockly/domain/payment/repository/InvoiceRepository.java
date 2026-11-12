package app.mockly.domain.payment.repository;

import app.mockly.domain.payment.entity.Invoice;
import app.mockly.domain.payment.entity.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findFirstBySubscriptionIdAndStatusInOrderByCreatedAtDesc(
            Long subscriptionId,
            Collection<InvoiceStatus> statuses
    );
}
