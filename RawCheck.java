import java.sql.*;
public class RawCheck {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:mysql://12.0.221.123:3311/ccbscf_edt_d1?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8";
    try (Connection c = DriverManager.getConnection(url, "ccbscf_edt_dev", "EDTd1Pass!")) {
      Statement st = c.createStatement();
      ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM field_usage_scenario");
      rs.next(); System.out.println("field_usage_scenario total rows: " + rs.getInt(1));
      rs = st.executeQuery("SELECT COUNT(*) FROM field_usage_scenario WHERE status=1"); rs.next();
      System.out.println("  status=1: " + rs.getInt(1));
      rs = st.executeQuery("SELECT table_id, COUNT(*) FROM field_usage_scenario GROUP BY table_id");
      System.out.println("=== by table_id ===");
      while(rs.next()) System.out.println("  table_id=" + rs.getInt(1) + " cnt=" + rs.getInt(2));
    }
  }
}
