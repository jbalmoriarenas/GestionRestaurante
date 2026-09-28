package com.gestionrestaurante.back.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestionrestaurante.back.dto.ConfiguracionEmpresaLogoDTO;
import com.gestionrestaurante.back.entity.ConfiguracionEmpresa;
import com.gestionrestaurante.back.exception.LogoEmpresaFormatoNoValidoException;
import com.gestionrestaurante.back.exception.LogoEmpresaNoConfiguradoException;
import com.gestionrestaurante.back.repository.ConfiguracionEmpresaRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Cubre actualizar (conservar el logo si no se adjunta uno nuevo, borrar el anterior solo
 * despues de guardar el nuevo, rechazar un content-type no permitido) con FileStorageService
 * mockeado - no se toca el disco en tests, mismo criterio que ConexionGoogleServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class ConfiguracionEmpresaServiceTest {

    private static final Long ADMIN_ID = 1L;

    @Mock
    private ConfiguracionEmpresaRepository configuracionEmpresaRepository;
    @Mock
    private FileStorageService fileStorageService;

    private ConfiguracionEmpresaService service;

    @BeforeEach
    void setUp() {
        service = new ConfiguracionEmpresaService(configuracionEmpresaRepository, fileStorageService);
    }

    private static ConfiguracionEmpresa configuracionExistente(String logoRuta) {
        ConfiguracionEmpresa configuracion = new ConfiguracionEmpresa();
        configuracion.setId(1L);
        configuracion.setNombreEmpresa("Sidrería La Zapica");
        configuracion.setLogoRuta(logoRuta);
        return configuracion;
    }

    @Test
    void actualizar_sinLogoConservaElLogoYaConfigurado() {
        ConfiguracionEmpresa configuracion = configuracionExistente("empresa/logo-actual.png");
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracion));
        when(configuracionEmpresaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConfiguracionEmpresa resultado = service.actualizar("Nuevo Nombre", null, null, ADMIN_ID);

        assertThat(resultado.getNombreEmpresa()).isEqualTo("Nuevo Nombre");
        assertThat(resultado.getLogoRuta()).isEqualTo("empresa/logo-actual.png");
        verify(fileStorageService, never()).guardar(any(), anyString());
        verify(fileStorageService, never()).eliminar(anyString());
    }

    @Test
    void actualizar_conLogoNuevoBorraElAnteriorDespuesDeGuardarElNuevo() {
        ConfiguracionEmpresa configuracion = configuracionExistente("empresa/logo-viejo.png");
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracion));
        when(configuracionEmpresaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageService.guardar(any(), anyString())).thenReturn("empresa/logo-nuevo.png");

        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1, 2, 3});
        ConfiguracionEmpresa resultado = service.actualizar("Sidrería La Zapica", logo, null, ADMIN_ID);

        assertThat(resultado.getLogoRuta()).isEqualTo("empresa/logo-nuevo.png");
        verify(fileStorageService).guardar(logo, "empresa");
        verify(fileStorageService).eliminar("empresa/logo-viejo.png");
    }

    @Test
    void actualizar_conContentTypeNoPermitidoLanzaExcepcionSinGuardarNada() {
        ConfiguracionEmpresa configuracion = configuracionExistente(null);
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracion));

        MockMultipartFile archivoInvalido =
                new MockMultipartFile("logo", "factura.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> service.actualizar("Sidrería La Zapica", archivoInvalido, null, ADMIN_ID))
                .isInstanceOf(LogoEmpresaFormatoNoValidoException.class);
        verify(fileStorageService, never()).guardar(any(), anyString());
        verify(configuracionEmpresaRepository, never()).save(any());
    }

    @Test
    void actualizar_conElPrimerLogoNuncaLlamaAEliminar() {
        ConfiguracionEmpresa configuracion = configuracionExistente(null);
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracion));
        when(configuracionEmpresaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageService.guardar(any(), anyString())).thenReturn("empresa/logo-nuevo.png");

        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1, 2, 3});
        ConfiguracionEmpresa resultado = service.actualizar("Sidrería La Zapica", logo, null, ADMIN_ID);

        assertThat(resultado.getLogoRuta()).isEqualTo("empresa/logo-nuevo.png");
        verify(fileStorageService, never()).eliminar(anyString());
    }

    @Test
    void actualizar_siFallaBorrarElLogoAnteriorNoAbortaLaActualizacion() {
        ConfiguracionEmpresa configuracion = configuracionExistente("empresa/logo-viejo.png");
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracion));
        when(configuracionEmpresaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileStorageService.guardar(any(), anyString())).thenReturn("empresa/logo-nuevo.png");
        doThrow(new java.io.UncheckedIOException(new java.io.IOException("bloqueado")))
                .when(fileStorageService).eliminar("empresa/logo-viejo.png");

        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1, 2, 3});
        ConfiguracionEmpresa resultado = service.actualizar("Sidrería La Zapica", logo, null, ADMIN_ID);

        assertThat(resultado.getLogoRuta()).isEqualTo("empresa/logo-nuevo.png");
        verify(configuracionEmpresaRepository).save(any());
    }

    @Test
    void actualizar_conEmailRemitenteLoGuardaTalCual() {
        ConfiguracionEmpresa configuracion = configuracionExistente(null);
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracion));
        when(configuracionEmpresaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConfiguracionEmpresa resultado = service.actualizar("Sidrería La Zapica", null, "pedidos@zapica.com", ADMIN_ID);

        assertThat(resultado.getEmailRemitente()).isEqualTo("pedidos@zapica.com");
    }

    @Test
    void actualizar_conEmailRemitenteEnBlancoLoGuardaComoNull() {
        ConfiguracionEmpresa configuracion = configuracionExistente(null);
        configuracion.setEmailRemitente("anterior@zapica.com");
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracion));
        when(configuracionEmpresaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConfiguracionEmpresa resultado = service.actualizar("Sidrería La Zapica", null, "   ", ADMIN_ID);

        assertThat(resultado.getEmailRemitente()).isNull();
    }

    @Test
    void obtenerLogo_sinLogoConfiguradoLanzaExcepcion() {
        when(configuracionEmpresaRepository.findById(1L)).thenReturn(Optional.of(configuracionExistente(null)));

        assertThatThrownBy(() -> service.obtenerLogo()).isInstanceOf(LogoEmpresaNoConfiguradoException.class);
    }

    @Test
    void obtenerLogo_conLogoConfiguradoDevuelveElRecursoYElMediaType() {
        when(configuracionEmpresaRepository.findById(1L))
                .thenReturn(Optional.of(configuracionExistente("empresa/logo.png")));
        org.springframework.core.io.Resource recursoEsperado = new org.springframework.core.io.ByteArrayResource(new byte[] {1});
        when(fileStorageService.cargarComoResource("empresa/logo.png")).thenReturn(recursoEsperado);

        ConfiguracionEmpresaLogoDTO logo = service.obtenerLogo();

        assertThat(logo.recurso()).isSameAs(recursoEsperado);
        assertThat(logo.mediaType().toString()).isEqualTo("image/png");
    }
}
