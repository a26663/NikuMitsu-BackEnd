package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import org.json.JSONObject;
import org.json.JSONException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.*;
import java.sql.*;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleLogin(req, resp);
    }

    // Opcional: Si quieres permitir GET para pruebas rápidas (NO RECOMENDADO PARA PRODUCCIÓN)
    // @Override
    // protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    //     // Advierte o simplemente llama a handleLogin si la lógica es la misma.
    //     // Pero idealmente el login siempre debería ser POST.
    //     // Por ahora, vamos a hacer que GET no haga nada o devuelva un error.
    //     resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    //     resp.setContentType("application/json;charset=UTF-8");
    //     try(PrintWriter out = resp.getWriter()){
    //         out.print(new JSONObject().put("error", "Método GET no permitido para login. Use POST.").toString());
    //     }
    // }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();

        String email = null;
        String password = null;

        // Leer del cuerpo de la petición (para POST)
        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            // Log error
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error al leer la solicitud.");
            try(PrintWriter out = resp.getWriter()) { out.print(jsonResponse.toString()); }
            return;
        }

        try {
            JSONObject requestBody = new JSONObject(sb.toString());
            email = requestBody.optString("email", null);
            password = requestBody.optString("password", null);
        } catch (JSONException e) {
            // Log error
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Formato JSON de solicitud inválido.");
            try(PrintWriter out = resp.getWriter()) { out.print(jsonResponse.toString()); }
            return;
        }


        try (Connection conn = DatabaseConnection.getConnection();
             PrintWriter out = resp.getWriter()) { // Obtener writer aquí para respuesta final

            if (email == null || email.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "Email y contraseña son requeridos.");
                out.print(jsonResponse.toString());
                return;
            }

            // Asumiendo que 'password' en la BD está en texto plano (¡MALA PRÁCTICA!)
            // En un sistema real, deberías hashear la contraseña aquí y compararla con el hash almacenado.
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT id_usuario, nombre, id_rol FROM usuario WHERE email = ? AND password = ? AND activo = TRUE");
            stmt.setString(1, email);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                jsonResponse.put("status", "success");
                jsonResponse.put("message", "Inicio de sesión exitoso");
                jsonResponse.put("id_usuario", rs.getInt("id_usuario"));
                jsonResponse.put("nombre", rs.getString("nombre"));
                jsonResponse.put("id_rol", rs.getInt("id_rol"));
                // jsonResponse.put("recursosUsuario", rs.getString("recursosUsuario")); // Si existiera
            } else {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "Credenciales inválidas o usuario inactivo.");
            }
            out.print(jsonResponse.toString());

        } catch (SQLException e) {
            e.printStackTrace(); // Loguea el error completo en el servidor
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            try (PrintWriter out = resp.getWriter()) {
                JSONObject errorJson = new JSONObject();
                errorJson.put("status", "error");
                errorJson.put("message", "Error de base de datos: " + e.getMessage());
                out.print(errorJson.toString());
            }
        } catch (Exception e) { // Captura para otros errores (como JSONException si no se obtuvo el writer antes)
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = resp.getWriter()) {
                JSONObject errorJson = new JSONObject();
                errorJson.put("status", "error");
                errorJson.put("message", "Error inesperado en el servidor: " + e.getMessage());
                out.print(errorJson.toString());
            }
        }
    }
}