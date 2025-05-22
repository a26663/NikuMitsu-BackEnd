// ListarProductosServlet.java
package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import jakarta.servlet.ServletException;
import java.io.*;
import java.sql.*;

@WebServlet("/productos") // Este es el endpoint para los clientes/compradores
public class ListarProductosServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray productosArray = new JSONArray(); // Renombrado para claridad
        PrintWriter out = null; // Declarar fuera para usar en catch

        try {
            out = resp.getWriter(); // Obtener writer
            Connection conn = DatabaseConnection.getConnection();

            // MODIFICACIÓN: Añadir WHERE disponible = TRUE a la consulta
            String sql = "SELECT id_producto, nombre, descripcion, precio, imagen, id_categoria, disponible FROM producto WHERE disponible = TRUE ORDER BY nombre";
            // Usar PreparedStatement es generalmente mejor, aunque aquí no hay parámetros de usuario
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    JSONObject p = new JSONObject();
                    p.put("id_producto", rs.getInt("id_producto"));
                    p.put("nombre", rs.getString("nombre"));
                    p.put("descripcion", rs.getString("descripcion")); // Asegúrate que no sea null si el frontend no lo maneja
                    p.put("precio", rs.getDouble("precio"));
                    p.put("imagen", rs.getString("imagen")); // Asegúrate que no sea null si el frontend no lo maneja
                    p.put("id_categoria", rs.getInt("id_categoria"));
                    p.put("disponible", rs.getBoolean("disponible")); // Aunque siempre será true, puede ser útil para el frontend
                    productosArray.put(p);
                }
            } finally { // Asegurar que la conexión se cierre
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (SQLException e) {
                        e.printStackTrace(); // Loguear error al cerrar conexión
                    }
                }
            }
            out.print(productosArray.toString());

        } catch (SQLException e) {
            e.printStackTrace(); // Loguear el error completo en el servidor
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            JSONObject errorJson = new JSONObject();
            errorJson.put("status", "error");
            errorJson.put("message", "Error de base de datos al listar productos: " + e.getMessage());
            // Re-obtener el writer si falló antes o si el 'out' del try no se inicializó
            // y la respuesta no está committed.
            if (out == null && !resp.isCommitted()) {
                out = resp.getWriter();
            }
            if (out != null && !resp.isCommitted()) { // Solo escribir si no se ha enviado nada
                out.print(errorJson.toString());
            }
        } catch (Exception e) { // Captura para otros errores inesperados
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            JSONObject errorJson = new JSONObject();
            errorJson.put("status", "error");
            errorJson.put("message", "Error inesperado al listar productos: " + e.getMessage());
            if (out == null && !resp.isCommitted()) {
                out = resp.getWriter();
            }
            if (out != null && !resp.isCommitted()) {
                out.print(errorJson.toString());
            }
        } finally {
            if (out != null) {
                out.close(); // Cerrar el writer
            }
        }
    }
}