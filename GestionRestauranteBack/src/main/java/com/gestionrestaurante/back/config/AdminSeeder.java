package com.gestionrestaurante.back.config;

import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.TipoUsuarioRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el primer usuario ADMIN si la tabla empleados esta vacia. Se hashea la contrasena
 * en caliente con el PasswordEncoder inyectado, en vez de precomputar un hash BCrypt a mano en SQL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final EmpleadoRepository empleadoRepository;
    private final TipoUsuarioRepository tipoUsuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin-seed.email}")
    private String adminEmail;

    @Value("${app.admin-seed.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (empleadoRepository.count() > 0) {
            return;
        }

        var tipoUsuarioAdmin = tipoUsuarioRepository.findByCodigo("ADMIN")
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el tipo de usuario ADMIN: revisa que la migracion V5 se haya aplicado"));

        Empleado admin = new Empleado();
        admin.setNombre("Admin");
        admin.setApellidos("Sistema");
        admin.setEmail(adminEmail);
        admin.setDniNie("00000000A");
        admin.setPuesto("Administrador");
        admin.setFechaAlta(LocalDate.now());
        admin.setActivo(true);
        admin.setTipoUsuario(tipoUsuarioAdmin);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));

        empleadoRepository.save(admin);
        log.info("Usuario ADMIN inicial creado: {}", adminEmail);
    }
}
