package com.gestionrestaurante.back.scheduler;

import com.gestionrestaurante.back.service.ValoracionSincronizacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Primera tarea programada del proyecto (back/fase4-1-back.md). Delega toda la logica en
 * {@link ValoracionSincronizacionService#sincronizar()}, el mismo metodo que usa el endpoint de
 * sincronizacion manual - este scheduler no contiene ninguna logica propia.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValoracionSincronizacionScheduler {

    private final ValoracionSincronizacionService sincronizacionService;

    @Scheduled(cron = "${app.valoraciones.sync-cron:0 0 */6 * * *}")
    public void sincronizarPeriodicamente() {
        log.info("Sincronizacion periodica de valoraciones: inicio");
        sincronizacionService.sincronizar();
        log.info("Sincronizacion periodica de valoraciones: fin");
    }
}
