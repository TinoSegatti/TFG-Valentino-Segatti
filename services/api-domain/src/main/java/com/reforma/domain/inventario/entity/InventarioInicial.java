package com.reforma.domain.inventario.entity;

import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.materiasprimas.entity.MateriaPrima;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_inventario_inicial")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioInicial {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_materia_prima", nullable = false)
    private MateriaPrima materiaPrima;

    @Column(name = "cantidad_inicial", nullable = false)
    private Double cantidadInicial;

    @Column(name = "precio_inicial", nullable = false)
    private Double precioInicial;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;
}
