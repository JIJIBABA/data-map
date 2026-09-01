import java.sql.*;
public class VerifyScan {
  public static void main(String[] a) throws Exception {
    String url = "jdbc:mysql://12.0.221.123:3311/ccbscf_edt_d1?characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=GMT%2B8";
    try (Connection c = DriverManager.getConnection(url, "ccbscf_edt_dev", "EDTd1Pass!")) {
      Statement st = c.createStatement();
      System.out.println("=== 5 tables: name / status / field_cnt / scen_cnt ===");
      ResultSet rs = st.executeQuery(
        "SELECT ti.table_name, ti.status, " +
        " (SELECT COUNT(*) FROM table_field tf WHERE tf.table_id=ti.id) AS fcnt," +
        " (SELECT COUNT(*) FROM field_usage_scenario fus WHERE fus.table_id=ti.id) AS scnt " +
        "FROM table_info ti WHERE ti.project_id=(SELECT id FROM project WHERE app_name='edt') " +
        "AND ti.table_name IN ('t_edt_auth_cert','t_collect_info','t_edt_authorize_apply','t_limit_apply','t_supplier_limit') " +
        "ORDER BY FIELD(ti.table_name,'t_collect_info','t_edt_auth_cert','t_edt_authorize_apply','t_limit_apply','t_supplier_limit')");
      printRS(rs);
      System.out.println("\n=== status=1 totals (field / scenario / relation) ===");
      for (String t : new String[]{"table_field","field_usage_scenario","table_relation"}) {
        rs = st.executeQuery(String.format(
          "SELECT '%s', status, COUNT(*) FROM %s WHERE " +
          (t.equals("table_relation") ? "project_id=(SELECT id FROM project WHERE app_name='edt')"
            : "table_id IN (SELECT id FROM table_info WHERE project_id=(SELECT id FROM project WHERE app_name='edt'))") +
          " GROUP BY status", t, t));
        printRS(rs);
      }
      System.out.println("\n=== EdtTencent line 389 (t_collect_info.trade_product_mode) ===");
      rs = st.executeQuery(
        "SELECT method_name, operation_type FROM field_usage_scenario " +
        "WHERE field_id IN (SELECT id FROM table_field WHERE field_name='trade_product_mode' AND table_id IN " +
        "(SELECT id FROM table_info WHERE project_id=(SELECT id FROM project WHERE app_name='edt') AND table_name='t_collect_info')) " +
        "AND method_name LIKE '%EdtTencent%' AND method_name NOT LIKE '%Test%'");
      printRS(rs);
    }
  }
  static void printRS(ResultSet rs) throws SQLException {
    ResultSetMetaData m = rs.getMetaData();
    StringBuilder h=new StringBuilder(); for(int i=1;i<=m.getColumnCount();i++) h.append(m.getColumnLabel(i)).append("\t");
    System.out.println(h);
    while(rs.next()){StringBuilder r=new StringBuilder(); for(int i=1;i<=m.getColumnCount();i++) r.append(rs.getString(i)).append("\t"); System.out.println(r);}
  }
}
