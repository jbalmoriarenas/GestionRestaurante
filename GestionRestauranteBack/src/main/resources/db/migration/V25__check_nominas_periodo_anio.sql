-- Mismo rango 2000-2100 que ya valida el backend (NominaController, AsignarPendienteRequestDTO)
-- y que usa VacacionesController para su propio filtro de anio: sin este CHECK, periodo_anio
-- no tenia ningun limite razonable a nivel de BBDD.
ALTER TABLE nominas ADD CONSTRAINT chk_nominas_periodo_anio CHECK (periodo_anio BETWEEN 2000 AND 2100);
