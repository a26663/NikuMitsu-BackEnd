package com.andrewpina.servlet; // O donde tengas otros servlets generales

import com.andrewpina.util.DatabaseConnection;
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

@WebServlet("/estados-pedido")
public class ListarEstadosPedidoServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray estadosArray = new JSONArray();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id_estado, nombre FROM estado_pedido ORDER BY id_estado");
             PrintWriter out = resp.getWriter()) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                JSONObject estadoJson = new JSONObject();
                estadoJson.put("id_estado", rs.getInt("id_estado"));
                estadoJson.put("nombre", rs.getString("nombre"));
                estadosArray.put(estadoJson);
            }
            out.print(estadosArray.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try (PrintWriter out = resp.getWriter()) { // Re-obtener si es necesario
                out.print(new JSONObject().put("error", "Error al cargar estados de pedido: " + e.getMessage()).toString());
            }
        }
    }
}