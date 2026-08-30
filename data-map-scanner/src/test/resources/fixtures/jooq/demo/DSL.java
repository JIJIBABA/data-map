package demo;

import org.jooq.Table;
import org.jooq.TableField;

/** JOOQ DSL 最小桩：方法名决定操作分类，返回类型仅用于让链式调用可解析。 */
public class DSL {
    public UpdateSetStep update(Table<?> table) { return null; }
    public SelectWhereStep selectFrom(Table<?> table) { return null; }
    public InsertValuesStep insertInto(Table<?> table, TableField<?, ?>... fields) { return null; }
    public void deleteFrom(Table<?> table) { }

    public static class UpdateSetStep {
        public void set(TableField<?, ?> field, Object value) { }
    }

    public static class SelectWhereStep {
        public void where(Object condition) { }
    }

    public static class InsertValuesStep {
        public void values(Object... values) { }
    }
}
