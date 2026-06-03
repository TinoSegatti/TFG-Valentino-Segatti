package com.reforma.domain.proveedores.entity;

import com.reforma.domain.granjas.entity.Granja;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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

/**
 * Proveedor de materias primas — Sprint 2 (RF-PROV-001 / RF-PROV-002).
 * Multi-tenant por {@code id_granja}; el código es único dentro de la granja.
 */
@Entity
@Table(name = "t_proveedor")
// id: PK BIGINT autoincremental. codigo_proveedor: clave de negocio/UI (única entre activos, V003).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Proveedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @Column(name = "codigo_proveedor", nullable = false, length = 50)
    private String codigoProveedor;

    @Column(name = "nombre_proveedor", nullable = false, length = 200)
    private String nombreProveedor;

    @Column(length = 50)
    private String telefono;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String cuit;

    @Column(columnDefinition = "TEXT")
    private String direccion;

    @Column(length = 100)
    private String localidad;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_ultima_actualizacion", nullable = false)
    private Instant fechaUltimaActualizacion;
}
