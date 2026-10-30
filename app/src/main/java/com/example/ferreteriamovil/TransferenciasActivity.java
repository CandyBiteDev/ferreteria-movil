package com.example.ferreteriamovil;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Confirmar recepción de transferencias (componente móvil obligatorio, parte 2). */
public class TransferenciasActivity extends AppCompatActivity {

    private final List<JSONObject> transferencias = new ArrayList<>();
    private int idBodega;
    private ProgressBar progreso;
    private TransferenciasAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ApiClient.get().haySesion()) {
            finish();
            return;
        }
        setContentView(R.layout.activity_transferencias);
        idBodega = getIntent().getIntExtra("idBodega", ApiClient.get().getUsuario().optInt("idBodega", 0));

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setSubtitle("Destino: " + getIntent().getStringExtra("bodega"));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_transferencias);
        toolbar.setOnMenuItemClickListener(item -> {
            cargar();
            return true;
        });

        progreso = findViewById(R.id.progreso);
        ListView lista = findViewById(R.id.listaTransferencias);
        adapter = new TransferenciasAdapter();
        lista.setAdapter(adapter);
        lista.setEmptyView(findViewById(R.id.lblVacio));
        cargar();
    }

    private void cargar() {
        progreso.setVisibility(View.VISIBLE);
        ApiClient.get().get("/transferencias?destino=" + idBodega + "&estado=EN_TRANSITO", new ApiClient.Respuesta() {
            public void ok(String json) {
                progreso.setVisibility(View.GONE);
                try {
                    JSONArray arr = new JSONArray(json);
                    transferencias.clear();
                    for (int i = 0; i < arr.length(); i++) transferencias.add(arr.getJSONObject(i));
                    adapter.notifyDataSetChanged();
                } catch (Exception e) {
                    mostrar("Aviso", "No se pudieron leer las transferencias.");
                }
            }

            public void error(String mensaje, int codigo) {
                progreso.setVisibility(View.GONE);
                if (codigo == 401) {
                    startActivity(new Intent(TransferenciasActivity.this, LoginActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                    finish();
                    return;
                }
                mostrar("Aviso", mensaje);
            }
        });
    }

    private void confirmar(JSONObject t) {
        EditText obs = new EditText(this);
        obs.setHint("Observación (opcional): ej. llegó completo");
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        obs.setPadding(pad, pad / 2, pad, pad / 2);
        new AlertDialog.Builder(this)
                .setTitle("Confirmar recepción #" + t.optInt("id"))
                .setMessage("¿Verificaste físicamente los productos? Al confirmar, el stock ingresa a " + t.optString("bodegaDestino") + ".")
                .setView(obs)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Confirmar", (d, w) -> enviarConfirmacion(t.optInt("id"), obs.getText().toString().trim()))
                .show();
    }

    private void enviarConfirmacion(int id, String observacion) {
        JSONObject body = new JSONObject();
        try {
            body.put("observacion", observacion);
        } catch (Exception ignorada) {
            // observación vacía
        }
        progreso.setVisibility(View.VISIBLE);
        ApiClient.get().post("/transferencias/" + id + "/recibir", body, new ApiClient.Respuesta() {
            public void ok(String json) {
                progreso.setVisibility(View.GONE);
                Toast.makeText(TransferenciasActivity.this, "Transferencia #" + id + " recibida. Stock actualizado.", Toast.LENGTH_LONG).show();
                cargar();
            }

            public void error(String mensaje, int codigo) {
                progreso.setVisibility(View.GONE);
                mostrar(codigo == 403 ? "Sin permiso" : "No se pudo confirmar", mensaje);
                cargar();
            }
        });
    }

    private void mostrar(String titulo, String mensaje) {
        new AlertDialog.Builder(this).setTitle(titulo).setMessage(mensaje).setPositiveButton("OK", null).show();
    }

    private class TransferenciasAdapter extends BaseAdapter {
        public int getCount() { return transferencias.size(); }
        public Object getItem(int i) { return transferencias.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transferencia, parent, false);
            JSONObject t = transferencias.get(i);
            ((TextView) v.findViewById(R.id.lblTitulo)).setText("Transferencia #" + t.optInt("id"));
            ((TextView) v.findViewById(R.id.lblRuta)).setText(t.optString("bodegaOrigen") + "  →  " + t.optString("bodegaDestino"));
            String obs = t.optString("observacion", "");
            ((TextView) v.findViewById(R.id.lblInfo)).setText("Enviada " + Formato.fecha(t.optString("fecha"))
                    + " por " + t.optString("usuario") + (obs.isEmpty() || obs.equals("null") ? "" : "\n" + obs));
            StringBuilder sb = new StringBuilder();
            JSONArray det = t.optJSONArray("detalles");
            if (det != null) {
                for (int k = 0; k < det.length(); k++) {
                    JSONObject d = det.optJSONObject(k);
                    sb.append("• ").append(Formato.cantidad(d.optDouble("cantidad"))).append(" ").append(d.optString("unidad"))
                            .append("  ").append(d.optString("producto")).append(k < det.length() - 1 ? "\n" : "");
                }
            }
            ((TextView) v.findViewById(R.id.lblProductos)).setText(sb.toString());
            Button btn = v.findViewById(R.id.btnConfirmar);
            btn.setOnClickListener(x -> confirmar(t));
            return v;
        }
    }
}
