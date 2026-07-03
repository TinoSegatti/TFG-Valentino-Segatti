package com.reforma.domain.reporte.service;

import com.reforma.domain.common.csv.CsvWriter;
import com.reforma.domain.reporte.dto.InformeEstadoResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Exporta una sección del informe de estado a CSV (RF-REP-002). Recibe el informe ya
 * calculado por {@link InformeEstadoService} y serializa la sección pedida con
 * {@code common/csv} (RFC 4180 simplificado, mismas utilidades que los catálogos).
 */
@Service
public class InformeCsvService {

    /** Secciones exportables; el nombre (en minúsculas) es el valor del parámetro {@code seccion}. */
    public enum SeccionCsv {
        PROVEEDORES,
        INVENTARIO,
        COMPRAS,
        CONSUMOS,
        ANOMALIAS
    }

    public String exportar(InformeEstadoResponse informe, String seccion) {
        SeccionCsv seccionCsv = parsearSeccion(seccion);
        return switch (seccionCsv) {
            case PROVEEDORES -> proveedores(informe);
            case INVENTARIO -> inventario(informe);
            case COMPRAS -> compras(informe);
            case CONSUMOS -> consumos(informe);
            case ANOMALIAS -> anomalias(informe);
        };
    }

    private static SeccionCsv parsearSeccion(String seccion) {
        try {
            return SeccionCsv.valueOf(seccion.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Sección de informe desconocida: " + seccion);
        }
    }

    private static String proveedores(InformeEstadoResponse informe) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of("codigo", "nombre", "compras", "monto", "kg", "materia_principal"));
        for (var p : informe.proveedores().proveedores()) {
            filas.add(List.of(
                    texto(p.codigo()),
                    texto(p.nombre()),
                    String.valueOf(p.compras()),
                    numero(p.monto()),
                    numero(p.kg()),
                    texto(p.materiaPrincipal())));
        }
        return CsvWriter.escribir(filas);
    }

    private static String inventario(InformeEstadoResponse informe) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of(
                "codigo", "nombre", "precio_vigente", "cantidad_acumulada", "cantidad_sistema",
                "cantidad_real", "merma", "valor_stock", "precio_almacen"));
        for (var i : informe.inventario().items()) {
            filas.add(List.of(
                    texto(i.codigoMateriaPrima()),
                    texto(i.nombreMateriaPrima()),
                    numero(i.precioPorKilo()),
                    numero(i.cantidadAcumulada()),
                    numero(i.cantidadSistema()),
                    numero(i.cantidadReal()),
                    numero(i.merma()),
                    numero(i.valorStock()),
                    numero(i.precioAlmacen())));
        }
        return CsvWriter.escribir(filas);
    }

    private static String compras(InformeEstadoResponse informe) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of(
                "codigo", "nombre", "kg", "monto", "precio_min", "precio_max", "precio_promedio"));
        for (var m : informe.compras().materias()) {
            filas.add(List.of(
                    texto(m.codigo()),
                    texto(m.nombre()),
                    numero(m.kg()),
                    numero(m.monto()),
                    numero(m.precioMin()),
                    numero(m.precioMax()),
                    numero(m.precioPromedio())));
        }
        return CsvWriter.escribir(filas);
    }

    private static String consumos(InformeEstadoResponse informe) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of("tipo", "codigo", "descripcion", "fabricaciones", "kg", "costo"));
        for (var f : informe.consumos().formulas()) {
            filas.add(List.of(
                    "FORMULA",
                    texto(f.codigo()),
                    texto(f.descripcion()),
                    String.valueOf(f.fabricaciones()),
                    numero(f.kgProducidos()),
                    numero(f.costoTotal())));
        }
        for (var m : informe.consumos().materias()) {
            filas.add(List.of(
                    "MATERIA_PRIMA",
                    texto(m.codigo()),
                    texto(m.nombre()),
                    "",
                    numero(m.kgConsumidos()),
                    numero(m.costo())));
        }
        return CsvWriter.escribir(filas);
    }

    private static String anomalias(InformeEstadoResponse informe) {
        List<List<String>> filas = new ArrayList<>();
        filas.add(List.of(
                "factura", "fecha_compra", "codigo_mp", "nombre_mp", "precio_ingresado",
                "precio_promedio_historico", "z_score", "clasificacion", "usuario_confirmo"));
        for (var a : informe.ia().anomalias()) {
            filas.add(List.of(
                    texto(a.numeroFactura()),
                    a.fechaCompra() != null ? a.fechaCompra().toString() : "",
                    texto(a.codigoMateriaPrima()),
                    texto(a.nombreMateriaPrima()),
                    numero(a.precioIngresado()),
                    a.precioPromedioHistorico() != null ? numero(a.precioPromedioHistorico()) : "",
                    a.zScore() != null ? numero(a.zScore()) : "",
                    texto(a.clasificacion()),
                    a.usuarioConfirmo() != null ? a.usuarioConfirmo().toString() : ""));
        }
        return CsvWriter.escribir(filas);
    }

    private static String texto(String valor) {
        return valor != null ? valor : "";
    }

    private static String numero(double valor) {
        // Punto decimal fijo (independiente del locale del JVM), igual que los CSV de catálogos.
        return String.format(Locale.ROOT, "%.2f", valor);
    }
}
