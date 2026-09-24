INSERT INTO vistas (codigo, nombre, descripcion) VALUES
    ('VALORACIONES', 'Valoraciones', 'Consulta de reseñas de Google Business Profile del restaurante');

INSERT INTO permisos (tipo_usuario_id, vista_id, nivel_acceso)
SELECT tu.id, v.id, 'EDICION'
FROM tipos_usuarios tu
JOIN vistas v ON v.codigo = 'VALORACIONES'
WHERE tu.codigo = 'ADMIN';
