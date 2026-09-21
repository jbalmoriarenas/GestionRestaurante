package com.gestionrestaurante.back.dto;

import java.util.List;

public record ProcesamientoZipDTO(
        int asignadosAutomaticamente,
        List<NominaPendienteDTO> pendientes
) {
}
