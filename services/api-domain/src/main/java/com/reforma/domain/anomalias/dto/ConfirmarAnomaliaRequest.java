package com.reforma.domain.anomalias.dto;

import jakarta.validation.constraints.NotNull;

/** Confirma o rechaza una anomalía detectada (RF-IA-ANOM-004). */
public record ConfirmarAnomaliaRequest(@NotNull Boolean confirmo) {}
