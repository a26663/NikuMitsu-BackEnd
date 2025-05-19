package com.andrewpina.servlet.admin;

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
import java.sql.Statement;
import java.sql.Types;

@WebServlet({"/admin/producto/agregar", "/admin/producto/modificar"})
public class AdminProductoServlet extends HttpServlet {

    private boolean esAdmin(int idUsuario, Connection conn) throws SQLException {
        if (idUsuario <= 0) return false;
        try (PreparedStatement stmt = conn.prepareStatement("SELECT id_rol FROM usuario WHERE id_usuario = ?")) {
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_rol") == 1; // 1 es admin
            }
        }
        return false;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processProducto(req, resp, "POST");
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        processProducto(req, resp, "PUT");
    }

    private void processProducto(HttpServletRequest req, HttpServletResponse resp, String method) throws IOException {
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
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("error", "Error al leer la solicitud.");
            out.print(jsonResponse.toString());
            out.close(); // Asegurar cierre
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            JSONObject body = new JSONObject(sb.toString());

            int adminIdUsuario = body.optInt("admin_id_usuario", -1);
            if (!esAdmin(adminIdUsuario, conn)) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("error", "Acceso denegado. Se requieren permisos de administrador.");
                out.print(jsonResponse.toString());
                out.close(); // Asegurar cierre
                return;
            }

            if ("POST".equals(method)) { // AGREGAR PRODUCTO
                // Para AGREGAR, todos estos campos son esperados (excepto opcionales)
                String nombre = body.getString("nombre");
                String descripcion = body.optString("descripcion", null);
                double precio = body.getDouble("precio");
                String imagen = body.optString("imagen", null);
                int idCategoria = body.getInt("id_categoria");
                boolean disponible = body.optBoolean("disponible", true);

                String sql = "INSERT INTO producto (nombre, descripcion, precio, imagen, id_categoria, disponible) VALUES (?, ?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    stmt.setString(1, nombre);
                    setNullableString(stmt, 2, descripcion);
                    stmt.setDouble(3, precio);
                    setNullableString(stmt, 4, imagen);
                    stmt.setInt(5, idCategoria);
                    stmt.setBoolean(6, disponible);

                    int affectedRows = stmt.executeUpdate();
                    if (affectedRows > 0) {
                        jsonResponse.put("status", "success");
                        jsonResponse.put("message", "Producto agregado exitosamente.");
                        try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                            if (generatedKeys.next()) {
                                jsonResponse.put("id_producto_creado", generatedKeys.getInt(1));
                            }
                        }
                    } else {
                        throw new SQLException("No se pudo agregar el producto.");
                    }
                }
            } else if ("PUT".equals(method)) { // MODIFICAR PRODUCTO
                int idProducto = body.getInt("id_producto"); // Requerido para modificar

                // Verificar si es una actualización completa (desde el modal) o solo de disponibilidad
                if (body.has("nombre") && body.has("precio") && body.has("id_categoria")) {
                    // Actualización completa del producto
                    String nombre = body.getString("nombre");
                    String descripcion = body.optString("descripcion", null);
                    double precio = body.getDouble("precio");
                    String imagen = body.optString("imagen", null);
                    int idCategoria = body.getInt("id_categoria");
                    boolean disponible = body.getBoolean("disponible"); // 'disponible' debe estar en el body para update completo

                    String sql = "UPDATE producto SET nombre = ?, descripcion = ?, precio = ?, imagen = ?, id_categoria = ?, disponible = ? WHERE id_producto = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, nombre);
                        setNullableString(stmt, 2, descripcion);
                        stmt.setDouble(3, precio);
                        setNullableString(stmt, 4, imagen);
                        stmt.setInt(5, idCategoria);
                        stmt.setBoolean(6, disponible);
                        stmt.setInt(7, idProducto);
                        stmt.executeUpdate();
                        jsonResponse.put("message", "Producto modificado completamente exitosamente.");
                    }
                } else if (body.has("disponible")) {
                    // Actualización solo del estado 'disponible'
                    boolean disponible = body.getBoolean("disponible");
                    String sql = "UPDATE producto SET disponible = ? WHERE id_producto = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setBoolean(1, disponible);
                        stmt.setInt(2, idProducto);
                        stmt.executeUpdate();
                        jsonResponse.put("message", "Disponibilidad del producto actualizada exitosamente.");
                    }
                } else {
                    // Si no es ni actualización completa ni solo de disponibilidad, es un request inválido para PUT
                    resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    jsonResponse.put("error", "Solicitud de modificación inválida. Faltan campos necesarios.");
                    out.print(jsonResponse.toString());
                    out.close(); // Asegurar cierre
                    return;
                }
                jsonResponse.put("status", "success");
                jsonResponse.put("id_producto_modificado", idProducto);
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
        } catch (Exception e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Error inesperado en el servidor: " + e.getMessage());
            out.print(jsonResponse.toString());
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private void setNullableString(PreparedStatement stmt, int parameterIndex, String value) throws SQLException {
        if (value != null && !value.trim().isEmpty()) {
            stmt.setString(parameterIndex, value);
        } else {
            stmt.setNull(parameterIndex, Types.VARCHAR);
        }
    }
}