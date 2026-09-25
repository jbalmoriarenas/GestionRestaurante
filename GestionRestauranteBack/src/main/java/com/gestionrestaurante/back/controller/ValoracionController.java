package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.ConexionEstadoDTO;
import com.gestionrestaurante.back.dto.ConexionUrlDTO;
import com.gestionrestaurante.back.dto.ResultadoSincronizacionDTO;
import com.gestionrestaurante.back.dto.ResumenValoracionesDTO;
import com.gestionrestaurante.back.dto.ValoracionDTO;
import com.gestionrestaurante.back.exception.ConexionGoogleNoDisponibleException;
import com.gestionrestaurante.back.exception.EstadoOAuthInvalidoException;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.ConexionGoogleService;
import com.gestionrestaurante.back.service.ValoracionService;
import com.gestionrestaurante.back.service.ValoracionSincronizacionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ValoracionController {

    @Value("${app.front.base-url}")
    private String frontBaseUrl;

    private final ConexionGoogleService conexionGoogleService;
    private final ValoracionSincronizacionService sincronizacionService;
    private final ValoracionService valoracionService;

    @GetMapping(Wuris.Valoraciones.CONEXION)
    @PreAuthorize("hasAuthority('VALORACIONES:EDICION')")
    public ConexionEstadoDTO obtenerEstadoConexion() {
        return conexionGoogleService.obtenerEstado();
    }

    @GetMapping(Wuris.Valoraciones.CONEXION_URL)
    @PreAuthorize("hasAuthority('VALORACIONES:EDICION')")
    public ConexionUrlDTO obtenerUrlConexion(Authentication authentication) {
        return new ConexionUrlDTO(conexionGoogleService.iniciarConexion(CurrentUser.empleadoId(authentication)));
    }

    /**
     * Sin @PreAuthorize a proposito (back/fase4-1-back.md, "Decisiones tecnicas"): Google
     * redirige aqui el navegador tras el consentimiento, una navegacion de pagina completa que
     * no lleva el header Authorization del interceptor del front. La seguridad depende de que
     * {@code state} sea un valor de un solo uso emitido por una llamada previa ya autenticada
     * a /conexion/url (ver ConexionGoogleService), no de un JWT en esta peticion.
     *
     * <p>{@code code} y {@code state} son opcionales porque, si el Admin cancela el
     * consentimiento en la pantalla de Google, esta redirige aqui solo con {@code error}
     * (p.ej. {@code access_denied}), sin {@code code} - con ambos parametros obligatorios el
     * binding de Spring fallaria antes de entrar al metodo, mostrando una pagina de error en
     * crudo en mitad de una redireccion de navegador en vez de volver, como en cualquier otro
     * fallo de este endpoint, a {@code <front>/valoraciones?error=...}.
     */
    @GetMapping(Wuris.Valoraciones.CONEXION_CALLBACK)
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        String destino;
        if (error != null) {
            // Fallo del lado de Google (consentimiento cancelado, etc.): el propio valor que
            // manda Google (p.ej. "access_denied") ya es un codigo estable y no sensible, se
            // reenvia tal cual - nunca se invoca completarConexion.
            destino = frontBaseUrl + "/valoraciones?error=" + error;
        } else if (code == null || state == null) {
            destino = frontBaseUrl + "/valoraciones?error=solicitud_incompleta";
        } else {
            try {
                conexionGoogleService.completarConexion(code, state);
                destino = frontBaseUrl + "/valoraciones?conectado=true";
            } catch (RuntimeException e) {
                destino = frontBaseUrl + "/valoraciones?error=" + codigoError(e);
            }
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destino)).build();
    }

    /** Codigo de error estable para la URL de redireccion - nunca el nombre de la clase de
     * excepcion (detalle interno de implementacion, ademas de variar si se refactoriza). */
    private static String codigoError(RuntimeException e) {
        if (e instanceof EstadoOAuthInvalidoException) {
            return "estado_invalido";
        }
        if (e instanceof ConexionGoogleNoDisponibleException) {
            return "google_no_disponible";
        }
        return "desconocido";
    }

    @DeleteMapping(Wuris.Valoraciones.CONEXION)
    @PreAuthorize("hasAuthority('VALORACIONES:EDICION')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desconectar() {
        conexionGoogleService.desconectar();
    }

    @PostMapping(Wuris.Valoraciones.SINCRONIZAR)
    @PreAuthorize("hasAuthority('VALORACIONES:EDICION')")
    public ResultadoSincronizacionDTO sincronizar() {
        return sincronizacionService.sincronizar();
    }

    @GetMapping(Wuris.Valoraciones.BASE)
    @PreAuthorize("hasAuthority('VALORACIONES:EDICION')")
    public Page<ValoracionDTO> listar(
            @RequestParam(required = false) @Min(1) @Max(5) Integer puntuacion,
            @PageableDefault(size = 10, sort = "fechaPublicacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return valoracionService.listar(puntuacion, pageable);
    }

    @GetMapping(Wuris.Valoraciones.RESUMEN)
    @PreAuthorize("hasAuthority('VALORACIONES:EDICION')")
    public ResumenValoracionesDTO obtenerResumen() {
        return valoracionService.obtenerResumen();
    }
}
