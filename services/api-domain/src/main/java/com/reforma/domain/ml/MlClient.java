package com.reforma.domain.ml;

import com.reforma.domain.ml.dto.AnomaliaMlResponse;
import com.reforma.domain.ml.dto.EvaluarAnomaliaMlRequest;
import com.reforma.domain.ml.dto.PrediccionStockMlRequest;
import com.reforma.domain.ml.dto.PrediccionStockMlResponse;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente hacia api-ml (FastAPI). Autentica con JWT s2s (ADR-0004).
 *
 * <p><b>Fail-open:</b> si api-ml no responde, devuelve un error o el token es rechazado, se devuelve
 * {@link Optional#empty()} y se loguea; la detección de anomalías nunca bloquea el flujo de compras.
 */
@Component
@Slf4j
public class MlClient {

    private final RestClient mlRestClient;
    private final MlJwtService mlJwtService;

    public MlClient(RestClient mlRestClient, MlJwtService mlJwtService) {
        this.mlRestClient = mlRestClient;
        this.mlJwtService = mlJwtService;
    }

    public Optional<AnomaliaMlResponse> evaluarAnomalia(EvaluarAnomaliaMlRequest request) {
        try {
            AnomaliaMlResponse respuesta = mlRestClient
                    .post()
                    .uri("/api/ml/anomalias/evaluar")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + mlJwtService.generarToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AnomaliaMlResponse.class);
            return Optional.ofNullable(respuesta);
        } catch (Exception e) {
            log.warn("api-ml no disponible para evaluar anomalía de precio: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Predice el agotamiento de stock por materia prima (RF-IA-PRED). Fail-open como el resto. */
    public Optional<PrediccionStockMlResponse> predecirStock(PrediccionStockMlRequest request) {
        try {
            PrediccionStockMlResponse respuesta = mlRestClient
                    .post()
                    .uri("/api/ml/prediccion/stock")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + mlJwtService.generarToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PrediccionStockMlResponse.class);
            return Optional.ofNullable(respuesta);
        } catch (Exception e) {
            log.warn("api-ml no disponible para predecir stock: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
