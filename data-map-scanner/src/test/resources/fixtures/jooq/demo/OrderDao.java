package demo;

import com.example.jooq.tables.T_ORDER;

public class OrderDao {
    private final DSL dsl = new DSL();

    public void update(T_ORDER table) {
        dsl.update(table).set(table.ORDER_STATUS, "X");
    }

    public void read(T_ORDER table) {
        dsl.selectFrom(table).where(table.ORDER_STATUS.eq("X"));
    }

    public void write(T_ORDER table) {
        dsl.insertInto(table, table.ORDER_STATUS).values("X");
    }

    public void delete(T_ORDER table) {
        dsl.deleteFrom(table);
    }
}
