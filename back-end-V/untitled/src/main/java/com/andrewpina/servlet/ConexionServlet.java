package com.andrewpina.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet("/conexion")
public class ConexionServlet extends HttpServlet {

    private static final String DB_URL = "jdbc:postgresql://postgres.cfqo60y4y5yx.us-east-1.rds.amazonaws.com:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "AndrewPina1";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json;charset=UTF-8");

        // Crear el objeto JSON que contendrá la respuesta
        JSONObject jsonResponse = new JSONObject();

        try (PrintWriter out = resp.getWriter()) {
            // Cargar el driver de PostgreSQL
            Class.forName("org.postgresql.Driver");

            // Establecer conexión con la base de datos
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Realizar una consulta a la base de datos para obtener los usuarios
                String sql = "SELECT id_usuario, nombre, email FROM usuario LIMIT 5";  // Cambia la consulta según lo que necesites
                try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {

                    // Crear un arreglo JSON para almacenar los usuarios
                    JSONArray usersArray = new JSONArray();

                    while (rs.next()) {
                        // Crear un objeto JSON para cada usuario
                        JSONObject user = new JSONObject();
                        user.put("id_usuario", rs.getInt("id_usuario"));
                        user.put("nombre", rs.getString("nombre"));
                        user.put("email", rs.getString("email"));

                        // Agregar el objeto usuario al arreglo
                        usersArray.put(user);
                    }

                    // Agregar el arreglo de usuarios a la respuesta JSON
                    jsonResponse.put("usuarios", usersArray);
                    jsonResponse.put("status", "success");

                } catch (SQLException e) {
                    jsonResponse.put("status", "error");
                    jsonResponse.put("message", "Error al consultar la base de datos: " + e.getMessage());
                }
            } catch (SQLException e) {
                jsonResponse.put("status", "error");
                jsonResponse.put("message", "Error de conexión: " + e.getMessage());
            }

            // Escribir la respuesta JSON
            out.println(jsonResponse.toString());
        } catch (ClassNotFoundException e) {
            throw new ServletException("Driver PostgreSQL no encontrado", e);
        }
    }
}
