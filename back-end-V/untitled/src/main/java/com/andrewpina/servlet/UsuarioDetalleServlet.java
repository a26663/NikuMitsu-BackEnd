package com.andrewpina.servlet; // O tu paquete

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/usuario/detalles")
public class UsuarioDetalleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = resp.getWriter();

        String idUsuarioStr = req.getParameter("id_usuario");
        if (idUsuarioStr == null || idUsuarioStr.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "Falta el parámetro id_usuario.");
            out.print(jsonResponse.toString());
            out.close();
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            int idUsuario = Integer.parseInt(idUsuarioStr);

            // Aquí podrías añadir una verificación para asegurar que el usuario solicitante
            // solo pueda ver sus propios detalles, a menos que sea un admin.
            // Por ahora, es simple.

            String sql = "SELECT nombre, email, telefono, calle, numero, ciudad, codigo_postal, id_rol " +
                    "FROM usuario WHERE id_usuario = ? AND activo = TRUE";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, idUsuario);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    jsonResponse.put("id_usuario", idUsuario); // Opcional, ya lo tenemos
                    jsonResponse.put("nombre", rs.getString("nombre"));
                    jsonResponse.put("email", rs.getString("email"));
                    jsonResponse.put("telefono", rs.getString("telefono"));
                    jsonResponse.put("calle", rs.getString("calle"));
                    jsonResponse.put("numero", rs.getString("numero"));
                    jsonResponse.put("ciudad", rs.getString("ciudad"));
                    jsonResponse.put("codigo_postal", rs.getString("codigo_postal"));
                    jsonResponse.put("id_rol", rs.getInt("id_rol"));
                    // No enviar password
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("error", "Usuario no encontrado o inactivo.");
                }
            }
            out.print(jsonResponse.toString());

        } catch (NumberFormatException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "ID de usuario inválido: " + idUsuarioStr);
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error de base de datos: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (JSONException e) { // Para errores al construir el JSON de error
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            // No intentar enviar JSON si jsonResponse mismo falla
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}