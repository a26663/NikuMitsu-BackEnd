package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import org.json.JSONObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
@WebServlet("/registro")
public class RegistroUsuarioServlet extends HttpServlet {
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject json = new JSONObject();

        try (BufferedReader reader = req.getReader();
             PrintWriter out = resp.getWriter();
             Connection conn = DatabaseConnection.getConnection()) {

            conn.setAutoCommit(true); // Aseguramos que los cambios se apliquen

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject body = new JSONObject(sb.toString());

            System.out.println("JSON recibido: " + body); // Log de depuración

            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO usuario (nombre, email, password, telefono, calle, numero, ciudad, codigo_postal, id_rol) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 2)");

            stmt.setString(1, body.getString("nombre"));
            stmt.setString(2, body.getString("email"));
            stmt.setString(3, body.getString("password"));
            stmt.setString(4, body.getString("telefono"));
            stmt.setString(5, body.getString("calle"));
            stmt.setString(6, body.getString("numero"));
            stmt.setString(7, body.getString("ciudad"));
            stmt.setString(8, body.getString("codigo_postal"));

            int filas = stmt.executeUpdate();

            if (filas > 0) {
                json.put("status", "success");
                json.put("message", "Usuario registrado correctamente");
            } else {
                json.put("status", "error");
                json.put("message", "No se insertó ningún usuario");
            }

            out.print(json);

        } catch (SQLException e) {
            e.printStackTrace(); // Más útil que solo e.getMessage()
            resp.setStatus(400);
            resp.getWriter().print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
