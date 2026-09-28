ALTER TABLE configuracion_empresa
    ADD COLUMN email_remitente VARCHAR(150) NULL;

-- Nullable a proposito: mientras no se configure, EmailServiceImpl usa spring.mail.username
-- (la cuenta SMTP autenticada) como remitente por defecto.
