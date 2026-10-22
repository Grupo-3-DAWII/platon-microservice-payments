package pe.edu.cibertec.platonmicroservicepayments.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "products_sold_item", schema = "payment")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductsSoldItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_products_sold", nullable = false)
    private ProductsSold sale;

    @Column(name = "id_products", nullable = false)
    private Long idProducts;

    @Column(name = "number_sold", nullable = false)
    private Integer numberSold;

    @Column(name = "unit_price", precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public BigDecimal subtotal() {
        return unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(numberSold));
    }

    @Column(name = "stock_deduction_id")
    private UUID stockDeductionId;
}
