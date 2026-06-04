package com.reforma.domain.compras.entity;

import com.reforma.domain.compras.domain.EstadoCompra;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.proveedores.entity.Proveedor;
import com.reforma.domain.usuarios.entity.Usuario;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "t_compra_cabecera")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompraCabecera {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_granja", nullable = false)
    private Granja granja;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_proveedor", nullable = false)
    private Proveedor proveedor;

    @Column(name = "numero_factura", length = 100)
    private String numeroFactura;

    @Column(name = "fecha_compra", nullable = false)
    private Instant fechaCompra;

    @Column(name = "total_factura", nullable = false)
    private Double totalFactura;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "fecha_eliminacion")
    private Instant fechaEliminacion;

    @Column(name = "eliminado_por", length = 32)
    private String eliminadoPor;

    @Column(name = "codigo_proveedor_snapshot", length = 50)
    private String codigoProveedorSnapshot;

    @Column(name = "nombre_proveedor_snapshot", length = 200)
    private String nombreProveedorSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCompra estado;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @Builder.Default
    private List<CompraDetalle> detalles = new ArrayList<>();
}
