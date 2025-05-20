package com.andrewpina.servlet; // O tu paquete de servlets

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@WebServlet("/remover-item-carrito")
public class RemoverItemCarritoServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = resp.getWriter();

        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "Error al leer la solicitud.");
            out.print(jsonResponse.toString());
            out.close();
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            JSONObject body = new JSONObject(sb.toString());
            int idUsuario = body.getInt("id_usuario");
            int idProducto = body.getInt("id_producto");

            // Lógica para eliminar el item_carrito
            // Esto elimina todas las unidades del producto.
            // Si quisieras reducir cantidad, la lógica sería diferente.
            String sql = "DELETE FROM item_carrito WHERE id_usuario = ? AND id_producto = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, idUsuario);
                stmt.setInt(2, idProducto);
                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    jsonResponse.put("status", "success");
                    jsonResponse.put("message", "Producto removido del carrito.");
                } else {
                    // Podría ser que el item ya no existiera
                    jsonResponse.put("status", "info"); // O error, según prefieras
                    jsonResponse.put("message", "El producto no se encontró en el carrito o no se pudo remover.");
                }
            }
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "JSON inválido o campos faltantes: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error de base de datos: " + e.getMessage());
            out.print(jsonResponse.toString());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}