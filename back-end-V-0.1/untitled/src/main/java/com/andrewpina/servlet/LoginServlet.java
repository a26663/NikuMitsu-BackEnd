package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import org.json.JSONObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.*;
import java.sql.*;
@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        resp.setContentType("application/json;charset=UTF-8");
        JSONObject json = new JSONObject();

        try (PrintWriter out = resp.getWriter();
             Connection conn = DatabaseConnection.getConnection()) {

            String email = req.getParameter("email");
            String password = req.getParameter("password");

            if (email == null || password == null) {
                resp.setStatus(400);
                json.put("status", "error");
                json.put("message", "Faltan parámetros email o password");
                out.print(json);
                return;
            }

            PreparedStatement stmt = conn.prepareStatement(
                "SELECT id_usuario, nombre, id_rol, recursosUsuario FROM usuario WHERE email = ? AND password = ? AND activo = TRUE");
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                json.put("status", "success");
                json.put("message", "Inicio de sesión exitoso");
                json.put("id_usuario", rs.getInt("id_usuario"));
                json.put("nombre", rs.getString("nombre"));
                json.put("id_rol", rs.getInt("id_rol"));
                json.put("recursosUsuario", rs.getString("recursosUsuario"));
            } else {
                json.put("status", "error");
                json.put("message", "Credenciales inválidas");
            }

            out.print(json);
        } catch (Exception e) {
            resp.setStatus(500);
            resp.getWriter().print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
