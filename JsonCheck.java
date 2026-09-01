import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.util.*;
public class JsonCheck {
  public static void main(String[] a) throws Exception {
    Map d = new ObjectMapper().readValue(new File("C:/Users/jxrt/AppData/Local/Temp/edt-latest.json"), Map.class);
    List tables = (List) d.get("tables");
    System.out.println("tables in JSON: " + tables.size());
    int totalScen = 0;
    for (Object t : tables) {
      Map tm = (Map) t;
      List sc = (List) tm.get("usageScenarios");
      System.out.println(tm.get("tableName") + ": fields=" + ((List)tm.get("fields")).size() + " scenarios=" + sc.size());
      totalScen += sc.size();
    }
    System.out.println("total scenarios in JSON: " + totalScen);
  }
}
