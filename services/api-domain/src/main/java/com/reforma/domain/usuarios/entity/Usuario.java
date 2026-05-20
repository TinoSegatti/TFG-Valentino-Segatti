package com.reforma.domain.usuarios.entity;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.RolEmpleado;
import com.reforma.domain.common.domain.TipoUsuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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
@Table(name = "t_usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, unique = true, length = 200)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "google_id", unique = true, length = 100)
    private String googleId;

    @Column(name = "nombre_usuario", nullable = false, length = 100)
    private String nombreUsuario;

    @Column(name = "apellido_usuario", nullable = false, length = 100)
    private String apellidoUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false, length = 20)
    private TipoUsuario tipoUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_suscripcion", nullable = false, length = 20)
    private PlanSuscripcion planSuscripcion;

    @Column(name = "max_granjas", nullable = false)
    private Integer maxGranjas;

    @Column(nullable = false)
    private Boolean activo;

    @Column(name = "email_verificado", nullable = false)
    private Boolean emailVerificado;

    @Column(name = "token_verificacion", unique = true, length = 100)
    private String tokenVerificacion;

    @Column(name = "fecha_expiracion_token")
    private Instant fechaExpiracionToken;

    @Column(name = "fecha_registro", nullable = false)
    private Instant fechaRegistro;

    @Column(name = "ultimo_acceso")
    private Instant ultimoAcceso;

    @Column(name = "codigo_referencia", unique = true, length = 50)
    private String codigoReferencia;

    @Column(name = "fecha_generacion_codigo")
    private Instant fechaGeneracionCodigo;

    @Column(name = "codigo_expiracion")
    private Instant codigoExpiracion;

    @Column(name = "es_usuario_empleado", nullable = false)
    private Boolean esUsuarioEmpleado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_dueno")
    private Usuario usuarioDueno;

    @OneToMany(mappedBy = "usuarioDueno", fetch = FetchType.LAZY)
    @Builder.Default
    private List<Usuario> empleados = new ArrayList<>();

    @Column(name = "fecha_vinculacion")
    private Instant fechaVinculacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_empleado", length = 20)
    private RolEmpleado rolEmpleado;

    @Column(name = "activo_como_empleado", nullable = false)
    private Boolean activoComoEmpleado;
}
