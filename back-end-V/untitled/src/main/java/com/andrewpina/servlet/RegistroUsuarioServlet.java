package com.andrewpina.servlet;

import com.andrewpina.util.DatabaseConnection;
import jakarta.servlet.ServletException;
import org.json.JSONObject;
import org.json.JSONException; // Importar para manejar errores de parseo de JSON

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;

@WebServlet("/registro")
public class RegistroUsuarioServlet extends HttpServlet {
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");
        JSONObject jsonResponse = new JSONObject(); // Usar un objeto para la respuesta

        String nombre = null, email = null, password = null, telefono = null,
                calle = null, numero = null, ciudad = null, codigoPostal = null;

        try (BufferedReader reader = req.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            JSONObject body = new JSONObject(sb.toString());

            // Extraer todos los campos requeridos y opcionales
            nombre = body.getString("nombre");
            email = body.getString("email");
            password = body.getString("password"); // ¡ALMACENAR HASHEADO EN PRODUCCIÓN!
            calle = body.getString("calle");
            numero = body.getString("numero");
            ciudad = body.getString("ciudad");
            codigoPostal = body.getString("codigo_postal");
            telefono = body.optString("telefono", null); // Opcional, default a null si no existe

        } catch (JSONException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Datos de solicitud incompletos o en formato incorrecto: " + e.getMessage());
            try(PrintWriter out = resp.getWriter()) { out.print(jsonResponse.toString()); }
            return;
        } catch (IOException e) {
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Error al leer la solicitud: " + e.getMessage());
            try(PrintWriter out = resp.getWriter()) { out.print(jsonResponse.toString()); }
            return;
        }

        // Validaciones básicas adicionales en backend (aunque el frontend ya haga algunas)
        if (nombre.trim().isEmpty() || email.trim().isEmpty() || password.trim().isEmpty() ||
                calle.trim().isEmpty() || numero.trim().isEmpty() || ciudad.trim().isEmpty() || codigoPostal.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Todos los campos marcados como obligatorios deben ser completados.");
            try(PrintWriter out = resp.getWriter()) { out.print(jsonResponse.toString()); }
            return;
        }
        // Aquí podrías añadir más validaciones, como formato de email, fortaleza de contraseña, etc.


        try (Connection conn = DatabaseConnection.getConnection();
             PrintWriter out = resp.getWriter()) {

            // conn.setAutoCommit(true); // Es true por defecto, no necesitas establecerlo a menos que lo hayas cambiado.

            // Usar id_rol = 2 para 'cliente' por defecto
            String sql = "INSERT INTO usuario (nombre, email, password, telefono, calle, numero, ciudad, codigo_postal, id_rol) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 2)";
            PreparedStatement stmt = conn.prepareStatement(sql);

            stmt.setString(1, nombre);
            stmt.setString(2, email);
            stmt.setString(3, password); // ¡RECUERDA HASHEAR ESTO EN UN SISTEMA REAL!
            if (telefono != null && !telefono.trim().isEmpty()) {
                stmt.setString(4, telefono);
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }
            stmt.setString(5, calle);
            stmt.setString(6, numero);
            stmt.setString(7, ciudad);
            stmt.setString(8, codigoPostal);

            int filasInsertadas = stmt.executeUpdate();

            if (filasInsertadas > 0) {
                // Antes de confirmar el éxito, asegurar que un carrito se cree para el nuevo usuario
                // Necesitamos el ID del usuario recién insertado.
                // Podríamos hacer un SELECT para obtener el ID basado en el email,
                // o modificar el INSERT para que devuelva el id_usuario (usando RETURNING id_usuario)

                // Opción 1: Usar RETURNING (Más eficiente si tu driver JDBC y BD lo soportan bien)
                // Para esto, el prepareStatement cambiaría y se leería el ID
                // String sqlInsertUsuario = "INSERT INTO usuario (...) VALUES (...) RETURNING id_usuario";
                // ResultSet rsId = stmt.executeQuery(); // o executeUpdate() y getGeneratedKeys() dependiendo del driver
                // if (rsId.next()) { int nuevoUsuarioId = rsId.getInt(1); /* crear carrito con este ID */ }

                // Opción 2: Seleccionar el ID (más compatible, pero dos queries)
                // Lo dejaremos sin creación automática de carrito aquí para simplificar,
                // el carrito se podría crear en el primer intento de añadir algo o al hacer login.
                // O, si la tabla carrito solo tiene id_usuario como PK, la FK en item_carrito
                // necesitaría ON UPDATE CASCADE si el id_usuario pudiera cambiar (no es el caso aquí).
                // Por ahora, el usuario podrá hacer login y el carrito se gestionará como antes.

                jsonResponse.put("status", "success");
                jsonResponse.put("message", "Usuario registrado correctamente.");
            } else {
                // Esto es inusual si no hubo excepción, podría indicar un problema no esperado.
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "No se pudo registrar el usuario. Inténtalo de nuevo.");
            }
            out.print(jsonResponse.toString());

        } catch (SQLException e) {
            e.printStackTrace();
            String errorMessage = "Error de base de datos al registrar el usuario.";
            // Código de error para violación de restricción unique en PostgreSQL es "23505"
            if ("23505".equals(e.getSQLState())) {
                if (e.getMessage().toLowerCase().contains("uq_usuario_email")) {
                    errorMessage = "El correo electrónico ya está registrado. Por favor, usa otro.";
                } else if (e.getMessage().toLowerCase().contains("uq_usuario_telefono") && telefono != null && !telefono.isEmpty()) {
                    errorMessage = "El número de teléfono ya está registrado.";
                } else {
                    errorMessage = "Un valor único ya existe: " + e.getMessage();
                }
                resp.setStatus(HttpServletResponse.SC_CONFLICT); // 409 Conflict
            } else {
                resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500 para otros errores SQL
            }
            jsonResponse.put("status", "error");
            jsonResponse.put("message", errorMessage);
            try(PrintWriter out = resp.getWriter()) { out.print(jsonResponse.toString()); }
        } catch (Exception e) { // Captura general para otras excepciones
            e.printStackTrace();
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("status", "error");
            jsonResponse.put("message", "Ocurrió un error inesperado en el servidor: " + e.getMessage());
            try(PrintWriter out = resp.getWriter()) { out.print(jsonResponse.toString()); }
        }
    }
}