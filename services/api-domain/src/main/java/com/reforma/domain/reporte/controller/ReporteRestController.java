package com.reforma.domain.reporte.controller;

import com.reforma.domain.auth.SecurityUtils;
import com.reforma.domain.reporte.dto.InformeEstadoResponse;
import com.reforma.domain.reporte.service.InformeCsvService;
import com.reforma.domain.reporte.service.InformeEstadoService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Informe de estado (RF-REP-001/002/003). Solo lecturas: cualquier rol con acceso al tenant
 * puede generarlo (se audita INFORME_GENERADO). Sin período explícito se informan los
 * últimos {@value InformeEstadoService#PERIODO_DEFAULT_DIAS} días.
 */
@RestController
@RequestMapping("/api/reportes/{idGranja}")
@RequiredArgsConstructor
public class ReporteRestController {

    private final InformeEstadoService informeEstadoService;
    private final InformeCsvService informeCsvService;

    @GetMapping("/informe")
    public InformeEstadoResponse informe(
            @PathVariable String idGranja,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        var principal = SecurityUtils.requirePrincipal();
        return informeEstadoService.generar(principal.tenantId(), principal.id(), idGranja, desde, hasta);
    }

    /** RF-REP-002 — exporta una sección del informe a CSV (nombre incluye granja y período). */
    @GetMapping(value = "/informe/csv", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> informeCsv(
            @PathVariable String idGranja,
            @RequestParam String seccion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        var principal = SecurityUtils.requirePrincipal();
        InformeEstadoResponse informe =
                informeEstadoService.generar(principal.tenantId(), principal.id(), idGranja, desde, hasta);
        String csv = informeCsvService.exportar(informe, seccion);
        String nombre = "informe_" + seccion.toLowerCase() + "_" + idGranja + "_"
                + informe.desde() + "_" + informe.hasta() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
