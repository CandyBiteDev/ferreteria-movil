package com.example.ferreteriamovil;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Consulta de stock por bodega (componente móvil obligatorio, parte 1). */
public class MainActivity extends AppCompatActivity {

    private final List<JSONObject> bodegas = new ArrayList<>();
    private final List<JSONObject> existencias = new ArrayList<>();
    private Spinner spinBodega;
    private EditText txtBuscar;
    private TextView lblResumen;
    private ProgressBar progreso;
    private Button btnTransferencias;
    private ExistenciasAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ApiClient.get().haySesion()) {
            irALogin();
            return;
        }
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        JSONObject u = ApiClient.get().getUsuario();
        toolbar.setSubtitle(u.optString("nombre") + " · " + u.optString("rol"));
        toolbar.inflateMenu(R.menu.menu_principal);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.accion_salir) {
                ApiClient.get().cerrarSesion();
                irALogin();
            } else if (item.getItemId() == R.id.accion_actualizar) {
                cargarExistencias();
            }
            return true;
        });

        spinBodega = findViewById(R.id.spinBodega);
        txtBuscar = findViewById(R.id.txtBuscar);
        lblResumen = findViewById(R.id.lblResumen);
        progreso = findViewById(R.id.progreso);
        btnTransferencias = findViewById(R.id.btnTransferencias);
        ListView lista = findViewById(R.id.listaExistencias);
        adapter = new ExistenciasAdapter();
        lista.setAdapter(adapter);
        lista.setEmptyView(findViewById(R.id.lblVacio));

        spinBodega.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { cargarExistencias(); }
            public void onNothingSelected(AdapterView<?> p) { }
        });
        txtBuscar.setOnEditorActionListener((v, a, e) -> { cargarExistencias(); return true; });
        txtBuscar.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { if (s.length() == 0 || s.length() >= 3) cargarExistencias(); }
        });
        btnTransferencias.setOnClickListener(v -> {
            Intent i = new Intent(this, TransferenciasActivity.class);
            JSONObject b = bodegaSeleccionada();
            if (b != null) {
                i.putExtra("idBodega", b.optInt("id"));
                i.putExtra("bodega", b.optString("nombre"));
            }
            startActivity(i);
        });

        cargarBodegas();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ApiClient.get().haySesion()) {
            irALogin();
            return;
        }
        if (adapter != null && !bodegas.isEmpty()) {
            cargarExistencias();
        }
    }

    private void irALogin() {
        startActivity(new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private JSONObject bodegaSeleccionada() {
        int pos = spinBodega.getSelectedItemPosition();
        return pos >= 0 && pos < bodegas.size() ? bodegas.get(pos) : null;
    }

    private void cargarBodegas() {
        progreso.setVisibility(View.VISIBLE);
        ApiClient.get().get("/bodegas", new ApiClient.Respuesta() {
            public void ok(String json) {
                try {
                    JSONArray arr = new JSONArray(json);
                    List<String> nombres = new ArrayList<>();
                    int miBodega = ApiClient.get().getUsuario().optInt("idBodega", -1);
                    int seleccion = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject b = arr.getJSONObject(i);
                        bodegas.add(b);
                        nombres.add(b.optString("nombre") + " — " + b.optString("sucursal"));
                        if (b.optInt("id") == miBodega) seleccion = i;
                    }
                    ArrayAdapter<String> a = new ArrayAdapter<>(MainActivity.this, android.R.layout.simple_spinner_item, nombres);
                    a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    spinBodega.setAdapter(a);
                    spinBodega.setSelection(seleccion);
                } catch (Exception e) {
                    mostrarError("No se pudieron leer las bodegas.");
                }
            }

            public void error(String mensaje, int codigo) {
                progreso.setVisibility(View.GONE);
                manejarError(mensaje, codigo);
            }
        });
    }

    private void cargarExistencias() {
        JSONObject b = bodegaSeleccionada();
        if (b == null) return;
        progreso.setVisibility(View.VISIBLE);
        String q = txtBuscar.getText().toString().trim();
        String ruta = "/existencias?bodega=" + b.optInt("id") + (q.isEmpty() ? "" : "&q=" + android.net.Uri.encode(q));
        ApiClient.get().get(ruta, new ApiClient.Respuesta() {
            public void ok(String json) {
                progreso.setVisibility(View.GONE);
                try {
                    JSONArray arr = new JSONArray(json);
                    existencias.clear();
                    int bajos = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        existencias.add(e);
                        if (e.optBoolean("bajoMinimo")) bajos++;
                    }
                    adapter.notifyDataSetChanged();
                    lblResumen.setText(existencias.size() + " productos · " + bajos + " bajo el mínimo");
                } catch (Exception e) {
                    mostrarError("No se pudo leer el inventario.");
                }
            }

            public void error(String mensaje, int codigo) {
                progreso.setVisibility(View.GONE);
                manejarError(mensaje, codigo);
            }
        });
        contarTransferencias(b.optInt("id"));
    }

    private void contarTransferencias(int idBodega) {
        ApiClient.get().get("/transferencias?destino=" + idBodega + "&estado=EN_TRANSITO", new ApiClient.Respuesta() {
            public void ok(String json) {
                try {
                    int n = new JSONArray(json).length();
                    btnTransferencias.setText(n == 0 ? "Transferencias por recibir" : "Transferencias por recibir (" + n + ")");
                } catch (Exception ignorada) {
                    // se deja el texto anterior
                }
            }

            public void error(String mensaje, int codigo) { }
        });
    }

    private void manejarError(String mensaje, int codigo) {
        if (codigo == 401) {
            irALogin();
            return;
        }
        mostrarError(mensaje);
    }

    private void mostrarError(String mensaje) {
        new AlertDialog.Builder(this).setTitle("Aviso").setMessage(mensaje).setPositiveButton("OK", null).show();
    }

    private class ExistenciasAdapter extends BaseAdapter {
        public int getCount() { return existencias.size(); }
        public Object getItem(int i) { return existencias.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup parent) {
            if (v == null) v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_existencia, parent, false);
            JSONObject e = existencias.get(i);
            boolean bajo = e.optBoolean("bajoMinimo");
            ((TextView) v.findViewById(R.id.lblProducto)).setText(e.optString("producto"));
            ((TextView) v.findViewById(R.id.lblDetalle)).setText(e.optString("codigo") + " · " + e.optString("categoria")
                    + " · mín. " + e.optInt("stockMinimo"));
            TextView cant = v.findViewById(R.id.lblCantidad);
            cant.setText(Formato.cantidad(e.optDouble("cantidad")));
            cant.setTextColor(bajo ? Color.parseColor("#C0392B") : Color.parseColor("#1E8449"));
            ((TextView) v.findViewById(R.id.lblUnidad)).setText(e.optString("unidad"));
            v.findViewById(R.id.lblAlerta).setVisibility(bajo ? View.VISIBLE : View.GONE);
            return v;
        }
    }
}
