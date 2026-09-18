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
    }

    public static final class Vacaciones {
        private Vacaciones() {
        }

        public static final String BASE = API + "/vacaciones";
        public static final String RESUMEN = BASE + "/resumen";
        public static final String CALENDARIO = BASE + "/calendario";
        public static final String SOLICITUDES = BASE + "/solicitudes";
        public static final String SOLICITUD_BY_ID = SOLICITUDES + "/{id}";
    }
}
