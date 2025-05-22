package com.andrewpina.servlet.admin;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/admin/usuarios") // Mantenemos el endpoint base
public class AdminListarUsuariosServlet extends HttpServlet {

    // Constantes para roles (podrías tenerlas en una clase de utilidades o config)
    private static final int ID_ROL_ADMIN = 1;
    private static final int ID_ROL_CLIENTE = 2;
    private static final int ID_ROL_REPARTIDOR = 3;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray usuariosArray = new JSONArray();
        PrintWriter out = null;
        Connection conn = null;

        String idRolFilterStr = req.getParameter("id_rol");
        Integer idRolFilter = null;
        String listarStaffSolamente = req.getParameter("staff_only"); // Nuevo parámetro opcional

        if (idRolFilterStr != null && !idRolFilterStr.trim().isEmpty()) {
            try {
                idRolFilter = Integer.parseInt(idRolFilterStr);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try (PrintWriter errOut = resp.getWriter()) {
                    errOut.print(new JSONObject().put("error", "ID de rol para filtro inválido: " + idRolFilterStr).toString());
                }
                return;
            }
        }

        try {
            out = resp.getWriter();
            conn = DatabaseConnection.getConnection();

            System.out.println("INFO (AdminListarUsuariosServlet): Buscando usuarios. Filtro de rol ID: " +
                    (idRolFilter != null ? idRolFilter : "NINGUNO") +
                    ", Staff Only: " + listarStaffSolamente);

            StringBuilder sqlBuilder = new StringBuilder(
                    "SELECT u.id_usuario, u.nombre, u.email, u.telefono, u.activo, u.id_rol, r.nombre as nombre_rol " +
                            "FROM usuario u " +
                            "JOIN rol r ON u.id_rol = r.id_rol"
            );
            List<Object> params = new ArrayList<>();
            boolean whereClauseAdded = false;

            if (idRolFilter != null) {
                sqlBuilder.append(" WHERE u.id_rol = ?");
                params.add(idRolFilter);
                whereClauseAdded = true;
            } else if (listarStaffSolamente != null && "true".equalsIgnoreCase(listarStaffSolamente)) {
                // Si se pide solo staff (admin y repartidores) y no un rol específico
                sqlBuilder.append(" WHERE u.id_rol IN (?, ?)"); // Asumiendo que ID_ROL_ADMIN es 1 y ID_ROL_REPARTIDOR es 3
                params.add(ID_ROL_ADMIN);
                params.add(ID_ROL_REPARTIDOR);
                whereClauseAdded = true;
            }
            // Si no hay idRolFilter ni listarStaffSolamente, no se añade WHERE, por lo que se listan TODOS.

            sqlBuilder.append(" ORDER BY u.nombre ASC");

            System.out.println("INFO (AdminListarUsuariosServlet): Ejecutando SQL: " + sqlBuilder.toString());

            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }

                ResultSet rs = stmt.executeQuery();
                int contador = 0;
                while (rs.next()) {
                    contador++;
                    JSONObject usuarioJson = new JSONObject();
                    usuarioJson.put("id_usuario", rs.getInt("id_usuario"));
                    usuarioJson.put("nombre", rs.getString("nombre"));
                    usuarioJson.put("email", rs.getString("email"));
                    usuarioJson.put("telefono", rs.getString("telefono")); // Puede ser null
                    usuarioJson.put("activo", rs.getBoolean("activo"));
                    usuarioJson.put("id_rol", rs.getInt("id_rol"));
                    usuarioJson.put("nombre_rol", rs.getString("nombre_rol"));
                    usuariosArray.put(usuarioJson);
                }
                System.out.println("INFO (AdminListarUsuariosServlet): Total de usuarios devueltos: " + contador);
            }
            out.print(usuariosArray.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error de BD al listar usuarios: " + e.getMessage()).toString());
        } catch (Exception e) { // Para JSONException u otros
            e.printStackTrace();
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            if (out != null) out.print(new JSONObject().put("error", "Error inesperado al listar usuarios: " + e.getMessage()).toString());
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