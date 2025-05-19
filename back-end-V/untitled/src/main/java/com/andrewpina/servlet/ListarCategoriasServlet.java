package com.andrewpina.servlet;

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

@WebServlet("/categorias")
public class ListarCategoriasServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray categoriasArray = new JSONArray();
        // Asegúrate de que el nombre de la tabla y columnas sean correctos
        // TU TABLA SE LLAMA 'categoria' y las columnas 'id_categoria', 'nombre'
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id_categoria, nombre, descripcion FROM categoria ORDER BY nombre"); // Asumiendo que 'descripcion' también existe
             PrintWriter out = resp.getWriter()) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                JSONObject categoriaJson = new JSONObject();
                categoriaJson.put("id_categoria", rs.getInt("id_categoria"));
                categoriaJson.put("nombre", rs.getString("nombre"));
                // categoriaJson.put("descripcion", rs.getString("descripcion")); // Opcional, si lo necesitas en el select o en otro lado
                categoriasArray.put(categoriaJson);
            }
            out.print(categoriasArray.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // Envía un JSON de error
            JSONObject errorJson = new JSONObject();
            errorJson.put("error", "Error al cargar categorías: " + e.getMessage());
            try (PrintWriter out = resp.getWriter()) { // Re-obtener writer si el anterior falló en el try principal
                out.print(errorJson.toString());
            } catch (IOException ioEx) {
                ioEx.printStackTrace(); // Error al escribir el error
            }
        }
    }
}