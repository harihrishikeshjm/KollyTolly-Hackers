import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class BackendServer {
    private static final String DB_URL = "jdbc:sqlite:whoskilledwhat.db";

    public static void main(String[] args) throws Exception {
        initDatabase();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/api/register", new RegisterHandler());
        server.createContext("/api/get_pending_users", new GetPendingUsersHandler());
        server.createContext("/api/approve_user", new ApproveUserHandler());
        server.createContext("/api/login", new LoginHandler());
        server.createContext("/api/change_password", new ChangePasswordHandler());
        server.createContext("/api/update_profile", new UpdateProfileHandler());
        server.createContext("/api/send_request", new SendRequestHandler());
        server.createContext("/api/get_requests", new GetRequestsHandler());
        server.createContext("/api/update_status", new UpdateStatusHandler());
        server.createContext("/api/post_opportunity", new PostOpportunityHandler()); 
        server.createContext("/api/get_opportunities", new GetOpportunitiesHandler()); 
        server.createContext("/api/get_profile", new GetProfileHandler());
        server.createContext("/api/search_teachers", new SearchTeachersHandler()); 
        server.createContext("/api/search_events", new SearchEventsHandler()); 
        server.setExecutor(null);
        server.start();
        System.out.println("Server started on http://localhost:8080");
    }

    private static void initDatabase() {
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                
                String sqlUsers = "CREATE TABLE IF NOT EXISTS users (" +
                                  "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                  "name TEXT, " +
                                  "role TEXT, " +
                                  "organization TEXT, " + 
                                  "roll_number TEXT, " +
                                  "department TEXT, " +
                                  "email TEXT UNIQUE, " +
                                  "password TEXT, " +
                                  "skills TEXT, " +
                                  "projects TEXT, " +
                                  "is_approved INTEGER" +
                                  ")";
                stmt.execute(sqlUsers);

                String adminPassword = hashPassword("admin@#123");
                String sqlAdmin = "INSERT OR REPLACE INTO users (name, role, email, password, is_approved) VALUES ('Admin', 'admin', 'admin@amrita.edu', '" + adminPassword + "', 1)";
                stmt.execute(sqlAdmin);
                
                String sqlRequests = "CREATE TABLE IF NOT EXISTS requests (" +
                                     "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                     "sender_email TEXT, " +
                                     "receiver_name TEXT, " +
                                     "request_type TEXT, " +
                                     "context TEXT, " +
                                     "status TEXT DEFAULT 'pending', " +
                                     "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
                stmt.execute(sqlRequests);

                String sqlOpp = "CREATE TABLE IF NOT EXISTS opportunities (" +
                                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                                "poster_email TEXT, " +
                                "type TEXT, " +
                                "title TEXT, " +
                                "role_needed TEXT, " +
                                "description TEXT, " +
                                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
                stmt.execute(sqlOpp);
                
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String hashPassword(String password) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static Map<String, String> parseFormData(InputStream is) throws IOException {
        Map<String, String> map = new HashMap<>();
        Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A");
        String rawData = scanner.hasNext() ? scanner.next() : "";
        if (rawData.isEmpty()) return map;
        String[] pairs = rawData.split("&");
        for (String pair : pairs) {
            String[] split = pair.split("=");
            if (split.length == 2) {
                map.put(URLDecoder.decode(split[0], "UTF-8"), URLDecoder.decode(split[1], "UTF-8"));
            }
        }
        return map;
    }

    static class RegisterHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "INSERT INTO users (name, role, roll_number, department, email, password, is_approved, organization) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, data.get("name"));
                    pstmt.setString(2, data.get("role"));
                    pstmt.setString(3, data.get("roll_number"));
                    pstmt.setString(4, data.get("department"));
                    pstmt.setString(5, data.get("email"));
                    pstmt.setString(6, hashPassword(data.get("password"))); 
                    pstmt.setInt(7, (data.get("role").equals("student") || data.get("role").equals("teacher")) ? 1 : 0);
                    pstmt.setString(8, data.get("organization"));
                    pstmt.executeUpdate();
                    
                    String response = "Registered successfully";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
                exchange.sendResponseHeaders(400, -1); 
            }
        }
    }

    static class LoginHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "SELECT * FROM users WHERE email = ? AND password = ?";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, data.get("email"));
                    pstmt.setString(2, hashPassword(data.get("password"))); 
                    
                    ResultSet rs = pstmt.executeQuery();
                    
                    if (rs.next()) {
                        if (rs.getInt("is_approved") == 0) {
                            String response = "Account pending approval.";
                            exchange.sendResponseHeaders(401, response.length());
                            exchange.getResponseBody().write(response.getBytes());
                        } else {
                            String response = "Login successful|" + rs.getString("role");
                            exchange.sendResponseHeaders(200, response.length());
                            exchange.getResponseBody().write(response.getBytes());
                        }
                    } else {
                        String response = "Invalid credentials";
                        exchange.sendResponseHeaders(401, response.length());
                        exchange.getResponseBody().write(response.getBytes());
                    }
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { 
                e.printStackTrace(); 
                exchange.sendResponseHeaders(500, -1); 
            }
        }
    }

    static class UpdateProfileHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "UPDATE users SET skills = ?, projects = ? WHERE email = ?";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, data.get("skills")); pstmt.setString(2, data.get("projects")); pstmt.setString(3, data.get("email"));
                    pstmt.executeUpdate();
                    String response = "Profile updated successfully.";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class SendRequestHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "INSERT INTO requests (sender_email, receiver_name, request_type, context) VALUES (?, ?, ?, ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, data.get("sender_email")); pstmt.setString(2, data.get("receiver_name"));
                    pstmt.setString(3, data.get("request_type")); pstmt.setString(4, data.get("context"));
                    pstmt.executeUpdate();
                    String response = "Request saved!";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class GetRequestsHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "SELECT id, sender_email, context, created_at FROM requests WHERE status = 'pending'";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    ResultSet rs = pstmt.executeQuery();
                    StringBuilder json = new StringBuilder("[");
                    while (rs.next()) {
                        if (json.length() > 1) json.append(",");
                        json.append(String.format("{\"id\":%d, \"sender\":\"%s\", \"context\":\"%s\", \"time\":\"%s\"}",
                                rs.getInt("id"), rs.getString("sender_email"), rs.getString("context"), rs.getString("created_at")));
                    }
                    json.append("]");
                    String response = json.toString();
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class UpdateStatusHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "UPDATE requests SET status = ? WHERE id = ?";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, data.get("status")); pstmt.setInt(2, Integer.parseInt(data.get("id")));
                    pstmt.executeUpdate();
                    String response = "Status updated.";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class PostOpportunityHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "INSERT INTO opportunities (poster_email, type, title, role_needed, description) VALUES (?, ?, ?, ?, ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, data.get("poster_email"));
                    pstmt.setString(2, data.get("type"));
                    pstmt.setString(3, data.get("title"));
                    pstmt.setString(4, data.get("role_needed"));
                    pstmt.setString(5, data.get("description"));
                    pstmt.executeUpdate();
                    String response = "Opportunity posted!";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class GetOpportunitiesHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "SELECT * FROM opportunities ORDER BY created_at DESC";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    ResultSet rs = pstmt.executeQuery();
                    StringBuilder json = new StringBuilder("[");
                    while (rs.next()) {
                        if (json.length() > 1) json.append(",");
                        json.append(String.format("{\"id\":%d, \"type\":\"%s\", \"title\":\"%s\", \"role\":\"%s\", \"desc\":\"%s\", \"poster\":\"%s\"}",
                                rs.getInt("id"), rs.getString("type"), rs.getString("title").replace("\"", "\\\""), 
                                rs.getString("role_needed").replace("\"", "\\\""), rs.getString("description").replace("\"", "\\\""), rs.getString("poster_email")));
                    }
                    json.append("]");
                    String response = json.toString();
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class GetProfileHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            String email = data.get("email");
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "SELECT name, roll_number, department, skills, projects, organization FROM users WHERE email = ?";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, email);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        String name = rs.getString("name") != null ? rs.getString("name") : "";
                        String roll = rs.getString("roll_number") != null ? rs.getString("roll_number") : "";
                        String dept = rs.getString("department") != null ? rs.getString("department") : "";
                        String skills = rs.getString("skills") != null ? rs.getString("skills") : "";
                        String projects = rs.getString("projects") != null ? rs.getString("projects") : "";
                        String org = rs.getString("organization") != null ? rs.getString("organization") : "";
                        
                        String json = String.format("{\"name\":\"%s\", \"roll\":\"%s\", \"dept\":\"%s\", \"skills\":\"%s\", \"projects\":\"%s\", \"org\":\"%s\"}",
                                name, roll, dept, skills.replace("\"", "\\\""), projects.replace("\"", "\\\""), org.replace("\"", "\\\""));
                        exchange.sendResponseHeaders(200, json.length());
                        exchange.getResponseBody().write(json.getBytes());
                    } else { exchange.sendResponseHeaders(404, -1); }
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class SearchTeachersHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            
            String query = "%" + data.getOrDefault("query", "").toLowerCase() + "%";
            String searcherRole = data.getOrDefault("role", "student"); 
            String targetRole = searcherRole.equals("teacher") ? "student" : "teacher";
            
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "SELECT name, department, skills, projects FROM users WHERE role = ? AND (LOWER(name) LIKE ? OR LOWER(department) LIKE ? OR LOWER(skills) LIKE ? OR LOWER(projects) LIKE ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, targetRole);
                    pstmt.setString(2, query);
                    pstmt.setString(3, query);
                    pstmt.setString(4, query);
                    pstmt.setString(5, query);
                    
                    ResultSet rs = pstmt.executeQuery();
                    StringBuilder json = new StringBuilder("[");
                    while (rs.next()) {
                        if (json.length() > 1) json.append(",");
                        String focus = rs.getString("projects") != null ? rs.getString("projects") : "None listed";
                        String skills = rs.getString("skills") != null ? rs.getString("skills") : "None listed";
                        json.append(String.format("{\"name\":\"%s\", \"dept\":\"%s\", \"skills\":\"%s\", \"interest\":\"%s\"}",
                                rs.getString("name"), rs.getString("department"), 
                                skills.replace("\"", "\\\"").replace("\n", " "), 
                                focus.replace("\"", "\\\"").replace("\n", " ")));
                    }
                    json.append("]");
                    
                    String response = json.toString();
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class SearchEventsHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            String query = "%" + data.getOrDefault("query", "").toLowerCase() + "%";
            
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "SELECT title, poster_email, role_needed, description FROM opportunities WHERE type = 'Non-Academic' AND (LOWER(title) LIKE ? OR LOWER(role_needed) LIKE ? OR LOWER(description) LIKE ?)";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, query);
                    pstmt.setString(2, query);
                    pstmt.setString(3, query);
                    
                    ResultSet rs = pstmt.executeQuery();
                    StringBuilder json = new StringBuilder("[");
                    while (rs.next()) {
                        if (json.length() > 1) json.append(",");
                        json.append(String.format("{\"name\":\"%s\", \"club\":\"%s\", \"role\":\"%s\", \"desc\":\"%s\"}",
                                rs.getString("title").replace("\"", "\\\""), 
                                rs.getString("poster_email"), 
                                rs.getString("role_needed").replace("\"", "\\\""), 
                                rs.getString("description").replace("\"", "\\\"")));
                    }
                    json.append("]");
                    
                    String response = json.toString();
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class GetPendingUsersHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    // Added 'organization' to the SELECT query
                    String sql = "SELECT name, role, email, department, organization FROM users WHERE is_approved = 0";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    ResultSet rs = pstmt.executeQuery();
                    StringBuilder json = new StringBuilder("[");
                    while (rs.next()) {
                        if (json.length() > 1) json.append(",");
                        String org = rs.getString("organization") != null ? rs.getString("organization") : "";
                        // Added 'org' to the JSON string
                        json.append(String.format("{\"name\":\"%s\", \"role\":\"%s\", \"email\":\"%s\", \"dept\":\"%s\", \"org\":\"%s\"}",
                                rs.getString("name"), rs.getString("role"), rs.getString("email"), rs.getString("department"), org.replace("\"", "\\\"")));
                    }
                    json.append("]");
                    String response = json.toString();
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class ApproveUserHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "UPDATE users SET is_approved = 1 WHERE email = ?";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, data.get("email"));
                    pstmt.executeUpdate();
                    String response = "User approved.";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }

    static class ChangePasswordHandler implements HttpHandler {
        @Override public void handle(HttpExchange exchange) throws IOException {
            setCorsHeaders(exchange);
            if ("OPTIONS".equals(exchange.getRequestMethod())) { exchange.sendResponseHeaders(204, -1); return; }
            Map<String, String> data = parseFormData(exchange.getRequestBody());

            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection conn = DriverManager.getConnection(DB_URL)) {
                    String sql = "UPDATE users SET password = ? WHERE email = ?";
                    PreparedStatement pstmt = conn.prepareStatement(sql);
                    pstmt.setString(1, hashPassword(data.get("new_password")));
                    pstmt.setString(2, data.get("email"));
                    pstmt.executeUpdate();

                    String response = "Password updated successfully.";
                    exchange.sendResponseHeaders(200, response.length());
                    exchange.getResponseBody().write(response.getBytes());
                    exchange.getResponseBody().close();
                }
            } catch (Exception e) { exchange.sendResponseHeaders(500, -1); }
        }
    }
}