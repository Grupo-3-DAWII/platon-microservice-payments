package pe.edu.cibertec.platonmicroservicepayments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "products_sold", schema = "payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductsSold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_products_sold")
    private Long idProductsSold;

    /** Referencia al microservicio de productos (sin FK a nivel de BD). */
    @Column(name = "id_products")
    private Long idProducts;

    /** Referencia al microservicio de cuentas (sin FK a nivel de BD). */
    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @Column(name = "number_sold")
    private Integer numberSold;

    @Column(name = "create_date", nullable = false, updatable = false)
    private LocalDateTime createDate;

    @Column(name = "stock_deduction_id")
    private UUID stockDeductionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_state_sold", nullable = false)
    private StateSold stateSold;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Builder.Default
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<ProductsSoldItem> items = new ArrayList<>();

    public boolean hasStockDeduction() {
        return stockDeductionId != null || !items.isEmpty();
    }

    @PrePersist
    void onCreate() {
        if (createDate == null) {
            createDate = LocalDateTime.now();
        }
    }
}
