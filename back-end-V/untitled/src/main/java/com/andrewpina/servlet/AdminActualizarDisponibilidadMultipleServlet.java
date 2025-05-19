package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/admin/productos/actualizar-disponibilidad-multiple")
public class AdminActualizarDisponibilidadMultipleServlet extends HttpServlet {

    private boolean esAdmin(int idUsuario, Connection conn) throws SQLException {
        // ... (misma función esAdmin que en AdminProductoServlet) ...
        if (idUsuario <= 0) return false;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id_rol FROM usuario WHERE id_usuario = ?")) {
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_rol") == 1; // 1 es admin
            }
        }
        return false;
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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

            int adminIdUsuario = body.optInt("admin_id_usuario", -1);
            if (!esAdmin(adminIdUsuario, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("error", "Acceso denegado.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            JSONArray idsProductosJsonArray = body.getJSONArray("ids_productos");
            boolean nuevoEstadoDisponible = body.getBoolean("disponible");

            if (idsProductosJsonArray == null || idsProductosJsonArray.length() == 0) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("error", "No se proporcionaron IDs de productos.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            List<Integer> idsProductos = new ArrayList<>();
            for (int i = 0; i < idsProductosJsonArray.length(); i++) {
                idsProductos.add(idsProductosJsonArray.getInt(i));
            }

            conn.setAutoCommit(false); // Iniciar transacción

            // Construir la query con placeholders IN (?)
            // Esto es un poco más complejo con PreparedStatement para un número variable de IDs.
            // Una forma es construir la cadena de '?' dinámicamente.
            // Otra, si el número de IDs no es masivo, es hacer un batch de updates individuales,
            // o usar un array de PostgreSQL si la BD lo soporta y el driver también.

            // Opción: Batch de updates individuales (más simple de implementar que IN dinámico)
            String sql = "UPDATE producto SET disponible = ? WHERE id_producto = ?";
            int[] resultadosBatch;
            int actualizadosConExito = 0;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Integer idProducto : idsProductos) {
                    stmt.setBoolean(1, nuevoEstadoDisponible);
                    stmt.setInt(2, idProducto);
                    stmt.addBatch();
                }
                resultadosBatch = stmt.executeBatch();
            }

            for (int res : resultadosBatch) {
                if (res >= 0) { // Statement.SUCCESS_NO_INFO o número de filas afectadas
                    actualizadosConExito++; // Contar cada ejecución exitosa del batch
                }
            }

            conn.commit();

            jsonResponse.put("status", "success");
            jsonResponse.put("message", actualizadosConExito + " de " + idsProductos.size() + " producto(s) actualizados.");
            jsonResponse.put("actualizados", actualizadosConExito);
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            e.printStackTrace();
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "JSON inválido o campos faltantes: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error de base de datos: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (Exception e) {
            e.printStackTrace();
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error inesperado en el servidor: " + e.getMessage());
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