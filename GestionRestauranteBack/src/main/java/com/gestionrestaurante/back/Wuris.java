package com.gestionrestaurante.back;

/**
 * Punto unico de las URLs de la API. Ningun controller ni configuracion de seguridad
 * debe escribir una ruta "a mano": siempre se referencia una constante de aqui, para poder
 * ver y cambiar todos los endpoints de la aplicacion desde un unico archivo.
 */
public final class Wuris {

    private Wuris() {
    }

    private static final String API = "/api";

    public static final class Auth {
        private Auth() {
        }

        public static final String BASE = API + "/auth";
        public static final String LOGIN = BASE + "/login";
        /** Patron usado en SecurityConfig para dejar publico todo el paquete de autenticacion. */
        public static final String PUBLIC_PATTERN = BASE + "/**";
    }

    public static final class Empleados {
        private Empleados() {
        }

        public static final String BASE = API + "/empleados";
        public static final String BY_ID = BASE + "/{id}";
        public static final String BAJA = BASE + "/{id}/baja";
        public static final String PASSWORD = BASE + "/{id}/password";
        public static final String HORARIO_TEORICO = BASE + "/{id}/horario-teorico";
        public static final String DIAS_VACACIONES_ANUALES = BASE + "/{id}/dias-vacaciones-anuales";
    }

    public static final class TiposUsuarios {
        private TiposUsuarios() {
        }

        public static final String BASE = API + "/tipos-usuarios";
    }

    public static final class Fichajes {
        private Fichajes() {
        }

        public static final String BASE = API + "/fichajes";
        public static final String ENTRADA = BASE + "/entrada";
        public static final String SALIDA = BASE + "/salida";
        public static final String ACTUAL = BASE + "/actual";
        public static final String MIOS = BASE + "/mios";
        public static final String MIOS_HOY = BASE + "/mios/hoy";

        public static final String ADMIN = BASE + "/admin";
        public static final String ADMIN_RESUMEN_DIARIO = ADMIN + "/resumen/diario";
        public static final String ADMIN_RESUMEN_SEMANAL = ADMIN + "/resumen/semanal";
        public static final String ADMIN_RESUMEN_MENSUAL = ADMIN + "/resumen/mensual";
        public static final String ADMIN_BY_ID = ADMIN + "/{id}";
    }

    public static final class Configuracion {
        private Configuracion() {
        }

        public static final String UBICACION = API + "/configuracion/ubicacion";
        public static final String VACACIONES = API + "/configuracion/vacaciones";
        public static final String EMPRESA = API + "/configuracion/empresa";
        public static final String EMPRESA_LOGO = EMPRESA + "/logo";
    }

    public static final class Vacaciones {
        private Vacaciones() {
        }

        public static final String BASE = API + "/vacaciones";
        public static final String RESUMEN = BASE + "/resumen";
        public static final String CALENDARIO = BASE + "/calendario";
        public static final String SOLICITUDES = BASE + "/solicitudes";
        public static final String SOLICITUD_BY_ID = SOLICITUDES + "/{id}";

        public static final String ADMIN = BASE + "/admin";
        public static final String ADMIN_CALENDARIO = ADMIN + "/calendario";
        public static final String ADMIN_RESUMEN = ADMIN + "/resumen";
        public static final String ADMIN_SOLICITUDES = ADMIN + "/solicitudes";
        public static final String ADMIN_SOLICITUDES_PENDIENTES = ADMIN_SOLICITUDES + "/pendientes";
        public static final String ADMIN_SOLICITUD_APROBAR = ADMIN_SOLICITUDES + "/{id}/aprobar";
        public static final String ADMIN_SOLICITUD_RECHAZAR = ADMIN_SOLICITUDES + "/{id}/rechazar";
    }

    public static final class Nominas {
        private Nominas() {
        }

        public static final String BASE = API + "/nominas";
        public static final String ZIP = BASE + "/zip";
        public static final String PENDIENTE_ASIGNAR = BASE + "/pendientes/{tempId}/asignar";
        public static final String BY_ID_DESCARGAR = BASE + "/{id}/descargar";
        public static final String BY_ID_ENVIAR = BASE + "/{id}/enviar";
    }

    public static final class Proveedores {
        private Proveedores() {
        }

        public static final String BASE = API + "/proveedores";
    }

    public static final class Facturas {
        private Facturas() {
        }

        public static final String BASE = API + "/facturas";
        public static final String EXTRAER = BASE + "/extraer";
        public static final String EXPORTAR = BASE + "/exportar";
        public static final String EXPORTAR_ZIP = BASE + "/exportar-zip";
        public static final String BY_ID = BASE + "/{id}";
        public static final String BY_ID_ESTADO_PAGO = BASE + "/{id}/estado-pago";
        public static final String BY_ID_ARCHIVO = BASE + "/{id}/archivo";
    }

    public static final class Valoraciones {
        private Valoraciones() {
        }

        public static final String BASE = API + "/valoraciones";
        public static final String RESUMEN = BASE + "/resumen";
        public static final String SINCRONIZAR = BASE + "/sincronizar";

        public static final String CONEXION = BASE + "/conexion";
        public static final String CONEXION_URL = CONEXION + "/url";
        public static final String CONEXION_CALLBACK = CONEXION + "/callback";
        /** Unico endpoint del proyecto sin @PreAuthorize (SecurityConfig): Google redirige el
         * navegador aqui tras el consentimiento, sin ningun JWT (back/fase4-1-back.md). */
        public static final String CONEXION_CALLBACK_PATTERN = CONEXION_CALLBACK;
    }
}
