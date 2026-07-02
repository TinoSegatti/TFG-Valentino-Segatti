package com.reforma.domain.ml.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Un ítem (materia prima) del request de predicción de stock hacia api-ml. */
public record ItemPrediccionMl(
        @JsonProperty("id_materia_prima") Long idMateriaPrima,
        @JsonProperty("stock_actual") double stockActual,
        @JsonProperty("serie_mensual") List<PuntoMensualMl> serieMensual) {}
