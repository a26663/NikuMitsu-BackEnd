package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import org.json.JSONArray;
import org.json.JSONObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;

@WebServlet("/carrito")
public class VerCarritoServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // MOVER ESTO AL PRINCIPIO DEL TRY o antes
        // resp.setContentType("application/json;charset=UTF-8");
        JSONArray carrito = new JSONArray();
        String idUsuarioStr = req.getParameter("id_usuario");

        if (idUsuarioStr == null || idUsuarioStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json;charset=UTF-8"); // Asegurar content type para error
            try (PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "Falta el parámetro id_usuario").toString());
            }
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection(); // Mover PrintWriter aquí es más seguro
             PrintWriter out = resp.getWriter()) { // Obtener el writer aquí

            resp.setContentType("application/json;charset=UTF-8"); // Establecer ANTES de escribir

            int idUsuario = Integer.parseInt(idUsuarioStr); // Puede lanzar NumberFormatException

            // CORRECCIÓN: La columna imagen se llama "imagen" en la tabla producto, no "recursosProducto"
            // Asumo que "recursosProducto" fue un error y querías "imagen"
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT p.id_producto, p.nombre, ic.cantidad, p.precio, p.imagen " + // Usar p.id_producto para el item
                            "FROM item_carrito ic JOIN producto p ON ic.id_producto = p.id_producto " +
                            "WHERE ic.id_usuario = ?");
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("id_producto", rs.getInt("id_producto")); // IMPORTANTE para referencia
                item.put("producto", rs.getString("nombre"));
                item.put("cantidad", rs.getInt("cantidad"));
                item.put("precio_unitario", rs.getDouble("precio"));
                item.put("imagen", rs.getString("imagen")); // Usar la columna correcta
                carrito.put(item);
            }
            out.print(carrito.toString()); // Es buena práctica usar .toString() explícitamente

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) { // Necesitas obtener el writer de nuevo si falló antes
                out.print(new JSONObject().put("error", "ID de usuario inválido: " + e.getMessage()).toString());
            }
            e.printStackTrace(); // Loguea el error en el servidor
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "Error de base de datos: " + e.getMessage()).toString());
            }
            e.printStackTrace();
        } catch (Exception e) { // Captura general para cualquier otra cosa
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "Error inesperado: " + e.getMessage()).toString());
            }
            e.printStackTrace();
        }
    }
}