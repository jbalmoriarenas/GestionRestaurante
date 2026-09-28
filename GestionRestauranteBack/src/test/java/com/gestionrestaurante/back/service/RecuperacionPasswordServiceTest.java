package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.TokenRecuperacionPassword;
import com.gestionrestaurante.back.exception.EnvioRecuperacionPasswordFallidoException;
import com.gestionrestaurante.back.exception.SolicitudRecuperacionDemasiadoFrecuenteException;
import com.gestionrestaurante.back.exception.TokenRecuperacionInvalidoException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.TokenRecuperacionPasswordRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cubre solicitar (email inexistente sin revelarlo, cooldown anti-spam, borrado de tokens
 * anteriores, hash nunca igual al token en claro, fallo de envio traducido) y restablecer
 * (token inexistente/usado/caducado, delegacion en EmpleadoService) con todos los colaboradores
 * mockeados - nunca se envia un email real en tests, mismo criterio que
 * ConexionGoogleServiceTest/ConfiguracionEmpresaServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class RecuperacionPasswordServiceTest {

    private static final int EXPIRACION_MINUTOS = 30;

    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private TokenRecuperacionPasswordRepository tokenRepository;
    @Mock
    private EmpleadoService empleadoService;
    @Mock
    private EmailService emailService;

    private RecuperacionPasswordService service;

    @BeforeEach
    void setUp() {
        service = new RecuperacionPasswordService(empleadoRepository, tokenRepository, empleadoService, emailService);
        ReflectionTestUtils.setField(service, "expiracionMinutos", EXPIRACION_MINUTOS);
    }

    private static Empleado empleado(long id) {
        Empleado empleado = new Empleado();
        empleado.setId(id);
        empleado.setNombre("Ana");
        empleado.setEmail("ana@test.com");
        return empleado;
    }

    @Test
    void solicitar_conEmailInexistenteNoLanzaExcepcionNiEnviaEmail() {
        // Revision de codigo, back/fase6-1-back.md: responde igual exista o no el email, para no
        // permitir enumerar que emails estan dados de alta como empleados activos.
        when(empleadoRepository.findByEmailAndActivoTrue("no-existe@test.com")).thenReturn(Optional.empty());

        service.solicitar("no-existe@test.com");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).enviarRecuperacionPassword(any(), anyString());
    }

    @Test
    void solicitar_conExitoBorraLosTokensAnterioresNoUsadosYGuardaUnoNuevoHasheado() {
        Empleado empleado = empleado(1L);
        when(empleadoRepository.findByEmailAndActivoTrue("ana@test.com")).thenReturn(Optional.of(empleado));
        TokenRecuperacionPassword anterior = new TokenRecuperacionPassword();
        when(tokenRepository.findByEmpleado_IdAndFechaUsoIsNull(1L)).thenReturn(List.of(anterior));

        service.solicitar("ana@test.com");

        verify(tokenRepository).deleteAll(List.of(anterior));

        ArgumentCaptor<TokenRecuperacionPassword> tokenCaptor = ArgumentCaptor.forClass(TokenRecuperacionPassword.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        TokenRecuperacionPassword guardado = tokenCaptor.getValue();
        assertThat(guardado.getEmpleado()).isEqualTo(empleado);
        assertThat(guardado.getFechaUso()).isNull();
        assertThat(guardado.getFechaExpiracion()).isAfter(LocalDateTime.now());

        ArgumentCaptor<String> tokenEnClaroCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).enviarRecuperacionPassword(eq(empleado), tokenEnClaroCaptor.capture());
        // El hash guardado nunca debe coincidir con el token en claro que recibe el email.
        assertThat(guardado.getTokenHash()).isNotEqualTo(tokenEnClaroCaptor.getValue());
    }

    @Test
    void solicitar_conSegundaSolicitudDentroDelCooldownLanzaExcepcion() {
        when(empleadoRepository.findByEmailAndActivoTrue("ana@test.com")).thenReturn(Optional.of(empleado(1L)));

        service.solicitar("ana@test.com");

        assertThatThrownBy(() -> service.solicitar("ana@test.com"))
                .isInstanceOf(SolicitudRecuperacionDemasiadoFrecuenteException.class);

        // Solo la primera solicitud debe haber llegado a generar token y enviar el email.
        verify(tokenRepository, times(1)).save(any());
        verify(emailService, times(1)).enviarRecuperacionPassword(any(), anyString());
    }

    @Test
    void solicitar_conFalloDeEnvioLanzaExcepcionTraducida() {
        Empleado empleado = empleado(1L);
        when(empleadoRepository.findByEmailAndActivoTrue("ana@test.com")).thenReturn(Optional.of(empleado));
        doThrow(new MailSendException("smtp caido"))
                .when(emailService).enviarRecuperacionPassword(any(), anyString());

        assertThatThrownBy(() -> service.solicitar("ana@test.com"))
                .isInstanceOf(EnvioRecuperacionPasswordFallidoException.class);
    }

    @Test
    void restablecer_conTokenInexistenteLanzaExcepcionSinLlamarAEmpleadoService() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restablecer("token-cualquiera", "nueva123"))
                .isInstanceOf(TokenRecuperacionInvalidoException.class);

        verify(empleadoService, never()).resetearPassword(anyLong(), anyString());
    }

    @Test
    void restablecer_conTokenYaUsadoLanzaExcepcionSinLlamarAEmpleadoService() {
        TokenRecuperacionPassword token = new TokenRecuperacionPassword();
        token.setFechaUso(LocalDateTime.now().minusMinutes(5));
        token.setFechaExpiracion(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.restablecer("token-cualquiera", "nueva123"))
                .isInstanceOf(TokenRecuperacionInvalidoException.class);

        verify(empleadoService, never()).resetearPassword(anyLong(), anyString());
    }

    @Test
    void restablecer_conTokenCaducadoLanzaExcepcionSinLlamarAEmpleadoService() {
        TokenRecuperacionPassword token = new TokenRecuperacionPassword();
        token.setFechaExpiracion(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.restablecer("token-cualquiera", "nueva123"))
                .isInstanceOf(TokenRecuperacionInvalidoException.class);

        verify(empleadoService, never()).resetearPassword(anyLong(), anyString());
    }

    @Test
    void restablecer_conTokenValidoDelegaEnEmpleadoServiceYMarcaElTokenComoUsado() {
        Empleado empleado = empleado(1L);
        TokenRecuperacionPassword token = new TokenRecuperacionPassword();
        token.setEmpleado(empleado);
        token.setFechaExpiracion(LocalDateTime.now().plusMinutes(10));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        service.restablecer("token-valido", "nuevaPassword123");

        verify(empleadoService).resetearPassword(1L, "nuevaPassword123");
        assertThat(token.getFechaUso()).isNotNull();
    }
}
