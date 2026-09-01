import java.sql.*;
public class FinalCheck {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:mysql://12.0.221.123:3311/ccbscf_edt_d1?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8";
    try (Connection c = DriverManager.getConnection(url, "ccbscf_edt_dev", "EDTd1Pass!")) {
      Statement st = c.createStatement();
      System.out.println("=== 5 tables (edt project): status / fields / scenarios ===");
      ResultSet rs = st.executeQuery(
        "SELECT ti.table_name, ti.status, " +
        " (SELECT COUNT(*) FROM table_field tf WHERE tf.table_id=ti.id) fcnt," +
        " (SELECT COUNT(*) FROM field_usage_scenario fus WHERE fus.table_id=ti.id) scnt," +
        " LEFT(IFNULL(ti.table_comment,''),24) cmt " +
        "FROM table_info ti WHERE ti.project_id=(SELECT id FROM project WHERE app_name='edt') " +
        "ORDER BY FIELD(ti.table_name,'t_collect_info','t_edt_auth_cert','t_edt_authorize_apply','t_limit_apply','t_supplier_limit')");
      while(rs.next()) System.out.println("  " + rs.getString(1) + " status=" + rs.getInt(2) + " fields=" + rs.getInt(3) + " scen=" + rs.getInt(4) + " cmt='" + rs.getString(5) + "'");

      System.out.println("\n=== status=1 totals (edt project) ===");
      for (String t : new String[]{"table_field","field_usage_scenario","table_relation"}) {
        rs = st.executeQuery(String.format(
          "SELECT '%s', status, COUNT(*) FROM %s WHERE " +
          (t.equals("table_relation") ? "project_id=(SELECT id FROM project WHERE app_name='edt')"
            : "table_id IN (SELECT id FROM table_info WHERE project_id=(SELECT id FROM project WHERE app_name='edt'))") +
          " GROUP BY status", t, t));
        while(rs.next()) System.out.println("  " + rs.getString(1) + " status=" + rs.getInt(2) + " cnt=" + rs.getInt(3));
      }

      System.out.println("\n=== EdtTencent line 389 (trade_product_mode, production not test) ===");
      rs = st.executeQuery(
        "SELECT method_name, operation_type FROM field_usage_scenario " +
        "WHERE field_id IN (SELECT id FROM table_field WHERE field_name='trade_product_mode' AND table_id IN " +
        "(SELECT id FROM table_info WHERE project_id=(SELECT id FROM project WHERE app_name='edt') AND table_name='t_collect_info')) " +
        "AND method_name LIKE '%EdtTencent%' AND method_name NOT LIKE '%Test%'");
      while(rs.next()) System.out.println("  " + rs.getString(1) + " [" + rs.getString(2) + "]");

      System.out.println("\n=== Test-class scenarios remaining (should be 0) ===");
      rs = st.executeQuery(
        "SELECT COUNT(*) FROM field_usage_scenario WHERE method_name LIKE '%Test%' AND table_id IN " +
        "(SELECT id FROM table_info WHERE project_id=(SELECT id FROM project WHERE app_name='edt'))");
      rs.next(); System.out.println("  count=" + rs.getInt(1));
    }
  }
}
