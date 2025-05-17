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

@WebServlet("/productos")
public class ListarProductosServlet extends HttpServlet {
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray productos = new JSONArray();

        try (PrintWriter out = resp.getWriter();
             Connection conn = DatabaseConnection.getConnection()) {

            ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM producto");
            while (rs.next()) {
                JSONObject p = new JSONObject();
                p.put("id_producto", rs.getInt("id_producto"));
                p.put("nombre", rs.getString("nombre"));
                p.put("descripcion", rs.getString("descripcion"));
                p.put("precio", rs.getDouble("precio"));
                p.put("imagen", rs.getString("imagen"));
                productos.put(p);
            }
            out.print(productos);
        } catch (SQLException e) {
            resp.setStatus(500);
            resp.getWriter().print(new JSONObject().put("error", e.getMessage()));
        }
    }
}
