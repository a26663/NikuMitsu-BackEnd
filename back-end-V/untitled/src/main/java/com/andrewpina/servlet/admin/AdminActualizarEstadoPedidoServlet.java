package com.andrewpina.servlet.admin; // Asegúrate que el paquete sea el correcto

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

@WebServlet("/admin/pedido/actualizar-estado")
public class AdminActualizarEstadoPedidoServlet extends HttpServlet {

    private static final int ID_ROL_ADMIN = 1; // Asumiendo que 1 es el rol de administrador

    // Helper para verificar si el usuario es admin
    private boolean esAdmin(int idUsuario, Connection conn) throws SQLException {
        if (idUsuario <= 0) return false;
        String sql = "SELECT id_rol FROM usuario WHERE id_usuario = ? AND activo = TRUE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_rol") == ID_ROL_ADMIN;
            }
        }
        return false;
    }

    // Helper para verificar si un estado de pedido existe
    private boolean existeEstadoPedido(int idEstado, Connection conn) throws SQLException {
        String sql = "SELECT 1 FROM estado_pedido WHERE id_estado = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, idEstado);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }


    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException { // Usar PUT para actualizar un recurso
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        PrintWriter out = resp.getWriter();
        Connection conn = null;

        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = req.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "Error al leer la solicitud.");
            out.print(jsonResponse.toString());
            out.close();
            return;
        }

        try {
            conn = DatabaseConnection.getConnection();
            JSONObject body = new JSONObject(sb.toString());

            int adminIdUsuario = body.getInt("admin_id_usuario");
            int idPedido = body.getInt("id_pedido");
            int nuevoIdEstado = body.getInt("nuevo_id_estado");

            // 1. Verificar si el solicitante es administrador
            if (!esAdmin(adminIdUsuario, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("error", "Acceso denegado. Se requieren permisos de administrador.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            // 2. Verificar si el nuevo estado de pedido es válido (existe en la tabla estado_pedido)
            if (!existeEstadoPedido(nuevoIdEstado, conn)) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("error", "El nuevo estado de pedido proporcionado no es válido.");
                out.print(jsonResponse.toString());
                out.close();
                return;
            }

            // (Opcional) Lógica de transición de estados:
            // Aquí podrías añadir reglas sobre qué estados pueden pasar a qué otros estados.
            // Por ejemplo, un pedido 'entregado' no debería poder volver a 'en_preparacion' fácilmente.
            // Esto requeriría obtener el estado actual del pedido primero.
            // PreparedStatement stmtCheckEstado = conn.prepareStatement("SELECT id_estado FROM pedido WHERE id_pedido = ?");
            // stmtCheckEstado.setInt(1, idPedido);
            // ResultSet rsEstadoActual = stmtCheckEstado.executeQuery();
            // if(rsEstadoActual.next()){
            //    int estadoActual = rsEstadoActual.getInt("id_estado");
            //    if(estadoActual == ID_ESTADO_ENTREGADO && nuevoIdEstado != ID_ESTADO_ENTREGADO){
            //        // Lógica de error o advertencia
            //    }
            // }
            // Por ahora, permitiremos cualquier cambio de estado si el admin lo solicita.

            String sqlUpdate = "UPDATE pedido SET id_estado = ? WHERE id_pedido = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sqlUpdate)) {
                stmt.setInt(1, nuevoIdEstado);
                stmt.setInt(2, idPedido);
                int affectedRows = stmt.executeUpdate();

                if (affectedRows > 0) {
                    jsonResponse.put("status", "success");
                    jsonResponse.put("message", "Estado del pedido #" + idPedido + " actualizado correctamente.");
                    // Si tienes la tabla historial_estado_pedido, aquí harías el INSERT
                    // String sqlHistorial = "INSERT INTO historial_estado_pedido (id_pedido, id_estado_nuevo, id_usuario_cambio) VALUES (?, ?, ?)";
                    // try (PreparedStatement stmtHist = conn.prepareStatement(sqlHistorial)) {
                    //      stmtHist.setInt(1, idPedido);
                    //      stmtHist.setInt(2, nuevoIdEstado);
                    //      stmtHist.setInt(3, adminIdUsuario);
                    //      stmtHist.executeUpdate();
                    // }
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("error", "Pedido no encontrado o no se pudo actualizar el estado.");
                }
            }
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "JSON inválido o campos faltantes: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (SQLException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error de base de datos: " + e.getMessage());
            out.print(jsonResponse.toString());
        } catch (Exception e) { // Captura general
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error inesperado en el servidor: " + e.getMessage());
            out.print(jsonResponse.toString());
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException ex) { ex.printStackTrace(); }
            }
            if (out != null) {
                out.close();
            }
        }
    }
}