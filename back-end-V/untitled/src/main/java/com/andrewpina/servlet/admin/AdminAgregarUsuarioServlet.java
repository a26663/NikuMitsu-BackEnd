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
import java.sql.SQLException;
import java.sql.Types;

@WebServlet("/admin/usuario/agregar")
public class AdminAgregarUsuarioServlet extends HttpServlet {
    private static final int ID_ROL_ADMIN = 1; // Deberían estar en config.js y ser accesibles aquí si fuera necesario

    // private boolean esSolicitanteAdmin(int adminIdSolicitante, Connection conn) throws SQLException { ... }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = resp.getWriter();
        Connection conn = null;

        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) sb.append(line);
        } catch (IOException e) { /* ... manejo error ... */ return; }

        try {
            conn = DatabaseConnection.getConnection();
            JSONObject body = new JSONObject(sb.toString());

            // int adminIdSolicitante = body.getInt("admin_id_solicitante");
            // if (!esSolicitanteAdmin(adminIdSolicitante, conn)) { ... error de acceso ... }

            String nombre = body.getString("nombre");
            String email = body.getString("email");
            String password = body.getString("password"); // ¡HASHEAR EN PRODUCCIÓN!
            String telefono = body.optString("telefono", null);
            int idRol = body.getInt("id_rol");
            boolean activo = body.optBoolean("activo", true);

            // Validar que el rol sea admin o repartidor
            if (idRol != ID_ROL_ADMIN && idRol != 3 /*ID_ROL_REPARTIDOR*/) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("error", "Solo se pueden crear usuarios Admin o Repartidor.");
                out.print(jsonResponse.toString());
                return;
            }

            // Dirección es requerida para todos los usuarios según tu schema
            // Si estos usuarios no necesitan dirección, debes hacer esas columnas NULLABLE en la BD
            // O proporcionar valores por defecto.
            String calle = body.optString("calle", "N/A"); // Asumiendo que el form de admin no los pide
            String numero = body.optString("numero", "N/A");
            String ciudad = body.optString("ciudad", "N/A");
            String codigoPostal = body.optString("codigo_postal", "N/A");


            String sql = "INSERT INTO usuario (nombre, email, password, telefono, calle, numero, ciudad, codigo_postal, id_rol, activo) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nombre);
                stmt.setString(2, email);
                stmt.setString(3, password); // ¡HASHEAR!
                if (telefono != null && !telefono.isEmpty()) stmt.setString(4, telefono); else stmt.setNull(4, Types.VARCHAR);
                stmt.setString(5, calle);
                stmt.setString(6, numero);
                stmt.setString(7, ciudad);
                stmt.setString(8, codigoPostal);
                stmt.setInt(9, idRol);
                stmt.setBoolean(10, activo);

                int affectedRows = stmt.executeUpdate();
                if (affectedRows > 0) {
                    jsonResponse.put("status", "success");
                    jsonResponse.put("message", "Usuario creado exitosamente.");
                } else {
                    throw new SQLException("No se pudo crear el usuario.");
                }
            }
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "JSON inválido o campos faltantes: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errMsg = "Error de base de datos: " + e.getMessage();
            if (e.getSQLState().equals("23505")) { // Unique constraint violation
                if (e.getMessage().contains("uq_usuario_email")) errMsg = "El email ya está registrado.";
                else if (e.getMessage().contains("uq_usuario_telefono")) errMsg = "El teléfono ya está registrado.";
                else errMsg = "Ya existe un registro con un valor único proporcionado.";
                resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409
            }
            jsonResponse.put("error", errMsg);
            out.print(jsonResponse.toString());
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (out != null) out.close();
        }
    }
}