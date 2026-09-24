package com.gestionrestaurante.back.controller;

import com.gestionrestaurante.back.Wuris;
import com.gestionrestaurante.back.dto.ConexionEstadoDTO;
import com.gestionrestaurante.back.dto.ConexionUrlDTO;
import com.gestionrestaurante.back.dto.ResultadoSincronizacionDTO;
import com.gestionrestaurante.back.dto.ResumenValoracionesDTO;
import com.gestionrestaurante.back.dto.ValoracionDTO;
import com.gestionrestaurante.back.security.CurrentUser;
import com.gestionrestaurante.back.service.ConexionGoogleService;
import com.gestionrestaurante.back.service.ValoracionService;
import com.gestionrestaurante.back.service.ValoracionSincronizacionService;
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
     */
    @GetMapping(Wuris.Valoraciones.CONEXION_CALLBACK)
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        String destino;
        try {
            conexionGoogleService.completarConexion(code, state);
            destino = frontBaseUrl + "/valoraciones?conectado=true";
        } catch (RuntimeException e) {
            destino = frontBaseUrl + "/valoraciones?error=" + e.getClass().getSimpleName();
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destino)).build();
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
            @RequestParam(required = false) Integer puntuacion,
            @PageableDefault(size = 10, sort = "fechaPublicacion", direction = Sort.Direction.DESC) Pageable pageable) {
        return valoracionService.listar(puntuacion, pageable);
    }

    @GetMapping(Wuris.Valoraciones.RESUMEN)
    @PreAuthorize("hasAuthority('VALORACIONES:EDICION')")
    public ResumenValoracionesDTO obtenerResumen() {
        return valoracionService.obtenerResumen();
    }
}
