import java.sql.*;
public class Check2 {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:mysql://12.0.221.123:3311/ccbscf_edt_d1?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8";
    try (Connection c = DriverManager.getConnection(url, "ccbscf_edt_dev", "EDTd1Pass!")) {
      Statement st = c.createStatement();
      System.out.println("=== table_info rows for edt project (id, name) ===");
      ResultSet rs = st.executeQuery("SELECT id, table_name FROM table_info WHERE project_id=(SELECT id FROM project WHERE app_name='edt') ORDER BY id");
      while(rs.next()) System.out.println("  id=" + rs.getInt(1) + " " + rs.getString(2));
      System.out.println("\n=== scenarios joined to table_info for these ids ===");
      rs = st.executeQuery("SELECT ti.table_name, COUNT(fus.id) FROM table_info ti LEFT JOIN field_usage_scenario fus ON fus.table_id=ti.id WHERE ti.project_id=(SELECT id FROM project WHERE app_name='edt') GROUP BY ti.id ORDER BY ti.table_name");
      while(rs.next()) System.out.println("  " + rs.getString(1) + " " + rs.getInt(2));
    }
  }
}
