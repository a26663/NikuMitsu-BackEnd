package com.andrewpina.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String DB_URL = "jdbc:postgresql://postgres.cfqo60y4y5yx.us-east-1.rds.amazonaws.com:5432/postgres";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "AndrewPina1";

    private static Connection connection;

    public static Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Class.forName("org.postgresql.Driver");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            } catch (ClassNotFoundException e) {
                throw new SQLException("Driver de PostgreSQL no encontrado.", e);
            }
        }
        return connection;
    }
}
