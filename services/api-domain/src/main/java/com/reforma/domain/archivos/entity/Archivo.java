package com.reforma.domain.archivos.entity;

import com.reforma.domain.archivos.domain.TipoModuloArchivo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Snapshot inmutable de un módulo (Inventario/Compras/Fórmulas) al momento de su creación.
 * Mapea {@code t_archivo} (V013). Solo se inserta: nunca se actualiza ni se borra.
 * {@code datos} guarda los DTOs de respuesta del módulo serializados a JSON, de modo que el
 * frontend los renderiza con los mismos modelos que la pantalla original.
 */
@Entity
@Table(name = "t_archivo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Archivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_granja", nullable = false, length = 32)
    private String idGranja;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_modulo", nullable = false, length = 20)
    private TipoModuloArchivo tipoModulo;

    @Column(name = "codigo_archivo", nullable = false, length = 50)
    private String codigoArchivo;

    @Column(length = 500)
    private String descripcion;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "creado_por", nullable = false, length = 32)
    private String creadoPor;

    @Column(name = "creado_por_email", nullable = false, length = 255)
    private String creadoPorEmail;

    @Column(name = "total_registros", nullable = false)
    private Integer totalRegistros;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String datos;
}
