package com.andrewpina.servlet.admin;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/admin/usuario/detalle")
public class AdminUsuarioDetalleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = null;
        Connection conn = null;

        String idUsuarioStr = req.getParameter("id_usuario");
        if (idUsuarioStr == null || idUsuarioStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try (PrintWriter errOut = resp.getWriter()) {
                errOut.print(new JSONObject().put("error", "Falta el parámetro id_usuario.").toString());
            }
            return;
        }

        try {
            out = resp.getWriter();
            conn = DatabaseConnection.getConnection();
            int idUsuario = Integer.parseInt(idUsuarioStr);

            // Asumimos que la verificación de que el solicitante es admin ya se hizo
            // o se haría con un filtro o token.

            String sql = "SELECT id_usuario, nombre, email, telefono, id_rol, activo " +
                    "FROM usuario WHERE id_usuario = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, idUsuario);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    jsonResponse.put("id_usuario", rs.getInt("id_usuario"));
                    jsonResponse.put("nombre", rs.getString("nombre"));
                    jsonResponse.put("email", rs.getString("email"));
                    jsonResponse.put("telefono", rs.getString("telefono")); // Puede ser null
                    jsonResponse.put("id_rol", rs.getInt("id_rol"));
                    jsonResponse.put("activo", rs.getBoolean("activo"));
                    // NO devolver la contraseña
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("error", "Usuario no encontrado.");
                }
            }
            out.print(jsonResponse.toString());

        } catch (NumberFormatException e) {
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            if (out != null) out.print(new JSONObject().put("error", "ID de usuario inválido: " + idUsuarioStr).toString());
        } catch (SQLException e) {
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error de BD: " + e.getMessage()).toString());
        } catch (Exception e) {
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error inesperado: " + e.getMessage()).toString());
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (out != null) out.close();
        }
    }
}