# Ferretería Móvil — Proyecto 05, Programación II (UMG)

App Android (Java) para los **encargados de bodega** de la Ferretería e Inventario Multisucursal.
Es el componente móvil obligatorio del proyecto: consultar stock por bodega y confirmar la
recepción de transferencias.

La app **no se conecta a MySQL**: consume la API REST autenticada del sistema de escritorio
([ferreteria-escritorio](https://github.com/CandyBiteDev/ferreteria-escritorio)), que debe estar abierto.

## Funciones
- **Login** con usuario y contraseña del sistema; la API entrega un token (válido 8 horas).
- **Stock por bodega**: lista de productos con existencia y unidad, búsqueda por código,
  nombre o categoría, y alerta **BAJO MÍNIMO**.
- **Transferencias por recibir**: muestra las transferencias en tránsito hacia la bodega,
  con origen, fecha y productos. **Confirmar recepción** suma el stock al destino y el
  escritorio la ve como RECIBIDA.
- Solo el encargado de la bodega destino puede confirmar (si no, la API responde "Sin permiso").

## Cómo ejecutarla
1. Abrir y ejecutar la aplicación de escritorio en la PC (levanta la API en el puerto 8080).
2. Abrir este proyecto en Android Studio y ejecutarlo en el celular o en el emulador.
3. En el campo **Servidor** escribir la IP que muestra el login del escritorio, ej. `192.168.3.87:8080`.
   Sirve igual en el celular (misma red Wi-Fi que la PC) y en el emulador.
4. **Probar conexión** y luego ingresar, por ejemplo con `encargado.mixco`
   (las contraseñas de prueba están en el README del repositorio de escritorio).

## Estructura
| Clase | Función |
| --- | --- |
| `ApiClient` | Cliente HTTP (patrón Singleton): token Bearer, hilos en segundo plano, mensajes de error claros |
| `LoginActivity` | Configuración del servidor, prueba de conexión e inicio de sesión |
| `MainActivity` | Stock por bodega con búsqueda |
| `TransferenciasActivity` | Lista de transferencias en tránsito y confirmación de recepción |
| `Formato` | Formato de cantidades y fechas |

## Endpoints que usa
`GET /api/ping` · `POST /api/auth/login` · `POST /api/auth/logout` · `GET /api/bodegas` ·
`GET /api/existencias?bodega=&q=` · `GET /api/transferencias?destino=&estado=EN_TRANSITO` ·
`POST /api/transferencias/{id}/recibir`

Requisitos: Android 7.0 (API 24) o superior. Datos de demostración ficticios.
