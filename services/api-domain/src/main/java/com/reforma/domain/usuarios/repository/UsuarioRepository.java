package com.reforma.domain.usuarios.repository;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.usuarios.entity.Usuario;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, String> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    /** Versión de sesión vigente (proyección liviana para validar JWT en cada request). */
    @Query("select u.tokenVersion from Usuario u where u.id = :id")
    Optional<Integer> findTokenVersionById(@Param("id") String id);

    boolean existsByEmailIgnoreCase(String email);

    /** Empleados vinculados a un dueño que ya aceptaron la invitación (activos). Para plan-gating. */
    long countByUsuarioDuenoIdAndActivoComoEmpleadoTrue(String idDueno);

    /** Todos los empleados vinculados a un dueño (incluye pendientes y desactivados), recientes primero. */
    java.util.List<Usuario> findByUsuarioDuenoIdOrderByFechaVinculacionDesc(String idDueno);

    /**
     * Cuentas dueñas (no empleados) de un plan dado registradas antes del corte.
     * Usado por la limpieza de cuentas DEMO vencidas (retención de prueba).
     */
    List<Usuario> findByPlanSuscripcionAndEsUsuarioEmpleadoFalseAndFechaRegistroBefore(
            PlanSuscripcion plan, Instant corte);
}
