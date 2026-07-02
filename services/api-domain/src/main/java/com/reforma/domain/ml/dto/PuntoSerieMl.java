package com.reforma.domain.ml.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Un punto de la serie de existencias (histórico o proyección) devuelto por api-ml. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PuntoSerieMl(String mes, double existencias) {}
