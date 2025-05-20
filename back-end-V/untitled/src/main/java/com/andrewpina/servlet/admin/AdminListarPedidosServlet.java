package com.andrewpina.servlet.admin; // Asegúrate que el paquete sea correcto

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
import java.util.ArrayList;
import java.util.List;

@WebServlet("/admin/pedidos/listar")
public class AdminListarPedidosServlet extends HttpServlet {

    // No necesitamos verificación de admin aquí si el acceso al endpoint ya está protegido
    // por un filtro general de la intranet o si asumimos que solo admins conocen/usan este panel.
    // Sin embargo, para mayor seguridad, se podría añadir una verificación del rol del solicitante
    // si se pasara un 'admin_id_usuario' o se usara sesión.
    // Por ahora, lo dejamos abierto para que el admin acceda.

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray pedidosArray = new JSONArray();
        PrintWriter out = null;
        Connection conn = null;

        // Filtro opcional por estado
        String idEstadoFilterStr = req.getParameter("id_estado");
        Integer idEstadoFilter = null;
        if (idEstadoFilterStr != null && !idEstadoFilterStr.trim().isEmpty()) {
            try {
                idEstadoFilter = Integer.parseInt(idEstadoFilterStr);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try(PrintWriter errOut = resp.getWriter()) { // Usar try-with-resources para el writer de error
                    errOut.print(new JSONObject().put("error", "ID de estado para filtro inválido: " + idEstadoFilterStr).toString());
                }
                return;
            }
        }

        try {
            out = resp.getWriter();
            conn = DatabaseConnection.getConnection();

            System.out.println("INFO (AdminListarPedidosServlet): Buscando pedidos. Filtro de estado ID: " + (idEstadoFilter != null ? idEstadoFilter : "NINGUNO"));


            // Consulta para obtener TODOS los pedidos, con información del cliente, estado y repartidor (si está asignado)
            StringBuilder sqlBuilder = new StringBuilder(
                    "SELECT p.id_pedido, p.fecha_pedido, p.total AS total_pedido, p.id_repartidor_asignado, p.repartidor_ha_tomado, " +
                            "u_cliente.id_usuario AS id_cliente, u_cliente.nombre AS nombre_cliente, " +
                            "u_repartidor.nombre AS nombre_repartidor, " + // Nombre del repartidor
                            "ep.id_estado, ep.nombre AS estado_nombre " +
                            "FROM pedido p " +
                            "JOIN usuario u_cliente ON p.id_usuario = u_cliente.id_usuario " +
                            "JOIN estado_pedido ep ON p.id_estado = ep.id_estado " +
                            "LEFT JOIN usuario u_repartidor ON p.id_repartidor_asignado = u_repartidor.id_usuario " // LEFT JOIN para repartidor, puede ser NULL
            );

            List<Object> params = new ArrayList<>();
            boolean whereClauseAdded = false;

            if (idEstadoFilter != null) {
                sqlBuilder.append(" WHERE ep.id_estado = ?");
                params.add(idEstadoFilter);
                whereClauseAdded = true;
            }

            // Aquí podrías añadir más filtros si los necesitas en el futuro
            // Ejemplo: filtrar por id_repartidor_asignado
            // String idRepartidorFilterStr = req.getParameter("id_repartidor");
            // if (idRepartidorFilterStr != null && !idRepartidorFilterStr.isEmpty()){
            //     if(!whereClauseAdded) sqlBuilder.append(" WHERE "); else sqlBuilder.append(" AND ");
            //     sqlBuilder.append("p.id_repartidor_asignado = ?");
            //     params.add(Integer.parseInt(idRepartidorFilterStr));
            // }


            sqlBuilder.append(" ORDER BY p.fecha_pedido DESC"); // Mostrar los más recientes primero

            System.out.println("INFO (AdminListarPedidosServlet): Ejecutando SQL: " + sqlBuilder.toString());

            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }

                ResultSet rs = stmt.executeQuery();
                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                int contador = 0;
                while (rs.next()) {
                    contador++;
                    JSONObject pedidoJson = new JSONObject();
                    pedidoJson.put("id_pedido", rs.getInt("id_pedido"));
                    Timestamp fechaPedidoTs = rs.getTimestamp("fecha_pedido");
                    pedidoJson.put("fecha_pedido", fechaPedidoTs != null ? sdf.format(fechaPedidoTs) : "N/A");
                    pedidoJson.put("total_pedido", rs.getDouble("total_pedido"));
                    pedidoJson.put("id_cliente", rs.getInt("id_cliente"));
                    pedidoJson.put("nombre_cliente", rs.getString("nombre_cliente"));
                    pedidoJson.put("id_estado", rs.getInt("id_estado"));
                    pedidoJson.put("estado_nombre", rs.getString("estado_nombre"));
                    pedidoJson.put("id_repartidor_asignado", rs.getObject("id_repartidor_asignado") != null ? rs.getInt("id_repartidor_asignado") : JSONObject.NULL);
                    pedidoJson.put("nombre_repartidor", rs.getString("nombre_repartidor")); // Será null si no hay repartidor asignado
                    pedidoJson.put("repartidor_ha_tomado", rs.getBoolean("repartidor_ha_tomado"));

                    pedidosArray.put(pedidoJson);
                }
                System.out.println("INFO (AdminListarPedidosServlet): Total de pedidos devueltos: " + contador);
            }
            out.print(pedidosArray.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error de BD al listar pedidos (admin): " + e.getMessage()).toString());
        } catch (Exception e) { // Para JSONException u otros
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error inesperado al listar pedidos (admin): " + e.getMessage()).toString());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
            if (out != null) {
                out.close();
            }
        }
    }
}