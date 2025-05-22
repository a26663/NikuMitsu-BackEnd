package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import org.json.JSONObject;
import org.json.JSONException; // Importar JSONException

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;

@WebServlet("/agregar-carrito")
public class AgregarCarritoServlet extends HttpServlet {
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // resp.setContentType("application/json;charset=UTF-8"); // Mover al try o antes de getWriter

        JSONObject jsonResponse = new JSONObject(); // Usar un objeto para la respuesta

        try (BufferedReader reader = req.getReader();
             Connection conn = DatabaseConnection.getConnection();
             PrintWriter out = resp.getWriter()) { // Obtener el writer aquí

            resp.setContentType("application/json;charset=UTF-8"); // Establecer ANTES de escribir

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject body = new JSONObject(sb.toString()); // Puede lanzar JSONException

            int idUsuario = body.getInt("id_usuario"); // Puede lanzar JSONException si no existe o no es int
            int idProducto = body.getInt("id_producto");
            int cantidad = body.getInt("cantidad");

            // Verifica si el carrito existe para el usuario, si no, créalo.
            // Esto es importante si la FK de item_carrito a carrito es estricta y el carrito no se crea automáticamente.
            // En tu schema, carrito se crea manualmente, así que esto es más una salvaguarda o podría no ser necesario
            // si garantizas que el carrito siempre existe.
            try (PreparedStatement checkCartStmt = conn.prepareStatement("SELECT 1 FROM carrito WHERE id_usuario = ?")) {
                checkCartStmt.setInt(1, idUsuario);
                ResultSet rsCart = checkCartStmt.executeQuery();
                if (!rsCart.next()) {
                    try (PreparedStatement createCartStmt = conn.prepareStatement("INSERT INTO carrito (id_usuario) VALUES (?)")) {
                        createCartStmt.setInt(1, idUsuario);
                        createCartStmt.executeUpdate();
                    }
                }
            }


            // Lógica ON CONFLICT DO UPDATE SET cantidad = item_carrito.cantidad + EXCLUDED.cantidad
            // El valor EXCLUDED.cantidad es el que se intentó insertar (en este caso, el nuevo 'cantidad')
            // Y item_carrito.cantidad es el valor existente.
            // Si solo quieres sumar la nueva cantidad a la existente:
            // ON CONFLICT (id_usuario, id_producto) DO UPDATE SET cantidad = item_carrito.cantidad + EXCLUDED.cantidad;
            // Si quieres reemplazar con la nueva cantidad (como lo tenías antes, pero menos común para "agregar"):
            // ON CONFLICT (id_usuario, id_producto) DO UPDATE SET cantidad = EXCLUDED.cantidad;

            // Tu SQL original: ON CONFLICT (id_usuario, id_producto) DO UPDATE SET cantidad = cantidad + ?
            // El placeholder "?" en la parte DO UPDATE se refiere al mismo valor que el 4to placeholder.
            // Esto es correcto si quieres que `cantidad` se sume a sí misma.
            // Si quieres sumar la nueva cantidad a la existente, sería:
            // "INSERT INTO item_carrito (id_usuario, id_producto, cantidad) VALUES (?, ?, ?)
            //  ON CONFLICT (id_usuario, id_producto) DO UPDATE SET cantidad = item_carrito.cantidad + ?"
            //  Luego stmt.setInt(4, cantidad) para la parte del UPDATE.

            // Manteniendo tu lógica original que suma el 'cantidad' entrante al existente:
            String sql = "INSERT INTO item_carrito (id_usuario, id_producto, cantidad) VALUES (?, ?, ?) " +
                    "ON CONFLICT (id_usuario, id_producto) DO UPDATE SET cantidad = item_carrito.cantidad + EXCLUDED.cantidad";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, idUsuario);
            stmt.setInt(2, idProducto);
            stmt.setInt(3, cantidad); // Para el INSERT
            // No se necesita el cuarto placeholder si usas EXCLUDED.cantidad

            int affectedRows = stmt.executeUpdate();

            if (affectedRows > 0) {
                jsonResponse.put("status", "success");
                // Verificar si fue inserción o actualización para el mensaje (opcional pero bueno)
                // Esto requeriría una consulta adicional o una función de PostgreSQL que devuelva si fue insert o update.
                // Por simplicidad, un mensaje genérico:
                jsonResponse.put("message", "Producto actualizado/agregado en el carrito.");
            } else {
                // Esto no debería ocurrir con ON CONFLICT si la lógica es correcta,
                // a menos que haya un trigger o algo muy extraño.
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "No se pudo agregar o actualizar el producto en el carrito.");
            }
            out.print(jsonResponse.toString());

        } catch (JSONException e) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request
            resp.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) { // Necesitas obtener el writer de nuevo
                out.print(new JSONObject().put("error", "JSON de solicitud inválido: " + e.getMessage()).toString());
            }
            e.printStackTrace();
        } catch (SQLException e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500
            resp.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "Error de base de datos: " + e.getMessage()).toString());
            }
            e.printStackTrace();
        } catch (Exception e) { // Captura general
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json;charset=UTF-8");
            try (PrintWriter out = resp.getWriter()) {
                out.print(new JSONObject().put("error", "Error inesperado: " + e.getMessage()).toString());
            }
            e.printStackTrace();
        }
    }
}