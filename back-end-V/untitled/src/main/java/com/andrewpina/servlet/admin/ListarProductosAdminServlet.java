package com.andrewpina.servlet.admin; // Ponerlo en un subpaquete admin

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
import java.util.ArrayList;
import java.util.List;

@WebServlet("/productos-admin") // Nuevo endpoint para la lista de admin
public class ListarProductosAdminServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONArray productosArray = new JSONArray();
        PrintWriter out = null;

        String idCategoriaFilterStr = req.getParameter("id_categoria");
        Integer idCategoriaFilter = null;
        if (idCategoriaFilterStr != null && !idCategoriaFilterStr.trim().isEmpty()) {
            try {
                idCategoriaFilter = Integer.parseInt(idCategoriaFilterStr);
            } catch (NumberFormatException e) {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try(PrintWriter errOut = resp.getWriter()) {
                    errOut.print(new JSONObject().put("error", "ID de categoría para filtro inválido.").toString());
                }
                return;
            }
        }

        try {
            out = resp.getWriter();
            Connection conn = DatabaseConnection.getConnection();

            StringBuilder sqlBuilder = new StringBuilder("SELECT id_producto, nombre, descripcion, precio, imagen, id_categoria, disponible FROM producto");
            List<Object> params = new ArrayList<>();

            if (idCategoriaFilter != null) {
                sqlBuilder.append(" WHERE id_categoria = ?");
                params.add(idCategoriaFilter);
            }
            sqlBuilder.append(" ORDER BY nombre");

            try (PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }

                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    JSONObject p = new JSONObject();
                    p.put("id_producto", rs.getInt("id_producto"));
                    p.put("nombre", rs.getString("nombre"));
                    p.put("descripcion", rs.getString("descripcion"));
                    p.put("precio", rs.getDouble("precio"));
                    p.put("imagen", rs.getString("imagen"));
                    p.put("id_categoria", rs.getInt("id_categoria"));
                    p.put("disponible", rs.getBoolean("disponible"));
                    productosArray.put(p);
                }
            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
                }
            }
            out.print(productosArray.toString());

        } catch (SQLException e) {
            handleError(resp, out, "Error de base de datos al listar productos (admin): " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
        } catch (Exception e) {
            handleError(resp, out, "Error inesperado al listar productos (admin): " + e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    private void handleError(HttpServletResponse resp, PrintWriter currentOut, String message, int statusCode, Exception e) throws IOException {
        if (e != null) e.printStackTrace();

        JSONObject errorJson = new JSONObject();
        errorJson.put("status", "error");
        errorJson.put("message", message);

        PrintWriter  writerForError = currentOut;
        // Si la respuesta ya está committed o el writer es null, no podemos cambiar status ni content-type
        // ni usar el writer original. Intentamos obtener uno nuevo si es posible.
        if (resp.isCommitted() || writerForError == null) {
            System.err.println("Respuesta ya committed o writer nulo al intentar enviar error: " + message);
            // Si el writer original no estaba disponible y no se pudo enviar error,
            // el servidor podría enviar una página HTML de error.
            // No hay mucho más que hacer aquí si currentOut ya fue usado/cerrado/es nulo.
            if (!resp.isCommitted()) { // Solo si no se ha enviado nada aún
                resp.setStatus(statusCode); // Establecer status si es posible
                resp.setContentType("application/json;charset=UTF-8"); // Y content type
                try (PrintWriter newOut = resp.getWriter()) {
                    newOut.print(errorJson.toString());
                } catch (IOException ioEx) {
                    System.err.println("Error al obtener writer para mensaje de error: " + ioEx.getMessage());
                }
            }
            return;
        }

        // Si el writer está disponible y la respuesta no está committed
        if (!resp.isCommitted()) {
            resp.setStatus(statusCode);
            // ContentType ya debería estar seteado al inicio, pero por si acaso.
            // resp.setContentType("application/json;charset=UTF-8");
        }
        writerForError.print(errorJson.toString());
    }
}