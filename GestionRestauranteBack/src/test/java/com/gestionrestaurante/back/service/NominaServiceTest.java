package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.NominaResponseDTO;
import com.gestionrestaurante.back.dto.ProcesamientoZipDTO;
import com.gestionrestaurante.back.entity.Empleado;
import com.gestionrestaurante.back.entity.EstadoNomina;
import com.gestionrestaurante.back.entity.Nomina;
import com.gestionrestaurante.back.exception.ArchivoNoValidoException;
import com.gestionrestaurante.back.exception.EmpleadoNotFoundException;
import com.gestionrestaurante.back.exception.EnvioNominaFallidoException;
import com.gestionrestaurante.back.exception.NominaNotFoundException;
import com.gestionrestaurante.back.exception.PendienteNoEncontradoException;
import com.gestionrestaurante.back.exception.ZipInvalidoException;
import com.gestionrestaurante.back.repository.EmpleadoRepository;
import com.gestionrestaurante.back.repository.NominaRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailSendException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cubre la logica de negocio real de NominaService (el upsert con su reset de estado
 * ENVIADA->ADJUNTADA, el matching de ZIP con sus tres motivos de pendiente, la asignacion
 * manual y el envio con exito/fallo) que NominaControllerTest no ejercita al mockear el
 * servicio entero, y que LocalFileStorageServiceTest tampoco cubre (solo prueba el
 * almacenamiento fisico). Mismo patron que VacacionesServiceTest: Mockito puro, sin contexto
 * de Spring.
 */
@ExtendWith(MockitoExtension.class)
class NominaServiceTest {

    @Mock
    private NominaRepository nominaRepository;
    @Mock
    private EmpleadoRepository empleadoRepository;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private EmailService emailService;

    private NominaService nominaService;

    @BeforeEach
    void setUp() {
        nominaService = new NominaService(nominaRepository, empleadoRepository, fileStorageService, emailService);
    }

    // ---------- subir ----------

    @Test
    void subir_archivoNoPdfLanzaExcepcionSinGuardarNada() {
        MultipartFile archivo = new MockMultipartFile("archivo", "nomina.txt", "text/plain", new byte[]{1});

        assertThatThrownBy(() -> nominaService.subir(1L, 1, 2026, archivo, 99L))
                .isInstanceOf(ArchivoNoValidoException.class);

        verify(fileStorageService, never()).guardar(any(), any());
        verify(nominaRepository, never()).save(any());
    }

    @Test
    void subir_empleadoInexistenteLanzaNotFound() {
        MultipartFile archivo = new MockMultipartFile("archivo", "nomina.pdf", "application/pdf", new byte[]{1});
        when(empleadoRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nominaService.subir(1L, 1, 2026, archivo, 99L))
                .isInstanceOf(EmpleadoNotFoundException.class);
    }

    @Test
    void subir_empleadoSinNominaPreviaCreaFilaAdjuntadaSinBorrarNada() {
        MultipartFile archivo = new MockMultipartFile("archivo", "nomina.pdf", "application/pdf", new byte[]{1});
        Empleado empleado = empleadoConId(5L);
        when(empleadoRepository.findById(5L)).thenReturn(Optional.of(empleado));
        when(fileStorageService.guardar(archivo, "nominas")).thenReturn("nominas/nuevo.pdf");
        when(nominaRepository.findByEmpleado_IdAndPeriodoMesAndPeriodoAnio(5L, 1, 2026)).thenReturn(Optional.empty());
        when(nominaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NominaResponseDTO respuesta = nominaService.subir(5L, 1, 2026, archivo, 99L);

        assertThat(respuesta.estado()).isEqualTo(EstadoNomina.ADJUNTADA);
        verify(fileStorageService, never()).eliminar(any());
    }

    @Test
    void subir_sobreNominaYaEnviadaLaDejaAdjuntadaYBorraElArchivoAntiguoTrasGuardar() {
        MultipartFile archivo = new MockMultipartFile("archivo", "nomina.pdf", "application/pdf", new byte[]{1});
        Empleado empleado = empleadoConId(5L);
        Nomina existente = nominaEnviada(empleado, "nominas/viejo.pdf");
        when(empleadoRepository.findById(5L)).thenReturn(Optional.of(empleado));
        when(fileStorageService.guardar(archivo, "nominas")).thenReturn("nominas/nuevo.pdf");
        when(nominaRepository.findByEmpleado_IdAndPeriodoMesAndPeriodoAnio(5L, 1, 2026)).thenReturn(Optional.of(existente));
        when(nominaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NominaResponseDTO respuesta = nominaService.subir(5L, 1, 2026, archivo, 99L);

        assertThat(respuesta.estado()).isEqualTo(EstadoNomina.ADJUNTADA);
        assertThat(respuesta.fechaEnvio()).isNull();
        assertThat(existente.getEnviadaPorAdminId()).isNull();
        // El fichero antiguo se borra (aqui, sin transaccion real activa, de forma sincrona
        // justo despues de un save() que ya tuvo exito - ver NominaService.borrarTrasCommit).
        verify(fileStorageService).eliminar("nominas/viejo.pdf");
    }

    // ---------- procesarZip ----------

    @Test
    void procesarZip_zipVacioLanzaZipInvalido() {
        MultipartFile zipVacio = new MockMultipartFile("archivo", "vacio.zip", "application/zip", new byte[0]);

        assertThatThrownBy(() -> nominaService.procesarZip(zipVacio, 99L))
                .isInstanceOf(ZipInvalidoException.class);
    }

    @Test
    void procesarZip_entradaQueMatcheaAsignaAutomaticamenteSinPendientes() throws IOException {
        Empleado empleado = empleadoConDni(3L, "87654321X");
        when(empleadoRepository.findByDniNieAndActivoTrue("87654321X")).thenReturn(Optional.of(empleado));
        when(nominaRepository.findByEmpleado_IdAndPeriodoMesAndPeriodoAnio(3L, 9, 2026)).thenReturn(Optional.empty());
        when(fileStorageService.guardar(any(), eq("nominas"))).thenReturn("nominas/nuevo.pdf");
        when(nominaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MultipartFile zip = zipConEntrada("87654321X_2026-09.pdf", "contenido");

        ProcesamientoZipDTO resultado = nominaService.procesarZip(zip, 99L);

        assertThat(resultado.asignadosAutomaticamente()).isEqualTo(1);
        assertThat(resultado.pendientes()).isEmpty();
    }

    @Test
    void procesarZip_entradaSinExtensionPdfQuedaPendienteConMotivoNoEsPdf() throws IOException {
        when(fileStorageService.guardar(any(), eq("nominas/pendientes"))).thenReturn("nominas/pendientes/uuid-generado.pdf");

        MultipartFile zip = zipConEntrada("acceso-directo.lnk", "contenido");

        ProcesamientoZipDTO resultado = nominaService.procesarZip(zip, 99L);

        assertThat(resultado.asignadosAutomaticamente()).isZero();
        assertThat(resultado.pendientes()).hasSize(1);
        assertThat(resultado.pendientes().get(0).motivo()).isEqualTo(NominaService.MOTIVO_NO_ES_PDF);
        // El nombre fisico de un pendiente es siempre "*.pdf" sin importar la extension real
        // de la entrada (NominaService.guardarPendiente), para que asignarPendiente lo
        // encuentre despues: se fuerza pasando un nombre sintetico a FileStorageService.
        verify(fileStorageService).guardar(
                argThat((MultipartFile f) -> "pendiente.pdf".equals(f.getOriginalFilename())), eq("nominas/pendientes"));
    }

    @Test
    void procesarZip_entradaPdfConNombreNoValidoQuedaPendienteConMotivoCorrecto() throws IOException {
        when(fileStorageService.guardar(any(), eq("nominas/pendientes"))).thenReturn("nominas/pendientes/uuid.pdf");

        MultipartFile zip = zipConEntrada("nomina_de_maria.pdf", "contenido");

        ProcesamientoZipDTO resultado = nominaService.procesarZip(zip, 99L);

        assertThat(resultado.pendientes()).hasSize(1);
        assertThat(resultado.pendientes().get(0).motivo()).isEqualTo(NominaService.MOTIVO_NOMBRE_NO_VALIDO);
    }

    @Test
    void procesarZip_entradaConDniInexistenteQuedaPendienteConMotivoEmpleadoNoEncontrado() throws IOException {
        when(empleadoRepository.findByDniNieAndActivoTrue("12345678A")).thenReturn(Optional.empty());
        when(fileStorageService.guardar(any(), eq("nominas/pendientes"))).thenReturn("nominas/pendientes/uuid.pdf");

        MultipartFile zip = zipConEntrada("12345678A_2026-09.pdf", "contenido");

        ProcesamientoZipDTO resultado = nominaService.procesarZip(zip, 99L);

        assertThat(resultado.pendientes()).hasSize(1);
        assertThat(resultado.pendientes().get(0).motivo()).isEqualTo(NominaService.MOTIVO_EMPLEADO_NO_ENCONTRADO);
    }

    // ---------- asignarPendiente ----------

    @Test
    void asignarPendiente_tempIdInexistenteLanzaPendienteNoEncontrado() {
        Empleado empleado = empleadoConId(5L);
        when(empleadoRepository.findById(5L)).thenReturn(Optional.of(empleado));
        when(fileStorageService.mover(anyString(), eq("nominas")))
                .thenThrow(new UncheckedIOException(new IOException("no existe")));

        assertThatThrownBy(() -> nominaService.asignarPendiente("uuid-que-no-existe", 5L, 1, 2026, 99L))
                .isInstanceOf(PendienteNoEncontradoException.class);
    }

    @Test
    void asignarPendiente_valido_creaLaNominaAdjuntada() {
        Empleado empleado = empleadoConId(5L);
        when(empleadoRepository.findById(5L)).thenReturn(Optional.of(empleado));
        when(fileStorageService.mover("nominas/pendientes/uuid.pdf", "nominas")).thenReturn("nominas/uuid.pdf");
        when(nominaRepository.findByEmpleado_IdAndPeriodoMesAndPeriodoAnio(5L, 1, 2026)).thenReturn(Optional.empty());
        when(nominaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NominaResponseDTO respuesta = nominaService.asignarPendiente("uuid", 5L, 1, 2026, 99L);

        assertThat(respuesta.estado()).isEqualTo(EstadoNomina.ADJUNTADA);
    }

    // ---------- enviar ----------

    @Test
    void enviar_nominaAdjuntadaConExitoQuedaEnviadaConAuditoria() {
        Empleado empleado = empleadoConId(5L);
        Nomina nomina = nominaAdjuntada(empleado, "nominas/archivo.pdf");
        when(nominaRepository.findWithEmpleadoById(10L)).thenReturn(Optional.of(nomina));
        when(fileStorageService.cargarComoResource("nominas/archivo.pdf")).thenReturn(new ByteArrayResource(new byte[]{1}));
        when(nominaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NominaResponseDTO respuesta = nominaService.enviar(10L, 99L);

        assertThat(respuesta.estado()).isEqualTo(EstadoNomina.ENVIADA);
        assertThat(nomina.getFechaEnvio()).isNotNull();
        assertThat(nomina.getEnviadaPorAdminId()).isEqualTo(99L);
        verify(emailService).enviarNomina(
                eq(empleado), any(), eq(nomina.getNombreArchivoOriginal()), eq(nomina.getPeriodoMes()), eq(nomina.getPeriodoAnio()));
    }

    @Test
    void enviar_fallaElEnvioDejaLaNominaSinCambiosYLanzaEnvioFallido() {
        Empleado empleado = empleadoConId(5L);
        Nomina nomina = nominaAdjuntada(empleado, "nominas/archivo.pdf");
        when(nominaRepository.findWithEmpleadoById(10L)).thenReturn(Optional.of(nomina));
        when(fileStorageService.cargarComoResource("nominas/archivo.pdf")).thenReturn(new ByteArrayResource(new byte[]{1}));
        doThrow(new MailSendException("smtp caido"))
                .when(emailService).enviarNomina(any(), any(), anyString(), anyInt(), anyInt());

        assertThatThrownBy(() -> nominaService.enviar(10L, 99L))
                .isInstanceOf(EnvioNominaFallidoException.class);

        assertThat(nomina.getEstado()).isEqualTo(EstadoNomina.ADJUNTADA);
        assertThat(nomina.getFechaEnvio()).isNull();
        verify(nominaRepository, never()).save(any());
    }

    @Test
    void enviar_nominaInexistenteLanzaNotFound() {
        when(nominaRepository.findWithEmpleadoById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> nominaService.enviar(99L, 1L))
                .isInstanceOf(NominaNotFoundException.class);
    }

    // ---------- helpers ----------

    private static Empleado empleadoConId(Long id) {
        Empleado empleado = new Empleado();
        empleado.setId(id);
        empleado.setNombre("Ana");
        empleado.setApellidos("Lopez");
        empleado.setEmail("ana@test.com");
        empleado.setActivo(true);
        return empleado;
    }

    private static Empleado empleadoConDni(Long id, String dniNie) {
        Empleado empleado = empleadoConId(id);
        empleado.setDniNie(dniNie);
        return empleado;
    }

    private static Nomina nominaEnviada(Empleado empleado, String rutaArchivo) {
        Nomina nomina = new Nomina();
        nomina.setId(1L);
        nomina.setEmpleado(empleado);
        nomina.setPeriodoMes(1);
        nomina.setPeriodoAnio(2026);
        nomina.setRutaArchivo(rutaArchivo);
        nomina.setNombreArchivoOriginal("vieja.pdf");
        nomina.setEstado(EstadoNomina.ENVIADA);
        nomina.setFechaSubida(LocalDateTime.now().minusDays(10));
        nomina.setSubidaPorAdminId(1L);
        nomina.setFechaEnvio(LocalDateTime.now().minusDays(5));
        nomina.setEnviadaPorAdminId(1L);
        return nomina;
    }

    private static Nomina nominaAdjuntada(Empleado empleado, String rutaArchivo) {
        Nomina nomina = new Nomina();
        nomina.setId(10L);
        nomina.setEmpleado(empleado);
        nomina.setPeriodoMes(9);
        nomina.setPeriodoAnio(2026);
        nomina.setRutaArchivo(rutaArchivo);
        nomina.setNombreArchivoOriginal("nomina.pdf");
        nomina.setEstado(EstadoNomina.ADJUNTADA);
        nomina.setFechaSubida(LocalDateTime.now());
        nomina.setSubidaPorAdminId(1L);
        return nomina;
    }

    private static MultipartFile zipConEntrada(String nombreEntrada, String contenido) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bytes)) {
            zos.putNextEntry(new ZipEntry(nombreEntrada));
            zos.write(contenido.getBytes());
            zos.closeEntry();
        }
        return new MockMultipartFile("archivo", "nominas.zip", "application/zip", bytes.toByteArray());
    }
}
