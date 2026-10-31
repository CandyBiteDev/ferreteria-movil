package com.example.ferreteriamovil;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cliente HTTP de la API (Singleton). La app NUNCA se conecta a MySQL:
 * todo pasa por la API autenticada con token Bearer.
 */
public final class ApiClient {

    public interface Respuesta {
        void ok(String json);

        void error(String mensaje, int codigo);
    }

    private static ApiClient instancia;

    private final ExecutorService hilos = Executors.newFixedThreadPool(3);
    private final Handler principal = new Handler(Looper.getMainLooper());
    private String baseUrl = "";
    private String token;
    private JSONObject usuario;

    private ApiClient() {
    }

    public static synchronized ApiClient get() {
        if (instancia == null) instancia = new ApiClient();
        return instancia;
    }

    /** Acepta "192.168.1.10", "192.168.1.10:8080" o "http://192.168.1.10:8080/api". */
    public void setServidor(String servidor) {
        String s = servidor.trim();
        if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://" + s;
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (!s.substring(s.indexOf("//") + 2).contains(":")) s = s + ":8080";
        if (!s.endsWith("/api")) s = s + "/api";
        baseUrl = s;
    }

    public String getServidor() { return baseUrl; }
    public boolean haySesion() { return token != null; }
    public JSONObject getUsuario() { return usuario; }

    public void iniciarSesion(String token, JSONObject usuario) {
        this.token = token;
        this.usuario = usuario;
    }

    public void cerrarSesion() {
        if (token != null) post("/auth/logout", null, new Respuesta() {
            public void ok(String json) { }
            public void error(String mensaje, int codigo) { }
        });
        token = null;
        usuario = null;
    }

    public void get(String ruta, Respuesta r) {
        ejecutar("GET", ruta, null, r);
    }

    public void post(String ruta, JSONObject cuerpo, Respuesta r) {
        ejecutar("POST", ruta, cuerpo == null ? null : cuerpo.toString(), r);
    }

    private void ejecutar(String metodo, String ruta, String cuerpo, Respuesta r) {
        final String tokenActual = token;
        hilos.execute(() -> {
            try {
                String json = solicitud(metodo, ruta, cuerpo, tokenActual);
                principal.post(() -> r.ok(json));
            } catch (ErrorApi e) {
                if (e.codigo == 401 && !ruta.startsWith("/auth/login")) token = null;
                principal.post(() -> r.error(e.getMessage(), e.codigo));
            } catch (ConnectException | SocketTimeoutException | UnknownHostException e) {
                principal.post(() -> r.error("No se pudo conectar con " + baseUrl + ".\n\n"
                        + "Verifica que:\n"
                        + "• La aplicación de escritorio esté abierta.\n"
                        + "• El celular y la PC estén en la misma red Wi-Fi.\n"
                        + "• La IP sea la que muestra la ventana de login del escritorio\n"
                        + "  (cambia si la PC se conecta a otra red).\n"
                        + "• El Firewall de Windows permita Java en redes privadas.", -1));
            } catch (Exception e) {
                principal.post(() -> r.error("Error: " + e.getMessage(), -1));
            }
        });
    }

    private String solicitud(String metodo, String ruta, String cuerpo, String tokenActual) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(baseUrl + ruta).openConnection();
        try {
            c.setConnectTimeout(6000);
            c.setReadTimeout(12000);
            c.setRequestMethod(metodo);
            c.setRequestProperty("Accept", "application/json");
            if (tokenActual != null) c.setRequestProperty("Authorization", "Bearer " + tokenActual);
            if (cuerpo != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(cuerpo.getBytes(StandardCharsets.UTF_8));
                }
            }
            int codigo = c.getResponseCode();
            String texto = leer(codigo >= 400 ? c.getErrorStream() : c.getInputStream());
            if (codigo >= 400) {
                String msg = "Error " + codigo;
                try {
                    msg = new JSONObject(texto).optString("error", msg);
                } catch (Exception ignorada) {
                    // respuesta sin JSON
                }
                throw new ErrorApi(msg, codigo);
            }
            return texto;
        } finally {
            c.disconnect();
        }
    }

    private static String leer(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = br.readLine()) != null) sb.append(linea);
        }
        return sb.toString();
    }

    private static class ErrorApi extends Exception {
        final int codigo;

        ErrorApi(String mensaje, int codigo) {
            super(mensaje);
            this.codigo = codigo;
        }
    }
}
