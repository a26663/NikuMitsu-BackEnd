package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/producto-detalle") // Endpoint público
public class ProductoDetalleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject productoJson = new JSONObject();
        String idProductoStr = req.getParameter("id_producto");

        if (idProductoStr == null || idProductoStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try(PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "Falta el parámetro id_producto").toString());
            }
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             PrintWriter out = resp.getWriter()) {
            int idProducto = Integer.parseInt(idProductoStr);
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM producto WHERE id_producto = ?");
            stmt.setInt(1, idProducto);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                productoJson.put("id_producto", rs.getInt("id_producto"));
                productoJson.put("nombre", rs.getString("nombre"));
                productoJson.put("descripcion", rs.getString("descripcion"));
                productoJson.put("precio", rs.getDouble("precio"));
                productoJson.put("imagen", rs.getString("imagen"));
                productoJson.put("id_categoria", rs.getInt("id_categoria"));
                productoJson.put("disponible", rs.getBoolean("disponible"));
                out.print(productoJson.toString());
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                out.print(new JSONObject().put("error", "Producto no encontrado").toString());
            }
        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            try(PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "ID de producto inválido").toString());
            }
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try(PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "Error de base de datos: " + e.getMessage()).toString());
            }
        }
    }
}