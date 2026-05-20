package com.reforma.domain.config;

import com.reforma.domain.common.domain.PlanSuscripcion;
import com.reforma.domain.common.domain.TipoUsuario;
import com.reforma.domain.common.util.IdGenerator;
import com.reforma.domain.granjas.entity.Granja;
import com.reforma.domain.granjas.repository.GranjaRepository;
import com.reforma.domain.usuarios.entity.Usuario;
import com.reforma.domain.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Carga usuario demo en desarrollo (demo@reforma.local / Demo1234!).
 * Desactivar en producción usando perfil distinto de {@code dev}.
 */
@Configuration
@Profile("dev")
@RequiredArgsConstructor
public class DevDataLoader {

    private final UsuarioRepository usuarioRepository;
    private final GranjaRepository granjaRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedDemo() {
        return args -> {
            if (usuarioRepository.findByEmailIgnoreCase("demo@reforma.local").isPresent()) {
                return;
            }
            var usuario = Usuario.builder()
                    .id("u_demo")
                    .email("demo@reforma.local")
                    .passwordHash(passwordEncoder.encode("Demo1234!"))
                    .nombreUsuario("Demo")
                    .apellidoUsuario("Reforma")
                    .tipoUsuario(TipoUsuario.CLIENTE)
                    .planSuscripcion(PlanSuscripcion.BUSINESS)
                    .maxGranjas(3)
                    .activo(true)
                    .emailVerificado(true)
                    .fechaRegistro(Instant.now())
                    .esUsuarioEmpleado(false)
                    .activoComoEmpleado(false)
                    .build();
            usuarioRepository.save(usuario);
            granjaRepository.save(Granja.builder()
                    .id("g_demo")
                    .usuario(usuario)
                    .nombreGranja("Granja Demo")
                    .descripcion("Granja para pruebas locales")
                    .fechaCreacion(Instant.now())
                    .activa(true)
                    .build());
        };
    }
}
