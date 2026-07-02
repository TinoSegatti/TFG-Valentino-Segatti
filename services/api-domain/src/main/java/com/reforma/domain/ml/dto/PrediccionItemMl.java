package com.reforma.domain.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Predicción de una MP devuelta por api-ml (campos snake_case del schema Pydantic). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PrediccionItemMl(
        @JsonProperty("id_materia_prima") Long idMateriaPrima,
        @JsonProperty("nivel_alerta") String nivelAlerta,
        String tendencia,
        @JsonProperty("stock_actual") double stockActual,
        @JsonProperty("dias_restantes") Integer diasRestantes,
        @JsonProperty("fecha_agotamiento_offset_dias") Integer fechaAgotamientoOffsetDias,
        @JsonProperty("neto_promedio") double netoPromedio,
        @JsonProperty("consumo_promedio") double consumoPromedio,
        @JsonProperty("ingreso_promedio") double ingresoPromedio,
        @JsonProperty("n_meses") int nMeses,
        @JsonProperty("modelo_usado") String modeloUsado,
        @JsonProperty("serie_historica") List<PuntoSerieMl> serieHistorica,
        @JsonProperty("serie_proyeccion") List<PuntoSerieMl> serieProyeccion) {}
