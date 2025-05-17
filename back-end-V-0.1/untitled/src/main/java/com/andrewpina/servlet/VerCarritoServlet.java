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
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray carrito = new JSONArray();

        String idUsuario = req.getParameter("id_usuario");

        try (PrintWriter out = resp.getWriter();
             Connection conn = DatabaseConnection.getConnection()) {

            PreparedStatement stmt = conn.prepareStatement("SELECT p.nombre, ic.cantidad, p.precio, p.recursosProducto FROM item_carrito ic JOIN producto p ON ic.id_producto = p.id_producto WHERE ic.id_usuario = ?");
            stmt.setInt(1, Integer.parseInt(idUsuario));
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                JSONObject item = new JSONObject();
                item.put("producto", rs.getString("nombre"));
                item.put("cantidad", rs.getInt("cantidad"));
                item.put("precio_unitario", rs.getDouble("precio"));
                item.put("recursosProducto", rs.getString("recursosProducto"));
                carrito.put(item);
            }
            out.print(carrito);
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
