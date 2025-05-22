package com.andrewpina.servlet.repartidor; // Asegúrate que el paquete sea correcto

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException; // Aunque no la usas explícitamente para lanzar, es bueno tenerla por si JSONObject la lanza

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

@WebServlet("/repartidor/pedidos")
public class RepartidorPedidosServlet extends HttpServlet {
    private static final int ID_ROL_REPARTIDOR = 3; // Asumiendo rol 3 para repartidor

    // Helper para verificar si el usuario es repartidor
    private boolean esRepartidorValido(int idUsuario, Connection conn) throws SQLException {
        if (idUsuario <= 0) return false;
        String sql = "SELECT id_rol FROM usuario WHERE id_usuario = ? AND activo = TRUE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_rol") == ID_ROL_REPARTIDOR;
            }
        }
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray pedidosArray = new JSONArray();
        PrintWriter out = null; // Declarar fuera para usar en el finally general
        Connection conn = null; // Declarar fuera para cerrar en el finally general

        try {
            out = resp.getWriter(); // Obtener writer una vez

            String idRepartidorStr = req.getParameter("id_repartidor");
            String esHistorial = req.getParameter("historial");

            if (idRepartidorStr == null || idRepartidorStr.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(new JSONObject().put("error", "Falta el parámetro id_repartidor.").toString());
                // out.close() se hará en el finally
                return;
            }

            int idRepartidor;
            try {
                idRepartidor = Integer.parseInt(idRepartidorStr);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print(new JSONObject().put("error", "ID de repartidor inválido: " + idRepartidorStr).toString());
                return;
            }

            conn = DatabaseConnection.getConnection();

            if (!esRepartidorValido(idRepartidor, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.print(new JSONObject().put("error", "Acceso denegado. Usuario no es un repartidor válido o está inactivo.").toString());
                return;
            }

            StringBuilder sqlBuilder = new StringBuilder(
                    "SELECT p.id_pedido, p.fecha_pedido, p.total, " +
                            "u_cliente.nombre AS nombre_cliente, u_cliente.telefono AS telefono_cliente, " +
                            "u_cliente.calle, u_cliente.numero, u_cliente.ciudad, u_cliente.codigo_postal, " +
                            "ep.nombre AS estado_pedido, ep.id_estado " +
                            "FROM pedido p " +
                            "JOIN usuario u_cliente ON p.id_usuario = u_cliente.id_usuario " +
                            "JOIN estado_pedido ep ON p.id_estado = ep.id_estado " +
                            "WHERE p.id_repartidor_asignado = ? "
            );

            if (esHistorial != null && "true".equalsIgnoreCase(esHistorial)) {
                sqlBuilder.append("AND ep.nombre IN ('entregado', 'cancelado') ");
                sqlBuilder.append("ORDER BY p.fecha_pedido DESC"); // Historial más reciente primero
            } else {
                sqlBuilder.append("AND ep.nombre NOT IN ('entregado', 'cancelado') ");
                sqlBuilder.append("ORDER BY p.fecha_pedido ASC"); // Activos más antiguos primero (o como prefieras)
            }

            System.out.println("INFO (RepartidorPedidosServlet): Ejecutando SQL: " + sqlBuilder.toString() + " para repartidor ID: " + idRepartidor);


            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                stmt.setInt(1, idRepartidor);
                ResultSet rs = stmt.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
                int contador = 0;
                while (rs.next()) {
                    contador++;
                    JSONObject pedidoJson = new JSONObject();
                    pedidoJson.put("id_pedido", rs.getInt("id_pedido"));
                    Timestamp fechaPedidoTs = rs.getTimestamp("fecha_pedido");
                    pedidoJson.put("fecha_pedido", fechaPedidoTs != null ? sdf.format(fechaPedidoTs) : null);
                    pedidoJson.put("total", rs.getDouble("total"));
                    pedidoJson.put("nombre_cliente", rs.getString("nombre_cliente"));
                    pedidoJson.put("telefono_cliente", rs.getString("telefono_cliente"));
                    pedidoJson.put("direccion_entrega", String.format("%s %s, %s, %s",
                            rs.getString("calle") != null ? rs.getString("calle") : "",
                            rs.getString("numero") != null ? rs.getString("numero") : "",
                            rs.getString("ciudad") != null ? rs.getString("ciudad") : "",
                            rs.getString("codigo_postal") != null ? rs.getString("codigo_postal") : ""
                    ).trim().replaceAll(", $", "").replaceAll(" ,", ",")); // Limpieza de formato
                    pedidoJson.put("estado_pedido", rs.getString("estado_pedido"));
                    pedidoJson.put("id_estado_actual", rs.getInt("id_estado"));
                    pedidosArray.put(pedidoJson);
                }
                System.out.println("INFO (RepartidorPedidosServlet): Pedidos encontrados: " + contador);
            }
            out.print(pedidosArray.toString()); // Esto enviará "[]" si no hay resultados, lo cual es correcto

        } catch (SQLException e) {
            e.printStackTrace();
            // Asegurar que el status y el content type se establezcan antes de escribir el error, si no se ha hecho.
            if (!resp.isCommitted()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            // Evitar NullPointerException si out es null porque getWriter() falló antes
            if (out != null) {
                out.print(new JSONObject().put("error", "Error de BD al obtener pedidos: " + e.getMessage()).toString());
            }
        } catch (Exception e) { // Captura más general para otros errores como JSONException o NumberFormatException no capturadas antes
            e.printStackTrace();
            if (!resp.isCommitted()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            if (out != null) {
                out.print(new JSONObject().put("error", "Error inesperado: " + e.getMessage()).toString());
            }
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace(); // Loguear error al cerrar conexión
                }
            }
            if (out != null) {
                out.close(); // Cerrar el PrintWriter
            }
        }
    }
}