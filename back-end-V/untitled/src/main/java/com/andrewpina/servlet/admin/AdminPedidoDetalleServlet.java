package com.andrewpina.servlet.admin; // Asegúrate que el paquete sea correcto

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
// JSONException no se lanza explícitamente pero JSONObject puede lanzarla
// import org.json.JSONException;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

@WebServlet("/admin/pedido/detalle")
public class AdminPedidoDetalleServlet extends HttpServlet {

    // private static final int ID_ROL_ADMIN = 1; // No se usa en esta versión simplificada del helper esAdmin

    // Helper simplificado, en un sistema real esto usaría sesión o tokens.
    private boolean esAdmin(HttpServletRequest req, Connection conn) throws SQLException {
        // Por ahora, si se llama a este endpoint, asumimos que la protección de acceso
        // general a los endpoints /admin/* ya se ha realizado (ej. por un filtro).
        // Si necesitas verificar el rol del usuario que hace la petición aquí,
        // necesitarías pasar su ID o usar la sesión.
        return true;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject(); // Respuesta final
        PrintWriter out = null;
        Connection conn = null;

        String idPedidoStr = req.getParameter("id_pedido");

        if (idPedidoStr == null || idPedidoStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter errOut = resp.getWriter()){ // Usar try-with-resources para el writer de error
                errOut.print(new JSONObject().put("error", "Falta el parámetro id_pedido.").toString());
            } // El errOut se cierra automáticamente aquí
            return;
        }

        try {
            out = resp.getWriter(); // Obtener writer principal
            conn = DatabaseConnection.getConnection();

            // Opcional: Verificar si el solicitante es admin (requeriría pasar admin_id o usar sesión)
            // if (!esAdmin(req, conn)) {
            //     resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
            //     jsonResponse.put("error", "Acceso denegado.");
            //     out.print(jsonResponse.toString());
            //     return;
            // }

            int idPedido = Integer.parseInt(idPedidoStr);
            System.out.println("INFO (AdminPedidoDetalleServlet): Buscando detalles para pedido ID: " + idPedido);

            JSONObject pedidoData = new JSONObject();
            JSONObject clienteData = new JSONObject();
            JSONArray detallesArray = new JSONArray();

            // 1. Obtener datos del pedido y del cliente
            // Eliminamos p.metodo_pago_seleccionado de la consulta SQL
            String sqlPedidoCliente =
                    "SELECT p.id_pedido, p.fecha_pedido, p.total, p.id_estado, p.id_repartidor_asignado, p.repartidor_ha_tomado, " +
                            "p.calle_envio, p.numero_envio, p.ciudad_envio, p.codigo_postal_envio, p.notas_direccion_envio, " +
                            // "p.metodo_pago_seleccionado, " + // <-- LÍNEA ELIMINADA
                            "u.id_usuario AS id_cliente, u.nombre AS nombre_cliente, u.email AS email_cliente, u.telefono AS telefono_cliente, " +
                            // La dirección del perfil del usuario se puede omitir si siempre usamos la dirección de envío del pedido.
                            // u.calle AS calle_perfil, u.numero AS numero_perfil, u.ciudad AS ciudad_perfil, u.codigo_postal AS codigo_postal_perfil,
                            "ep.nombre AS estado_nombre " +
                            "FROM pedido p " +
                            "JOIN usuario u ON p.id_usuario = u.id_usuario " +
                            "JOIN estado_pedido ep ON p.id_estado = ep.id_estado " +
                            "WHERE p.id_pedido = ?";

            try (PreparedStatement stmtPedido = conn.prepareStatement(sqlPedidoCliente)) {
                stmtPedido.setInt(1, idPedido);
                ResultSet rs = stmtPedido.executeQuery();
                if (rs.next()) {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                    Timestamp fechaTs = rs.getTimestamp("fecha_pedido");

                    pedidoData.put("id_pedido", rs.getInt("id_pedido"));
                    pedidoData.put("fecha_pedido", fechaTs != null ? sdf.format(fechaTs) : JSONObject.NULL);
                    pedidoData.put("total_pedido", rs.getDouble("total")); // JS espera total_pedido
                    pedidoData.put("id_estado", rs.getInt("id_estado"));
                    pedidoData.put("estado_nombre", rs.getString("estado_nombre"));
                    pedidoData.put("id_repartidor_asignado", rs.getObject("id_repartidor_asignado") != null ? rs.getInt("id_repartidor_asignado") : JSONObject.NULL);
                    pedidoData.put("repartidor_ha_tomado", rs.getBoolean("repartidor_ha_tomado"));

                    pedidoData.put("calle_envio", rs.getString("calle_envio"));
                    pedidoData.put("numero_envio", rs.getString("numero_envio"));
                    pedidoData.put("ciudad_envio", rs.getString("ciudad_envio"));
                    pedidoData.put("codigo_postal_envio", rs.getString("codigo_postal_envio"));
                    pedidoData.put("notas_direccion_envio", rs.getString("notas_direccion_envio")); // Puede ser null

                    // ELIMINAR la línea que lee metodo_pago_seleccionado
                    // pedidoData.put("metodo_pago", rs.getString("metodo_pago_seleccionado")); // <-- LÍNEA ELIMINADA
                    // Si el JS espera un campo "metodo_pago" en el objeto pedido, podemos ponerlo como un valor por defecto:
                    pedidoData.put("metodo_pago", "No Registrado"); // O JSONObject.NULL si el JS lo maneja

                    clienteData.put("id_cliente", rs.getInt("id_cliente"));
                    clienteData.put("nombre", rs.getString("nombre_cliente"));
                    clienteData.put("email", rs.getString("email_cliente"));
                    clienteData.put("telefono", rs.getString("telefono_cliente"));
                    // Para el modal, el JS esperaba la dirección en el objeto cliente.
                    // Usaremos la dirección de envío del pedido.
                    clienteData.put("calle", rs.getString("calle_envio"));
                    clienteData.put("numero", rs.getString("numero_envio"));
                    clienteData.put("ciudad", rs.getString("ciudad_envio"));
                    clienteData.put("codigo_postal", rs.getString("codigo_postal_envio"));

                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("error", "Pedido no encontrado.");
                    out.print(jsonResponse.toString());
                    // out.close() se hará en el finally
                    return; // Salir temprano
                }
            }

            // 2. Obtener detalles del pedido (items)
            String sqlDetalles = "SELECT dp.cantidad, dp.precio_unitario, pr.nombre AS nombre_producto " +
                    "FROM detalle_pedido dp " +
                    "JOIN producto pr ON dp.id_producto = pr.id_producto " +
                    "WHERE dp.id_pedido = ?";
            try (PreparedStatement stmtDetalles = conn.prepareStatement(sqlDetalles)) {
                stmtDetalles.setInt(1, idPedido);
                ResultSet rsDetalles = stmtDetalles.executeQuery();
                while (rsDetalles.next()) {
                    JSONObject detalleJson = new JSONObject();
                    detalleJson.put("nombre_producto", rsDetalles.getString("nombre_producto"));
                    detalleJson.put("cantidad", rsDetalles.getInt("cantidad"));
                    detalleJson.put("precio_unitario", rsDetalles.getDouble("precio_unitario"));
                    detallesArray.put(detalleJson);
                }
            }

            // Construir la respuesta JSON final
            jsonResponse.put("pedido", pedidoData);
            jsonResponse.put("cliente", clienteData);
            jsonResponse.put("detalles", detallesArray);
            out.print(jsonResponse.toString());

        } catch (NumberFormatException e) {
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            if (out != null) out.print(new JSONObject().put("error", "ID de pedido inválido: " + idPedidoStr).toString());

        } catch (SQLException e) {
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error de BD al obtener detalles del pedido: " + e.getMessage()).toString());
        } catch (Exception e) { // Para JSONException y otros inesperados
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error inesperado al obtener detalles del pedido: " + e.getMessage()).toString());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
            if (out != null) {
                out.close(); // Cerrar el PrintWriter principal
            }
        }
    }
}