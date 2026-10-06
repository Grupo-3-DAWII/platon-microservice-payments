package pe.edu.cibertec.platonmicroservicepayments.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "state_sold", schema = "sgestionlibreria_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StateSold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_state_sold")
    private Long idStateSold;

    @Column(name = "name_state", nullable = false, length = 100, unique = true)
    private String nameState;
}
