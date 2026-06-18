package com.reforma.domain.mantenimiento.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrado físico de todos los datos de un tenant (dueño + empleados) y de sus cuentas.
 * Lo usa {@code LimpiezaCuentasDemoService} para resetear cuentas DEMO vencidas.
 *
 * <p>El orden de los DELETE respeta las claves foráneas: la mayoría de las tablas granja-scoped
 * caen por cascada al borrar {@code t_granja} (ON DELETE CASCADE), pero hay dos referencias
 * sin cascada que deben limpiarse antes:
 * <ul>
 *   <li>{@code t_ia_anomalia_precio.id_materia_prima} (NO ACTION) bloquearía el borrado por
 *       cascada de {@code t_materia_prima} → se elimina primero.</li>
 *   <li>{@code t_auditoria.id_usuario} (NO ACTION) bloquearía el borrado de las cuentas → se
 *       elimina antes que los usuarios.</li>
 * </ul>
 * Y las cuentas de empleado se borran antes que la del dueño porque lo referencian vía
 * {@code id_usuario_dueno}, y después de borrar las granjas para que ya no queden compras /
 * fabricaciones apuntando a ellas.
 */
@Repository
public class PurgaCuentaDemoRepository {

    @PersistenceContext
    private EntityManager em;

    /** Predicado sobre una columna {@code id_usuario}: el dueño o cualquier empleado suyo. */
    private static final String ES_USUARIO_DEL_TENANT =
            "id_usuario = :owner OR id_usuario IN "
                    + "(SELECT id FROM t_usuarios WHERE id_usuario_dueno = :owner)";

    @Transactional
    public void purgarTenant(String idDueno) {
        // 1. Anomalías de precio: referencian t_materia_prima sin cascada (bloquearían el paso 3).
        em.createNativeQuery(
                        "DELETE FROM t_ia_anomalia_precio WHERE id_materia_prima IN ("
                                + "SELECT id FROM t_materia_prima WHERE id_granja IN ("
                                + "SELECT id FROM t_granja WHERE " + ES_USUARIO_DEL_TENANT + "))")
                .setParameter("owner", idDueno)
                .executeUpdate();

        // 2. Auditoría de todos los usuarios del tenant (FK sin cascada hacia t_usuarios).
        em.createNativeQuery("DELETE FROM t_auditoria WHERE " + ES_USUARIO_DEL_TENANT)
                .setParameter("owner", idDueno)
                .executeUpdate();

        // 3. Granjas del tenant. Cascada → compras, fórmulas, fabricaciones, inventario,
        //    materias primas, proveedores, animales, registro_precio (historial ML), alertas y
        //    predicciones ML, archivos.
        em.createNativeQuery("DELETE FROM t_granja WHERE " + ES_USUARIO_DEL_TENANT)
                .setParameter("owner", idDueno)
                .executeUpdate();

        // 4. Cuentas de empleado vinculadas. Cascada → suscripciones/pagos, token_seguridad.
        em.createNativeQuery("DELETE FROM t_usuarios WHERE id_usuario_dueno = :owner")
                .setParameter("owner", idDueno)
                .executeUpdate();

        // 5. La cuenta del dueño. Cascada → suscripciones/pagos, token_seguridad.
        em.createNativeQuery("DELETE FROM t_usuarios WHERE id = :owner")
                .setParameter("owner", idDueno)
                .executeUpdate();
    }
}
