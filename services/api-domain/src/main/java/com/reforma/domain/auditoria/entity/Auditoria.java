package com.reforma.domain.auditoria.entity;

import com.reforma.domain.auditoria.domain.AccionAuditoria;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Registro inmutable de auditoría (RF-AUD). Una fila por operación sensible.
 * Mapea {@code t_auditoria} (V001). No se actualiza ni se borra: solo se inserta.
 */
@Entity
@Table(name = "t_auditoria")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Auditoria {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "id_usuario", nullable = false, length = 32)
    private String idUsuario;

    @Column(name = "id_granja", length = 32)
    private String idGranja;

    @Column(name = "tabla_origen", nullable = false, length = 50)
    private String tablaOrigen;

    @Column(name = "id_registro", nullable = false, length = 32)
    private String idRegistro;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccionAuditoria accion;

    @Column(columnDefinition = "text")
    private String descripcion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_anteriores_json")
    private String datosAnterioresJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_nuevos_json")
    private String datosNuevosJson;

    @Column(name = "fecha_operacion", nullable = false)
    private Instant fechaOperacion;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;
}
