package com.gestionrestaurante.back.service;

import com.gestionrestaurante.back.dto.ConfiguracionEmpresaLogoDTO;
import com.gestionrestaurante.back.entity.ConfiguracionEmpresa;
import com.gestionrestaurante.back.exception.LogoEmpresaFormatoNoValidoException;
import com.gestionrestaurante.back.exception.LogoEmpresaNoConfiguradoException;
import com.gestionrestaurante.back.repository.ConfiguracionEmpresaRepository;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lectura/escritura del nombre y logo de la empresa. Es una fila unica (id=1, sembrada por
 * V39) mostrada en la cabecera de la app a cualquier usuario autenticado (back/fase5-1-back.md)
 * - la escritura queda restringida a Admin en el controller, no aqui. Encapsula tambien la
 * lectura del recurso fisico del logo (FileStorageService): el controller nunca debe conocer
 * ese servicio directamente, mismo patron que FacturaService.descargarArchivo (revision de
 * codigo tras la primera version de esta fase).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ConfiguracionEmpresaService {

    private static final Long ID_UNICO = 1L;

    private static final Set<String> CONTENT_TYPES_LOGO_VALIDOS =
            Set.of("image/jpeg", "image/png", "image/webp");

    private static final String SUBCARPETA_LOGO = "empresa";

    private final ConfiguracionEmpresaRepository configuracionEmpresaRepository;
    private final FileStorageService fileStorageService;

    public ConfiguracionEmpresa obtener() {
        return configuracionEmpresaRepository.findById(ID_UNICO)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta la fila de configuracion_empresa (id=1); revisar que la migracion V39 se aplico"));
    }

    /** Lanza {@link LogoEmpresaNoConfiguradoException} (404) si todavia no hay ningun logo. */
    public ConfiguracionEmpresaLogoDTO obtenerLogo() {
        ConfiguracionEmpresa configuracion = obtener();
        if (configuracion.getLogoRuta() == null) {
            throw new LogoEmpresaNoConfiguradoException();
        }
        Resource recurso = fileStorageService.cargarComoResource(configuracion.getLogoRuta());
        MediaType mediaType = MediaTypeFactory.getMediaType(configuracion.getLogoRuta())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return new ConfiguracionEmpresaLogoDTO(recurso, mediaType);
    }

    /**
     * {@code logo} es opcional: si es {@code null}, se conserva el logo ya configurado. Al
     * reemplazar un logo existente, el fichero anterior se borra solo despues de que el nuevo
     * se haya guardado con exito, para no dejar a la empresa sin logo si el guardado fallara.
     */
    @Transactional
    public ConfiguracionEmpresa actualizar(String nombreEmpresa, MultipartFile logo, String emailRemitente, Long adminId) {
        ConfiguracionEmpresa configuracion = obtener();
        configuracion.setNombreEmpresa(nombreEmpresa);
        // Blank -> null: "sin remitente configurado" (EmailServiceImpl cae a spring.mail.username),
        // no un valor vacio persistido - el front puede mandar "" al vaciar el campo.
        configuracion.setEmailRemitente(emailRemitente != null && !emailRemitente.isBlank() ? emailRemitente : null);

        if (logo != null && !logo.isEmpty()) {
            String contentType = logo.getContentType();
            if (contentType == null || !CONTENT_TYPES_LOGO_VALIDOS.contains(contentType)) {
                throw new LogoEmpresaFormatoNoValidoException();
            }

            String rutaAnterior = configuracion.getLogoRuta();
            String nuevaRuta = fileStorageService.guardar(logo, SUBCARPETA_LOGO);
            if (rutaAnterior != null) {
                // Best-effort (mismo criterio que la revocacion del refresh token de Google en
                // ConexionGoogleService): si no se puede borrar el logo anterior (p.ej. archivo
                // bloqueado), no debe abortar la actualizacion del nuevo, que ya se guardo bien.
                try {
                    fileStorageService.eliminar(rutaAnterior);
                } catch (RuntimeException ex) {
                    log.warn("No se ha podido borrar el logo anterior ({}), se continua igualmente", rutaAnterior, ex);
                }
            }
            configuracion.setLogoRuta(nuevaRuta);
            configuracion.setLogoNombreOriginal(logo.getOriginalFilename());
        }

        configuracion.setUpdatedAt(LocalDateTime.now());
        configuracion.setActualizadoPorAdminId(adminId);
        return configuracionEmpresaRepository.save(configuracion);
    }
}
