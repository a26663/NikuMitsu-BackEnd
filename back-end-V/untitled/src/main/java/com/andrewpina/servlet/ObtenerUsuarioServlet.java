package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import org.json.JSONObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;
import java.io.*;
import java.sql.*;

@WebServlet("/usuario")
public class ObtenerUsuarioServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        JSONObject json = new JSONObject();

        try (PrintWriter out = resp.getWriter();
             Connection conn = DatabaseConnection.getConnection()) {

            String idParam = req.getParameter("id_usuario");

            if (idParam == null) {
                resp.setStatus(400);
                json.put("status", "error");
                json.put("message", "Parámetro 'id_usuario' requerido");
                out.print(json);
                return;
            }

            int idUsuario = Integer.parseInt(idParam);

            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id_usuario, nombre, email, telefono, calle, numero, ciudad, codigo_postal, id_rol FROM usuario WHERE id_usuario = ?");
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                json.put("id_usuario", rs.getInt("id_usuario"));
                json.put("nombre", rs.getString("nombre"));
                json.put("email", rs.getString("email"));
                json.put("telefono", rs.getString("telefono"));
                json.put("calle", rs.getString("calle"));
                json.put("numero", rs.getString("numero"));
                json.put("ciudad", rs.getString("ciudad"));
                json.put("codigo_postal", rs.getString("codigo_postal"));
                json.put("id_rol", rs.getInt("id_rol"));
            } else {
                json.put("status", "error");
                json.put("message", "Usuario no encontrado");
            }

            out.print(json);

        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
