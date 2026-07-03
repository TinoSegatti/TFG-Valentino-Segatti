package com.reforma.domain.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reforma.domain.reporte.dto.InformeEstadoResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Tests puros de la exportación CSV del informe (RF-REP-002), sin Spring context. */
class InformeCsvServiceTest {

    private final InformeCsvService informeCsvService = new InformeCsvService();

    private static InformeEstadoResponse informeConProveedor() {
        return new InformeEstadoResponse(
                "g_demo",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 6, 30),
                new InformeEstadoResponse.ResumenGeneral(2, 2100, 3800, 1, 1000, 10.5),
                new InformeEstadoResponse.SeccionProveedores(List.of(
                        new InformeEstadoResponse.ProveedorInforme(
                                "PRV1", "Proveedor, Uno", 2, 2100.0, 175.0, "Maíz"))),
                new InformeEstadoResponse.SeccionInventario(List.of(), 3800, 10.5),
                new InformeEstadoResponse.SeccionCompras(List.of(), List.of()),
                new InformeEstadoResponse.SeccionConsumos(List.of(), List.of()),
                new InformeEstadoResponse.SeccionIa(List.of(), false, List.of()));
    }

    @Test
    @DisplayName("exportar proveedores: cabecera + fila con montos y escape RFC 4180 de comas")
    void exportarProveedores() {
        String csv = informeCsvService.exportar(informeConProveedor(), "proveedores");

        String[] lineas = csv.split("\r\n");
        assertThat(lineas[0]).isEqualTo("codigo,nombre,compras,monto,kg,materia_principal");
        assertThat(lineas[1]).isEqualTo("PRV1,\"Proveedor, Uno\",2,2100.00,175.00,Maíz");
    }

    @Test
    @DisplayName("exportar: acepta la sección en cualquier capitalización")
    void exportarSeccionCaseInsensitive() {
        String csv = informeCsvService.exportar(informeConProveedor(), "PROVEEDORES");

        assertThat(csv).startsWith("codigo,nombre");
    }

    @Test
    @DisplayName("exportar: 400 si la sección no existe")
    void exportarSeccionDesconocida() {
        assertThatThrownBy(() -> informeCsvService.exportar(informeConProveedor(), "noexiste"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
