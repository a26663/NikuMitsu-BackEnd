package com.andrewpina.servlet.admin;

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

@WebServlet("/admin/pedido/asignar-repartidor")
public class AdminAsignarRepartidorServlet extends HttpServlet {

    private static final int ID_ROL_ADMIN = 1;
    private static final int ID_ROL_REPARTIDOR = 3; // Asumiendo que 3 es repartidor

    private boolean esUsuarioConRol(int idUsuario, int idRolEsperado, Connection conn) throws SQLException {
        if (idUsuario <= 0) return false;
        String sql = "SELECT id_rol FROM usuario WHERE id_usuario = ? AND activo = TRUE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_rol") == idRolEsperado;
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

            int adminId = body.optInt("admin_id_usuario", -1);
            int idPedido = body.getInt("id_pedido");
            int idRepartidor = body.getInt("id_repartidor");

            if (!esUsuarioConRol(adminId, ID_ROL_ADMIN, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("error", "Acceso denegado. Se requiere ser administrador.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            if (!esUsuarioConRol(idRepartidor, ID_ROL_REPARTIDOR, conn)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("error", "El usuario seleccionado no es un repartidor válido o está inactivo.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            // MODIFICACIÓN: También actualizar repartidor_ha_tomado a TRUE
            String sqlUpdate = "UPDATE pedido SET id_repartidor_asignado = ?, repartidor_ha_tomado = TRUE WHERE id_pedido = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                stmt.setInt(1, idRepartidor);
                stmt.setInt(2, idPedido);
                int affectedRows = stmt.executeUpdate();
                if (affectedRows > 0) {
                    jsonResponse.put("status", "success");
                    jsonResponse.put("message", "Repartidor asignado al pedido #" + idPedido + " exitosamente.");
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("error", "Pedido no encontrado o no se pudo asignar el repartidor.");
                }
            }
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "JSON inválido o campos faltantes: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error de base de datos: " + e.getMessage());
            out.print(jsonResponse.toString());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            if (out != null) {
                out.close();
            }
        }
    }
}