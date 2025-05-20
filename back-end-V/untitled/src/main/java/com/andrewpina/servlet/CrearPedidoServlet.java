package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import org.json.JSONObject;
import org.json.JSONException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

// Clase DTO (si no la tienes en un archivo separado)
class ItemCarritoDTO {
    int idProducto;
    int cantidad;
    double precioUnitarioActual;

    public ItemCarritoDTO(int idProducto, int cantidad, double precioUnitarioActual) {
        this.idProducto = idProducto;
        this.cantidad = cantidad;
        this.precioUnitarioActual = precioUnitarioActual;
    }
}
@WebServlet("/crear-pedido")
public class CrearPedidoServlet extends HttpServlet {

    private static final int ID_ESTADO_PEDIDO_INICIAL = 3; // 'en_preparacion' por defecto

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        Connection conn = null;
        PrintWriter out = null;

        int idUsuario = -1;
        String metodoPagoRecibido = null;
        // Nuevos campos de dirección
        String calleEnvio = null, numeroEnvio = null, ciudadEnvio = null, codigoPostalEnvio = null, notasDireccionEnvio = null;

        try {
            out = resp.getWriter();
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = req.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            JSONObject body = new JSONObject(sb.toString());

            idUsuario = body.getInt("id_usuario");
            metodoPagoRecibido = body.getString("metodo_pago"); // Aún lo recibimos aunque no lo guardemos en 'pedido' si la columna fue eliminada

            // Leer los campos de dirección del envío del JSON
            // Estos campos serán obligatorios si 'usarDireccionPerfil' es false en el frontend
            calleEnvio = body.getString("calle_envio");
            numeroEnvio = body.getString("numero_envio");
            ciudadEnvio = body.getString("ciudad_envio");
            codigoPostalEnvio = body.getString("codigo_postal_envio");
            notasDireccionEnvio = body.optString("notas_direccion_envio", null); // Opcional

            if (metodoPagoRecibido == null || metodoPagoRecibido.trim().isEmpty() ||
                    calleEnvio == null || calleEnvio.trim().isEmpty() ||
                    numeroEnvio == null || numeroEnvio.trim().isEmpty() ||
                    ciudadEnvio == null || ciudadEnvio.trim().isEmpty() ||
                    codigoPostalEnvio == null || codigoPostalEnvio.trim().isEmpty()) {
                throw new JSONException("Faltan campos requeridos (método de pago o dirección de envío completa).");
            }


        } catch (JSONException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Datos de solicitud incompletos o inválidos: " + e.getMessage());
            if (out != null) out.print(jsonResponse.toString());
            return;
        } catch (IOException e) {
            // ... (manejo de error como antes)
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error al leer la solicitud: " + e.getMessage());
            if (out != null) out.print(jsonResponse.toString());
            return;
        }

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            List<ItemCarritoDTO> itemsParaPedido = new ArrayList<>();
            double totalPedidoCalculado = 0.0;

            String sqlSelectCarrito = "SELECT ic.id_producto, ic.cantidad, p.precio AS precio_actual " +
                    "FROM item_carrito ic " +
                    "JOIN producto p ON ic.id_producto = p.id_producto " +
                    "WHERE ic.id_usuario = ? AND p.disponible = TRUE";
            try (PreparedStatement stmtSelectCarrito = conn.prepareStatement(sqlSelectCarrito)) {
                stmtSelectCarrito.setInt(1, idUsuario);
                ResultSet rsCarrito = stmtSelectCarrito.executeQuery();
                while (rsCarrito.next()) {
                    itemsParaPedido.add(new ItemCarritoDTO(
                            rsCarrito.getInt("id_producto"),
                            rsCarrito.getInt("cantidad"),
                            rsCarrito.getDouble("precio_actual")
                    ));
                    totalPedidoCalculado += (rsCarrito.getInt("cantidad") * rsCarrito.getDouble("precio_actual"));
                }
            }

            if (itemsParaPedido.isEmpty()) {
                // ... (manejo carrito vacío como antes)
                if (conn != null) conn.rollback(); // Buena práctica aunque no haya inserts aún
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "El carrito está vacío o los productos seleccionados ya no están disponibles.");
                out.print(jsonResponse.toString());
                return;
            }

            // Insertar en la tabla 'pedido' CON la dirección de envío
            // Asumimos que 'metodo_pago' como columna en la tabla 'pedido' ya no existe o no se usa.
            // Si sí existe, puedes añadirla.
            String sqlInsertPedido = "INSERT INTO pedido (id_usuario, id_estado, total, fecha_pedido, " +
                    "calle_envio, numero_envio, ciudad_envio, codigo_postal_envio, notas_direccion_envio) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?) RETURNING id_pedido";
            int idPedidoGenerado = -1;
            try (PreparedStatement stmtInsertPedido = conn.prepareStatement(sqlInsertPedido)) {
                stmtInsertPedido.setInt(1, idUsuario);
                stmtInsertPedido.setInt(2, ID_ESTADO_PEDIDO_INICIAL);
                stmtInsertPedido.setDouble(3, totalPedidoCalculado);
                stmtInsertPedido.setString(4, calleEnvio);
                stmtInsertPedido.setString(5, numeroEnvio);
                stmtInsertPedido.setString(6, ciudadEnvio);
                stmtInsertPedido.setString(7, codigoPostalEnvio);
                if (notasDireccionEnvio != null && !notasDireccionEnvio.trim().isEmpty()) {
                    stmtInsertPedido.setString(8, notasDireccionEnvio);
                } else {
                    stmtInsertPedido.setNull(8, Types.VARCHAR);
                }

                ResultSet rsPedido = stmtInsertPedido.executeQuery();
                if (rsPedido.next()) {
                    idPedidoGenerado = rsPedido.getInt(1);
                } else {
                    throw new SQLException("No se pudo obtener el ID del pedido generado.");
                }
            }

            // ... (Insertar detalles, limpiar carrito, commit como antes) ...
            String sqlInsertDetalle = "INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmtInsertDetalle = conn.prepareStatement(sqlInsertDetalle)) {
                for (ItemCarritoDTO item : itemsParaPedido) {
                    stmtInsertDetalle.setInt(1, idPedidoGenerado);
                    stmtInsertDetalle.setInt(2, item.idProducto);
                    stmtInsertDetalle.setInt(3, item.cantidad);
                    stmtInsertDetalle.setDouble(4, item.precioUnitarioActual);
                    stmtInsertDetalle.addBatch();
                }
                stmtInsertDetalle.executeBatch();
            }

            String sqlDeleteCarrito = "DELETE FROM item_carrito WHERE id_usuario = ?";
            try (PreparedStatement stmtDeleteCarrito = conn.prepareStatement(sqlDeleteCarrito)) {
                stmtDeleteCarrito.setInt(1, idUsuario);
                stmtDeleteCarrito.executeUpdate();
            }

            conn.commit();

            jsonResponse.put("status", "success");
            jsonResponse.put("message", "Pedido #" + idPedidoGenerado + " creado exitosamente.");
            jsonResponse.put("id_pedido", idPedidoGenerado);
            jsonResponse.put("total_pedido", String.format("%.2f", totalPedidoCalculado));
            if (metodoPagoRecibido != null) {
                jsonResponse.put("metodo_pago_confirmado", metodoPagoRecibido); // Devolverlo para el mensaje de éxito
            }


        } catch (SQLException e) {
            // ... (manejo SQLException como antes) ...
            e.printStackTrace();
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error de base de datos al crear el pedido: " + e.getMessage());
        } catch (Exception e) {
            // ... (manejo Exception general como antes) ...
            e.printStackTrace();
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            if (!resp.isCommitted()) resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error inesperado al crear el pedido: " + e.getMessage());
        } finally {
            // ... (cierre de conn y out como antes) ...
            if (conn != null) { try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }}
            if (out != null && !resp.isCommitted()) { out.print(jsonResponse.toString()); out.close(); }
            else if (out != null && resp.isCommitted() && jsonResponse.has("status") && "error".equals(jsonResponse.optString("status"))) {
                System.err.println("CrearPedidoServlet: Error procesado pero la respuesta ya estaba committed. Error JSON: " + jsonResponse.toString());
            }
        }
    }
}
