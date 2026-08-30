package demo;

import com.example.jooq.tables.pojos.TOrder;

/** 通过 POJO 写/读字段，覆盖跨方法（param）来源追踪。 */
public class OrderService {
    private final OrderDao dao = new OrderDao();

    public void create(TOrder pojo) {
        TOrder p = new TOrder();
        fill(p);
        dao.insert(p);
    }

    public void fill(TOrder p) {
        p.setChassisNum("VIN123");
    }

    public String read(TOrder pojo) {
        return pojo.getChassisNum();
    }
}
