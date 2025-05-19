package com.andrewpina.servlet;

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
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/repartidor/pedido/actualizar-estado")
public class RepartidorActualizarEstadoPedidoServlet extends HttpServlet {
    private static final int ID_ROL_REPARTIDOR = 3;

    // Helper para verificar si el usuario es repartidor
    private boolean esRepartidorValido(int idUsuario, Connection conn) throws SQLException {
        // ... (misma función esRepartidorValido que en RepartidorPedidosServlet) ...
        if (idUsuario <= 0) return false;
        String sql = "SELECT id_rol FROM usuario WHERE id_usuario = ? AND activo = TRUE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_rol") == ID_ROL_REPARTIDOR;
            }
        }
        return false;
    }

    // Helper para verificar si el repartidor está asignado a ESE pedido
    private boolean esRepartidorAsignado(int idRepartidor, int idPedido, Connection conn) throws SQLException {
        String sql = "SELECT 1 FROM pedido WHERE id_pedido = ? AND id_repartidor_asignado = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idPedido);
            stmt.setInt(2, idRepartidor);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { // Usamos PUT para actualizar
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
            // ... (manejo de error)
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "Error al leer la solicitud.");
            out.print(jsonResponse.toString());
            out.close();
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            JSONObject body = new JSONObject(sb.toString());

            int idRepartidor = body.getInt("id_repartidor");
            int idPedido = body.getInt("id_pedido");
            int nuevoIdEstado = body.getInt("nuevo_id_estado");

            // Validaciones
            if (!esRepartidorValido(idRepartidor, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("error", "Usuario no es un repartidor válido o está inactivo.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }
            if (!esRepartidorAsignado(idRepartidor, idPedido, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("error", "Este pedido no está asignado a usted o no existe.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            // Aquí podrías añadir lógica para validar las transiciones de estado
            // (ej. no se puede pasar de 'pendiente' a 'entregado' directamente)

            String sqlUpdate = "UPDATE pedido SET id_estado = ? WHERE id_pedido = ? AND id_repartidor_asignado = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                stmt.setInt(1, nuevoIdEstado);
                stmt.setInt(2, idPedido);
                stmt.setInt(3, idRepartidor); // Doble chequeo
                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    jsonResponse.put("status", "success");
                    jsonResponse.put("message", "Estado del pedido #" + idPedido + " actualizado.");
                    // Si implementas historial_estado_pedido, aquí harías el INSERT.
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND); // O Internal Server Error si debería haber actualizado
                    jsonResponse.put("error", "No se pudo actualizar el estado del pedido. Verifique que el pedido exista y esté asignado a usted.");
                }
            }
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            // ... (manejo de error)
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "JSON inválido o campos faltantes: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            // ... (manejo de error)
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error de base de datos: " + e.getMessage());
            out.print(jsonResponse.toString());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }
}