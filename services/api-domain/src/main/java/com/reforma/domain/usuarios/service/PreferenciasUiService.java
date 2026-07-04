package com.reforma.domain.usuarios.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reforma.domain.usuarios.dto.PreferenciasUiRequest;
import com.reforma.domain.usuarios.dto.PreferenciasUiResponse;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.math.BigDecimal;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Preferencias de UI por usuario (módulo Personalización): fondo de plataforma + cortina de
 * contraste, persistidas en {@code t_usuarios.preferencias_ui} (JSONB, V014). Opera solo sobre
 * el usuario del JWT (cualquier rol edita SU preferencia — RD-C5); no se audita ni se gatea
 * por plan.
 *
 * <p>El fondo puede ser una clave de la galería curada o {@code "personalizada"}: una imagen
 * elegida por el usuario, embebida como data URL (comprimida por el frontend, tope
 * {@link #IMAGEN_MAX_CHARS}). Solo data URLs de tipos de imagen conocidos — nunca URLs
 * externas (evita fugas de tracking/contenido remoto).
 */
@Service
@RequiredArgsConstructor
public class PreferenciasUiService {

    /** Fondo cuyo valor real viaja en {@code imagenPersonalizada}. */
    static final String FONDO_PERSONALIZADO = "personalizada";

    /**
     * Galería curada de fondos (claves conocidas por el frontend) + el fondo personalizado.
     * Debe coincidir con FONDOS_TEMA de core/tema/tema.service.ts.
     */
    static final Set<String> FONDOS_VALIDOS = Set.of(
            "default",
            "color-grafito",
            "color-bosque",
            "color-oceano",
            "color-vino",
            "escena-amanecer",
            "escena-campo",
            "escena-noche",
            "escena-niebla",
            FONDO_PERSONALIZADO);

    /** RD-C3: la cortina nunca baja de 0.35 — garantiza contraste del texto claro. */
    static final BigDecimal CORTINA_MIN = new BigDecimal("0.35");

    static final BigDecimal CORTINA_MAX = new BigDecimal("0.85");

    static final String FONDO_DEFAULT = "default";
    static final BigDecimal CORTINA_DEFAULT = new BigDecimal("0.55");

    /** ~1 MB de imagen binaria (base64 agrega ~33%). El frontend comprime antes de subir. */
    static final int IMAGEN_MAX_CHARS = 1_400_000;

    private static final Pattern IMAGEN_DATA_URL =
            Pattern.compile("^data:image/(png|jpe?g|webp);base64,[A-Za-z0-9+/=]+$");

    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public PreferenciasUiResponse obtener(String idUsuario) {
        Usuario usuario = usuarioRepository.findById(idUsuario).orElseThrow();
        return leer(usuario.getPreferenciasUi());
    }

    @Transactional
    public PreferenciasUiResponse actualizar(String idUsuario, PreferenciasUiRequest request) {
        PreferenciasUiRequest normalizada = validar(request);
        Usuario usuario = usuarioRepository.findById(idUsuario).orElseThrow();
        usuario.setPreferenciasUi(escribir(normalizada));
        usuarioRepository.save(usuario);
        return new PreferenciasUiResponse(
                normalizada.fondo(),
                normalizada.intensidadCortina(),
                normalizada.imagenPersonalizada());
    }

    /** Valida y normaliza: la imagen solo se acepta (y persiste) con fondo personalizado. */
    private static PreferenciasUiRequest validar(PreferenciasUiRequest request) {
        if (!FONDOS_VALIDOS.contains(request.fondo())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Fondo desconocido: " + request.fondo());
        }
        BigDecimal alfa = request.intensidadCortina();
        if (alfa.compareTo(CORTINA_MIN) < 0 || alfa.compareTo(CORTINA_MAX) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La intensidad de la cortina debe estar entre " + CORTINA_MIN + " y " + CORTINA_MAX);
        }
        if (!FONDO_PERSONALIZADO.equals(request.fondo())) {
            return new PreferenciasUiRequest(request.fondo(), alfa, null);
        }
        String imagen = request.imagenPersonalizada();
        if (imagen == null || imagen.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "El fondo personalizado requiere una imagen");
        }
        if (imagen.length() > IMAGEN_MAX_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La imagen supera el tamaño máximo permitido (~1 MB)");
        }
        if (!IMAGEN_DATA_URL.matcher(imagen).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La imagen debe ser un data URL de tipo png, jpeg o webp");
        }
        return request;
    }

    private PreferenciasUiResponse leer(String json) {
        if (json == null || json.isBlank()) {
            return new PreferenciasUiResponse(FONDO_DEFAULT, CORTINA_DEFAULT, null);
        }
        try {
            PreferenciasUiRequest guardadas =
                    objectMapper.readValue(json, PreferenciasUiRequest.class);
            // Si el fondo guardado salió de la galería (p. ej. se quitó en una versión futura)
            // o quedó personalizado sin imagen, se degrada al default en lugar de romper.
            boolean valido = FONDOS_VALIDOS.contains(guardadas.fondo())
                    && (!FONDO_PERSONALIZADO.equals(guardadas.fondo())
                            || (guardadas.imagenPersonalizada() != null
                                    && !guardadas.imagenPersonalizada().isBlank()));
            String fondo = valido ? guardadas.fondo() : FONDO_DEFAULT;
            BigDecimal alfa = guardadas.intensidadCortina() != null
                    ? guardadas.intensidadCortina().max(CORTINA_MIN).min(CORTINA_MAX)
                    : CORTINA_DEFAULT;
            String imagen =
                    valido && FONDO_PERSONALIZADO.equals(fondo) ? guardadas.imagenPersonalizada() : null;
            return new PreferenciasUiResponse(fondo, alfa, imagen);
        } catch (JsonProcessingException e) {
            return new PreferenciasUiResponse(FONDO_DEFAULT, CORTINA_DEFAULT, null);
        }
    }

    private String escribir(PreferenciasUiRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            // No debería ocurrir con un record plano; se traduce a 500 vía handler global.
            throw new IllegalStateException("No se pudieron serializar las preferencias", e);
        }
    }
}
