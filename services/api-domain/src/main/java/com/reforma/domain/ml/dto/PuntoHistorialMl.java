package com.reforma.domain.ml.dto;

import java.time.LocalDate;

/** Un precio histórico (fecha de negocio + precio) enviado a api-ml para el cálculo del Z-Score. */
public record PuntoHistorialMl(LocalDate fecha, double precio) {}
