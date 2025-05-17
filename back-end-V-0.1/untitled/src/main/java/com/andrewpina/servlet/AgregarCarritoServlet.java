package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import org.json.JSONObject;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;

@WebServlet("/agregar-carrito")
public class AgregarCarritoServlet extends HttpServlet {
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject json = new JSONObject();

        try (BufferedReader reader = req.getReader();
             PrintWriter out = resp.getWriter();
             Connection conn = DatabaseConnection.getConnection()) {

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject body = new JSONObject(sb.toString());

            int idUsuario = body.getInt("id_usuario");
            int idProducto = body.getInt("id_producto");
            int cantidad = body.getInt("cantidad");

            PreparedStatement stmt = conn.prepareStatement("INSERT INTO item_carrito (id_usuario, id_producto, cantidad) VALUES (?, ?, ?) ON CONFLICT (id_usuario, id_producto) DO UPDATE SET cantidad = cantidad + ?");
            stmt.setInt(1, idUsuario);
            stmt.setInt(2, idProducto);
            stmt.setInt(3, cantidad);
            stmt.setInt(4, cantidad);
            stmt.executeUpdate();

            json.put("status", "success");
            json.put("message", "Producto agregado al carrito");
            out.print(json);
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
