package com.reforma.domain.prediccion.entity;

import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import com.reforma.domain.prediccion.domain.NivelAlertaStock;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Última predicción de agotamiento de stock de una materia prima (RF-IA-PRED). Mapea
 * {@code t_ia_prediccion_stock}. Se upsertea una fila por (granja, materia prima) en cada cálculo.
 */
@Entity
@Table(name = "t_ia_prediccion_stock")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrediccionStock {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @Column(name = "fecha_agotamiento")
    private LocalDate fechaAgotamiento;

    @Column(name = "dias_restantes")
    private Integer diasRestantes;

    @Enumerated(EnumType.STRING)
    @Column(name = "nivel_alerta", length = 20)
    private NivelAlertaStock nivelAlerta;

    @Column(name = "modelo_usado", length = 50)
    private String modeloUsado;

    @Column(name = "calculado_en", nullable = false)
    private Instant calculadoEn;
}
