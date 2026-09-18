import java.sql.*;

public class DbTool {
    public static void main(String[] args) throws Exception {
        String url = System.getProperty("db.url");
        String user = System.getProperty("db.user");
        String pass = System.getProperty("db.pass");
        String action = args.length > 0 ? args[0] : "count";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            if ("count".equals(action)) {
                String[] tables = {"project","table_info","table_field","table_relation","field_usage_scenario","scan_record"};
                for (String t : tables) {
                    try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                        rs.next();
                        System.out.println(t + " : " + rs.getLong(1));
                    }
                }
            } else if ("clear".equals(action)) {
                try (Statement st = conn.createStatement()) {
                    st.executeUpdate("DELETE FROM field_usage_scenario");
                    st.executeUpdate("DELETE FROM table_relation");
                    st.executeUpdate("DELETE FROM table_field");
                    st.executeUpdate("DELETE FROM table_info");
                    st.executeUpdate("DELETE FROM scan_record");
                    st.executeUpdate("DELETE FROM project");
                    System.out.println("cleared 6 tables");
                }
            }
        }
    }
}
