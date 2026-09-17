INSERT INTO tipos_usuarios (codigo, nombre, descripcion) VALUES
    ('ADMIN', 'Administrador', 'Acceso completo a todos los modulos'),
    ('EMPLEADO', 'Empleado', 'Acceso limitado a su propio fichaje');

INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('EMPLEADOS', 'Empleados', 'Alta, edicion y baja de empleados'),
    ('FICHAJES', 'Fichajes', 'Registro de entrada/salida y consulta de horas'),
    ('FACTURAS', 'Facturas de proveedores', 'Gestion de facturas de proveedores'),
    ('NOMINAS', 'Nominas', 'Gestion y envio de nominas');

-- ADMIN: edicion en las 4 vistas
INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo IN ('EMPLEADOS', 'FICHAJES', 'FACTURAS', 'NOMINAS')
WHERE tu.codigo = 'ADMIN';

-- EMPLEADO: edicion unicamente en Fichajes (el alcance "solo los suyos" se aplica en el servicio)
INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'FICHAJES'
WHERE tu.codigo = 'EMPLEADO';
