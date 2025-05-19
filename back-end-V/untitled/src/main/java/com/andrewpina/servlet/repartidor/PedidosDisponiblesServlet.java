package com.andrewpina.servlet.repartidor;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

@WebServlet("/repartidor/pedidos-disponibles")
public class PedidosDisponiblesServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray pedidosArray = new JSONArray();
        PrintWriter out = null;

        try {
            out = resp.getWriter();
            Connection conn = DatabaseConnection.getConnection();

            // MODIFICACIÓN: Añadir 'p.repartidor_ha_tomado = FALSE' al WHERE
            String sql = "SELECT p.id_pedido, p.fecha_pedido, p.total, " +
                    "u_cliente.nombre AS nombre_cliente, " +
                    "u_cliente.calle, u_cliente.numero, u_cliente.ciudad, u_cliente.codigo_postal, " +
                    "ep.nombre AS estado_pedido " +
                    "FROM pedido p " +
                    "JOIN usuario u_cliente ON p.id_usuario = u_cliente.id_usuario " +
                    "JOIN estado_pedido ep ON p.id_estado = ep.id_estado " +
                    "WHERE p.id_repartidor_asignado IS NULL AND p.repartidor_ha_tomado = FALSE " + // <-- MODIFICADO
                    "AND ep.nombre IN ('confirmado', 'en_preparacion', 'listo_para_entrega') " +
                    "ORDER BY p.fecha_pedido ASC";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");

                while (rs.next()) {
                    JSONObject pedidoJson = new JSONObject();
                    pedidoJson.put("id_pedido", rs.getInt("id_pedido"));
                    Timestamp fechaPedidoTs = rs.getTimestamp("fecha_pedido");
                    pedidoJson.put("fecha_pedido", sdf.format(fechaPedidoTs));
                    pedidoJson.put("total", rs.getDouble("total"));
                    pedidoJson.put("nombre_cliente", rs.getString("nombre_cliente"));
                    pedidoJson.put("direccion_entrega", String.format("%s %s, %s, %s",
                            rs.getString("calle"), rs.getString("numero"),
                            rs.getString("ciudad"), rs.getString("codigo_postal")));
                    pedidoJson.put("estado_pedido", rs.getString("estado_pedido"));
                    pedidosArray.put(pedidoJson);
                }
            } finally {
                if (conn != null) { try { conn.close(); } catch (SQLException e) { e.printStackTrace(); } }
            }
            out.print(pedidosArray.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorJson = new JSONObject().put("error", "Error de BD al listar pedidos disponibles: " + e.getMessage());
            if (out == null && !resp.isCommitted()) { try { out = resp.getWriter(); } catch (IOException ignored) {} }
            if (out != null && !resp.isCommitted()) { out.print(errorJson.toString()); }
        } catch (JSONException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorJson = new JSONObject().put("error", "Error al construir JSON para pedidos disponibles: " + e.getMessage());
            if (out == null && !resp.isCommitted()) { try { out = resp.getWriter(); } catch (IOException ignored) {} }
            if (out != null && !resp.isCommitted()) { out.print(errorJson.toString()); }
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}