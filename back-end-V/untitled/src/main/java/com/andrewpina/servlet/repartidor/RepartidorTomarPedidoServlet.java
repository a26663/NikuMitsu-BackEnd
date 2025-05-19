package com.andrewpina.servlet.repartidor;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/repartidor/pedido/tomar")
public class RepartidorTomarPedidoServlet extends HttpServlet {
    private static final int ID_ROL_REPARTIDOR = 3;

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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = resp.getWriter();
        Connection conn = null;

        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "Error al leer la solicitud.");
            out.print(jsonResponse.toString());
            out.close();
            return;
        }

        try {
            conn = DatabaseConnection.getConnection();
            JSONObject body = new JSONObject(sb.toString());

            int idRepartidor = body.getInt("id_repartidor");
            int idPedido = body.getInt("id_pedido");

            if (!esRepartidorValido(idRepartidor, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("error", "Acción no permitida. Usuario no es un repartidor válido o está inactivo.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            conn.setAutoCommit(false);

            // MODIFICACIÓN: Actualizar también 'repartidor_ha_tomado' a TRUE
            // y añadir 'repartidor_ha_tomado = FALSE' a la condición WHERE
            String sqlUpdate = "UPDATE pedido SET id_repartidor_asignado = ?, repartidor_ha_tomado = TRUE " +
                    "WHERE id_pedido = ? AND id_repartidor_asignado IS NULL AND repartidor_ha_tomado = FALSE " +
                    "AND id_estado IN (SELECT id_estado FROM estado_pedido WHERE nombre IN ('confirmado', 'en_preparacion', 'listo_para_entrega'))";
            int affectedRows;
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                stmt.setInt(1, idRepartidor);
                stmt.setInt(2, idPedido);
                affectedRows = stmt.executeUpdate();
            }

            if (affectedRows > 0) {
                conn.commit();
                jsonResponse.put("status", "success");
                jsonResponse.put("message", "Pedido #" + idPedido + " tomado exitosamente.");
            } else {
                conn.rollback();
                String errorMessage = "No se pudo tomar el pedido. Puede que ya esté asignado, su estado haya cambiado, o ya no esté disponible.";
                int statusCode = HttpServletResponse.SC_BAD_REQUEST;

                String checkSql = "SELECT id_repartidor_asignado, repartidor_ha_tomado, ep.nombre as estado_nombre " +
                        "FROM pedido pd JOIN estado_pedido ep ON pd.id_estado = ep.id_estado " +
                        "WHERE pd.id_pedido = ?";
                try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                    checkStmt.setInt(1, idPedido);
                    ResultSet rsCheck = checkStmt.executeQuery();
                    if (rsCheck.next()) {
                        if (rsCheck.getObject("id_repartidor_asignado") != null || rsCheck.getBoolean("repartidor_ha_tomado")) {
                            errorMessage = "El pedido ya ha sido tomado o asignado.";
                            statusCode = HttpServletResponse.SC_CONFLICT;
                        } else {
                            String estadoActual = rsCheck.getString("estado_nombre");
                            if (!("confirmado".equals(estadoActual) || "en_preparacion".equals(estadoActual) || "listo_para_entrega".equals(estadoActual))) {
                                errorMessage = "El pedido ya no está en un estado que permita ser tomado (estado actual: " + estadoActual + ").";
                            }
                        }
                    } else {
                        errorMessage = "Pedido no encontrado.";
                        statusCode = HttpServletResponse.SC_NOT_FOUND;
                    }
                }
                jsonResponse.put("error", errorMessage);
                resp.setStatus(statusCode);
            }
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "JSON inválido o campos faltantes: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error de base de datos: " + e.getMessage());
            out.print(jsonResponse.toString());
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            if (out != null) {
                out.close();
            }
        }
    }
}