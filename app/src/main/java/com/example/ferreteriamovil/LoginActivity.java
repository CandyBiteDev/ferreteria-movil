package com.example.ferreteriamovil;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private static final String PREFS = "config";
    private EditText txtServidor, txtUsuario, txtPassword;
    private Button btnIngresar;
    private ProgressBar progreso;
    private TextView lblEstado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        txtServidor = findViewById(R.id.txtServidor);
        txtUsuario = findViewById(R.id.txtUsuario);
        txtPassword = findViewById(R.id.txtPassword);
        btnIngresar = findViewById(R.id.btnIngresar);
        progreso = findViewById(R.id.progreso);
        lblEstado = findViewById(R.id.lblEstado);

        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        txtServidor.setText(p.getString("servidor", "192.168.3.87:8080"));
        txtUsuario.setText(p.getString("usuario", ""));

        btnIngresar.setOnClickListener(v -> ingresar());
        findViewById(R.id.btnProbar).setOnClickListener(v -> probarConexion());
    }

    private boolean prepararServidor() {
        String servidor = txtServidor.getText().toString().trim();
        if (servidor.isEmpty()) {
            txtServidor.setError("Escribe la IP de la PC");
            return false;
        }
        ApiClient.get().setServidor(servidor);
        return true;
    }

    private void cargando(boolean si) {
        progreso.setVisibility(si ? View.VISIBLE : View.GONE);
        btnIngresar.setEnabled(!si);
    }

    private void probarConexion() {
        if (!prepararServidor()) return;
        cargando(true);
        lblEstado.setText("Probando " + ApiClient.get().getServidor() + " ...");
        ApiClient.get().get("/ping", new ApiClient.Respuesta() {
            public void ok(String json) {
                cargando(false);
                lblEstado.setText("✔ Conectado a " + ApiClient.get().getServidor());
            }

            public void error(String mensaje, int codigo) {
                cargando(false);
                lblEstado.setText("✖ Sin conexión");
                mostrarError(mensaje);
            }
        });
    }

    private void ingresar() {
        String usuario = txtUsuario.getText().toString().trim();
        String password = txtPassword.getText().toString();
        if (usuario.isEmpty()) { txtUsuario.setError("Requerido"); return; }
        if (password.isEmpty()) { txtPassword.setError("Requerido"); return; }
        if (!prepararServidor()) return;

        JSONObject body = new JSONObject();
        try {
            body.put("username", usuario);
            body.put("password", password);
        } catch (Exception ignorada) {
            return;
        }
        cargando(true);
        lblEstado.setText("Ingresando...");
        ApiClient.get().post("/auth/login", body, new ApiClient.Respuesta() {
            public void ok(String json) {
                cargando(false);
                try {
                    JSONObject r = new JSONObject(json);
                    ApiClient.get().iniciarSesion(r.getString("token"), r.getJSONObject("usuario"));
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString("servidor", txtServidor.getText().toString().trim())
                            .putString("usuario", usuario).apply();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                } catch (Exception e) {
                    mostrarError("Respuesta inesperada del servidor.");
                }
            }

            public void error(String mensaje, int codigo) {
                cargando(false);
                lblEstado.setText("");
                mostrarError(mensaje);
            }
        });
    }

    private void mostrarError(String mensaje) {
        new AlertDialog.Builder(this).setTitle("No se pudo continuar").setMessage(mensaje).setPositiveButton("Entendido", null).show();
    }
}
