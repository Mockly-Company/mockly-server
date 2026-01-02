package app.mockly.domain.plan.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "plan_price",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_plan_currency",
            columnNames = {"plan_id", "currency"}
        )
    }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PlanPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price; // 가격

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Currency currency;
}
