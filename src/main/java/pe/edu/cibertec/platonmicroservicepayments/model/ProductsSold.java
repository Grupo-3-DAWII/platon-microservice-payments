package pe.edu.cibertec.platonmicroservicepayments.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "products_sold", schema = "sgestionlibreria_payment")
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
    @Column(name = "id_products", nullable = false)
    private Long idProducts;

    /** Referencia al microservicio de cuentas (sin FK a nivel de BD). */
    @Column(name = "id_user", nullable = false)
    private Long idUser;

    @Column(name = "number_sold", nullable = false)
    private Integer numberSold;

    @Column(name = "create_date", nullable = false, updatable = false)
    private LocalDateTime createDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_state_sold", nullable = false)
    private StateSold stateSold;

    @PrePersist
    void onCreate() {
        if (createDate == null) {
            createDate = LocalDateTime.now();
        }
    }
}
