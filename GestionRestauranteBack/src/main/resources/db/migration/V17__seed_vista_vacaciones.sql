INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('VACACIONES', 'Vacaciones', 'Solicitud y aprobacion de dias de vacaciones');

INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'VACACIONES'
WHERE tu.codigo IN ('ADMIN', 'EMPLEADO');
