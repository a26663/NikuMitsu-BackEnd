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

    private static final int ID_ESTADO_PEDIDO_INICIAL = 3; // Ahora sería 'en_preparacion'

    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject();
        Connection conn = null;
        PrintWriter out = null;

        try {
            out = resp.getWriter();

            int idUsuario = -1;
            String metodoPagoRecibido = null;

            try (BufferedReader reader = req.getReader()) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                JSONObject body = new JSONObject(sb.toString());
                idUsuario = body.getInt("id_usuario");
                metodoPagoRecibido = body.optString("metodo_pago", null); // Leído pero no guardado en BD
                if (metodoPagoRecibido != null && !metodoPagoRecibido.trim().isEmpty()) {
                    System.out.println("INFO (CrearPedidoServlet): Método de pago recibido del frontend: '" + metodoPagoRecibido + "' (No se guardará en la BD si la columna fue eliminada).");
                }

            } catch (JSONException e) {
                e.printStackTrace();
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "Datos de solicitud incompletos o inválidos: " + e.getMessage());
                out.print(jsonResponse.toString());
                return;
            } catch (IOException e) {
                e.printStackTrace();
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "Error al leer la solicitud: " + e.getMessage());
                out.print(jsonResponse.toString());
                return;
            }

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
                    int idProducto = rsCarrito.getInt("id_producto");
                    int cantidad = rsCarrito.getInt("cantidad");
                    double precioActual = rsCarrito.getDouble("precio_actual");
                    itemsParaPedido.add(new ItemCarritoDTO(idProducto, cantidad, precioActual));
                    totalPedidoCalculado += (cantidad * precioActual);
                }
            }

            if (itemsParaPedido.isEmpty()) {
                if (conn != null) conn.rollback();
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "El carrito está vacío o los productos seleccionados ya no están disponibles.");
                out.print(jsonResponse.toString());
                return;
            }

            // La columna 'repartidor_ha_tomado' tomará su valor DEFAULT FALSE de la BD
            // La columna 'metodo_pago' fue eliminada de esta query en un paso anterior.
            String sqlInsertPedido = "INSERT INTO pedido (id_usuario, id_estado, total, fecha_pedido) " +
                    "VALUES (?, ?, ?, CURRENT_TIMESTAMP) RETURNING id_pedido";
            int idPedidoGenerado = -1;
            try (PreparedStatement stmtInsertPedido = conn.prepareStatement(sqlInsertPedido)) {
                stmtInsertPedido.setInt(1, idUsuario);
                stmtInsertPedido.setInt(2, ID_ESTADO_PEDIDO_INICIAL);
                stmtInsertPedido.setDouble(3, totalPedidoCalculado);
                // No hay setString para metodo_pago si fue eliminado

                ResultSet rsPedido = stmtInsertPedido.executeQuery();
                if (rsPedido.next()) {
                    idPedidoGenerado = rsPedido.getInt(1);
                } else {
                    throw new SQLException("No se pudo obtener el ID del pedido generado.");
                }
            }

            if (idPedidoGenerado == -1) {
                throw new SQLException("Falló la creación del pedido, ID no generado.");
            }

            String sqlInsertDetalle = "INSERT INTO detalle_pedido (id_pedido, id_producto, cantidad, precio_unitario) " +
                    "VALUES (?, ?, ?, ?)";
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
            if (metodoPagoRecibido != null) { // Devolverlo si el frontend lo espera para confirmación
                jsonResponse.put("metodo_pago_confirmado", metodoPagoRecibido);
            }


        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            if (!resp.isCommitted()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error de base de datos al crear el pedido: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); } }
            if (!resp.isCommitted()) {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error inesperado al crear el pedido: " + e.getMessage());
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
            if (out != null && !resp.isCommitted()) {
                out.print(jsonResponse.toString());
                out.close();
            } else if (out != null && resp.isCommitted() && jsonResponse.has("status") && "error".equals(jsonResponse.optString("status"))) {
                System.err.println("CrearPedidoServlet: Error procesado pero la respuesta ya estaba committed. Error JSON: " + jsonResponse.toString());
            }
        }
    }
}