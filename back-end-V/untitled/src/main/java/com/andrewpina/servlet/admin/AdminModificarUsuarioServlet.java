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
import java.util.ArrayList;
import java.util.List;

@WebServlet("/admin/usuario/modificar")
public class AdminModificarUsuarioServlet extends HttpServlet {
    private static final int ID_ROL_ADMIN = 1;

    // private boolean esSolicitanteAdmin(int adminIdSolicitante, Connection conn) throws SQLException { ... }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

            int idUsuario = body.getInt("id_usuario");
            String nombre = body.getString("nombre");
            String email = body.getString("email");
            String telefono = body.optString("telefono", null);
            int idRol = body.getInt("id_rol");
            boolean activo = body.getBoolean("activo");
            String password = body.optString("password", null); // Contraseña es opcional

            // Validar que el rol sea admin o repartidor
            if (idRol != ID_ROL_ADMIN && idRol != 3 /*ID_ROL_REPARTIDOR*/) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("error", "Solo se pueden editar usuarios con rol de Administrador o Repartidor.");
                out.print(jsonResponse.toString());
                return;
            }

            // No actualizamos dirección desde aquí, solo datos de usuario principales y rol/estado
            // Si se quisiera, se añadirían los campos de dirección al body y a la query.

            StringBuilder sqlBuilder = new StringBuilder("UPDATE usuario SET nombre = ?, email = ?, telefono = ?, id_rol = ?, activo = ?");
            List<Object> params = new ArrayList<>();
            params.add(nombre);
            params.add(email);
            if (telefono != null && !telefono.isEmpty()) params.add(telefono); else params.add(null); // Para setString o setNull
            params.add(idRol);
            params.add(activo);

            if (password != null && !password.isEmpty()) {
                sqlBuilder.append(", password = ?"); // ¡HASHEAR EN PRODUCCIÓN!
                params.add(password);
            }
            sqlBuilder.append(" WHERE id_usuario = ?");
            params.add(idUsuario);

            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    Object param = params.get(i);
                    if (param instanceof String) stmt.setString(i + 1, (String) param);
                    else if (param instanceof Integer) stmt.setInt(i + 1, (Integer) param);
                    else if (param instanceof Boolean) stmt.setBoolean(i + 1, (Boolean) param);
                    else if (param == null) { // Para el teléfono
                        if (i + 1 == 3) stmt.setNull(i + 1, Types.VARCHAR); // Asumiendo que el teléfono es el 3er '?'
                        // Ajustar el índice si la posición del teléfono cambia
                    }
                }
                // Alternativamente, una forma más explícita si los tipos son fijos:
                // stmt.setString(1, nombre);
                // stmt.setString(2, email);
                // if (telefono != null && !telefono.isEmpty()) stmt.setString(3, telefono); else stmt.setNull(3, Types.VARCHAR);
                // stmt.setInt(4, idRol);
                // stmt.setBoolean(5, activo);
                // int paramIndex = 6;
                // if (password != null && !password.isEmpty()) {
                //     stmt.setString(paramIndex++, password);
                // }
                // stmt.setInt(paramIndex, idUsuario);


                int affectedRows = stmt.executeUpdate();
                if (affectedRows > 0) {
                    jsonResponse.put("status", "success");
                    jsonResponse.put("message", "Usuario modificado exitosamente.");
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("error", "Usuario no encontrado o no se realizaron cambios.");
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
            if (e.getSQLState().equals("23505")) { /* ... manejo de unique constraint ... */ }
            jsonResponse.put("error", errMsg);
            out.print(jsonResponse.toString());
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            if (out != null) out.close();
        }
    }
}