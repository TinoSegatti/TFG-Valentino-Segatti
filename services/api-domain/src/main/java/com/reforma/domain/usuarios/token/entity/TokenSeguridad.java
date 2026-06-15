package com.reforma.domain.usuarios.token.entity;

import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.token.domain.TipoToken;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Token de seguridad de un solo uso (verificación de email, reset de contraseña, invitación).
 * Mapea {@code t_token_seguridad} (V010). Se persiste el hash, nunca el token en claro.
 */
@Entity
@Table(name = "t_token_seguridad")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenSeguridad {

    @Id
    @Column(length = 32)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoToken tipo;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_expiracion", nullable = false)
    private Instant fechaExpiracion;

    @Column(name = "consumido_en")
    private Instant consumidoEn;
}
