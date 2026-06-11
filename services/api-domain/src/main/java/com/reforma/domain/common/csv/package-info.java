/**
 * Utilidades genéricas para import/export CSV de catálogos (RF-MP-004, RF-PROV, RF-ANI-003).
 * <p>RFC 4180 simplificado: coma como delimitador, doble comilla como quote, escape "" dentro
 * de campos entrecomillados. UTF-8 sin BOM al exportar; tolera BOM al importar.
 */
package com.reforma.domain.common.csv;
