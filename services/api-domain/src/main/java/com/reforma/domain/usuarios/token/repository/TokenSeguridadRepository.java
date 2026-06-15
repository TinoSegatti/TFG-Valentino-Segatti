package com.reforma.domain.usuarios.token.repository;

import com.reforma.domain.usuarios.token.domain.TipoToken;
import com.reforma.domain.usuarios.token.entity.TokenSeguridad;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenSeguridadRepository extends JpaRepository<TokenSeguridad, String> {

    Optional<TokenSeguridad> findByTokenHashAndTipo(String tokenHash, TipoToken tipo);

    /** Invalida (marca consumidos) los tokens vigentes de un usuario para un tipo dado. */
    @Modifying
    @Query("UPDATE TokenSeguridad t SET t.consumidoEn = :ahora "
            + "WHERE t.usuario.id = :idUsuario AND t.tipo = :tipo AND t.consumidoEn IS NULL")
    int invalidarVigentes(
            @Param("idUsuario") String idUsuario,
            @Param("tipo") TipoToken tipo,
            @Param("ahora") Instant ahora);
}
