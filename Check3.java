import java.sql.*;
public class Check3 {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:mysql://12.0.221.123:3311/ccbscf_edt_d1?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8";
    try (Connection c = DriverManager.getConnection(url, "ccbscf_edt_dev", "EDTd1Pass!")) {
      Statement st = c.createStatement();
      System.out.println("=== projects ===");
      ResultSet rs = st.executeQuery("SELECT id, app_name FROM project ORDER BY id");
      while(rs.next()) System.out.println("  " + rs.getInt(1) + " " + rs.getString(2));
      System.out.println("=== latest table_info + scenario counts (last 12 by id) ===");
      rs = st.executeQuery("SELECT ti.id, ti.project_id, ti.table_name, (SELECT COUNT(*) FROM field_usage_scenario fus WHERE fus.table_id=ti.id) sc FROM table_info ti ORDER BY ti.id DESC LIMIT 12");
      while(rs.next()) System.out.println("  ti.id=" + rs.getInt(1) + " proj=" + rs.getInt(2) + " " + rs.getString(3) + " scen=" + rs.getInt(4));
      System.out.println("=== field_usage_scenario: max id, count, status mix ===");
      rs = st.executeQuery("SELECT MAX(id), COUNT(*), status FROM field_usage_scenario"); rs.next();
      System.out.println("  max_id=" + rs.getInt(1) + " count=" + rs.getInt(2) + " status=" + rs.getInt(3));
    }
  }
}
