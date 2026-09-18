package com.datamap.scanner.jooq;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字段使用场景收集：识别 table.FIELD 引用（table 解析为 JOOQ 表实例，FIELD 为该表的 TableField），
 * 按 TreePath 向上语境分类为 UPDATE / WRITE / READ / DELETE；同时识别 JOOQ POJO/Record 上的
 * setXxx/getXxx（pojo.setXxx / record.getXxx）访问，并把 setter 依据来源追踪（origin trace）
 * 分类为 WRITE / UPDATE / UNRESOLVED，最终与 table.FIELD 路径合并到同一个 table.field 键。
 */
public class JooqFieldAccessCollector {

    /** table.FIELD.xxx(...) 中 xxx 为字段条件方法 => READ。 */
    private static final Set<String> FIELD_READ_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "eq", "ne", "lt", "le", "gt", "ge", "in", "like", "notLike", "isNull", "isNotNull")));

    /** table.FIELD 作为这些方法的实参 => READ。 */
    private static final Set<String> READ_CONTEXT_METHODS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "where", "and", "or", "orderBy", "groupBy", "having")));

    private static final String UNRESOLVED = "UNRESOLVED";
    private static final int MAX_TRACE_DEPTH = 3;

    private final AnalysisContext ctx;
    private final Map<String, TypeElement> tables;
    /** 表类 TypeElement -> 表名，O(1) 反查（替代按表数量线性扫描）。 */
    private final Map<TypeElement, String> invertedTables;
    /** 元素 -> TreePath 缓存，避免反复 ctx.trees.getPath(element)。 */
    private final Map<Element, TreePath> pathCache;
    /** 调用点索引：callee -> 调用点列表（一次性预计算）。 */
    private final Map<ExecutableElement, List<CallSite>> callSites;

    private JooqFieldAccessCollector(AnalysisContext ctx, Map<String, TypeElement> tables) {
        this.ctx = ctx;
        this.tables = tables;
        this.invertedTables = new HashMap<>();
        for (Map.Entry<String, TypeElement> e : tables.entrySet()) {
            this.invertedTables.put(e.getValue(), e.getKey());
        }
        this.pathCache = new HashMap<>();
        this.callSites = buildCallSites();
    }

    public static Map<String, List<JooqFieldAccess>> collect(AnalysisContext ctx, Map<String, TypeElement> tables) {
        return new JooqFieldAccessCollector(ctx, tables).run();
    }

    /** 单个调用点：caller 方法、实参列表、所在调用点路径（用于跨编译单元来源追踪）。 */
    private static final class CallSite {
        final ExecutableElement caller;
        final List<? extends ExpressionTree> args;
        final TreePath path;

        CallSite(ExecutableElement caller, List<? extends ExpressionTree> args, TreePath path) {
            this.caller = caller;
            this.args = args;
            this.path = path;
        }
    }

    private Map<String, List<JooqFieldAccess>> run() {
        Map<String, List<JooqFieldAccess>> result = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMemberSelect(MemberSelectTree mst, Void p) {
                    if (possibleTableRef(mst.getExpression())) {
                        String table = resolveTable(new TreePath(getCurrentPath(), mst.getExpression()));
                        if (table != null) {
                            String fieldName = mst.getIdentifier().toString();
                            String op = classify(getCurrentPath(), mst);
                            ExecutableElement method = enclosingMethod(getCurrentPath());
                            if (method != null) {
                                result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>())
                                      .add(new JooqFieldAccess(fieldName, op, method));
                            }
                        }
                    }
                    return super.visitMemberSelect(mst, p);
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    String name = methodName(node);
                    if (("deleteFrom".equals(name) || "delete".equals(name)) && !node.getArguments().isEmpty()) {
                        String table = resolveTable(new TreePath(getCurrentPath(), node.getArguments().get(0)));
                        if (table != null) {
                            ExecutableElement method = enclosingMethod(getCurrentPath());
                            if (method != null) {
                                result.computeIfAbsent(table + ".", k -> new ArrayList<>())
                                      .add(new JooqFieldAccess("", "DELETE", method));
                            }
                        }
                    }
                    collectPojoAccess(node, getCurrentPath(), result);
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return result;
    }

    /**
     * 一次性预计算调用点索引：遍历所有编译单元，解析每个 MethodInvocationTree 的
     * callee 与所在方法（caller），按 callee 归组。使用 TreePathScanner 自带的
     * getCurrentPath() 直接取元素，避免 ctx.resolve 对整棵编译单元的重复扫描。
     */
    private Map<ExecutableElement, List<CallSite>> buildCallSites() {
        Map<ExecutableElement, List<CallSite>> map = new HashMap<>();
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    Element e = ctx.trees.getElement(getCurrentPath());
                    if (e instanceof ExecutableElement) {
                        ExecutableElement caller = enclosingMethod(getCurrentPath());
                        if (caller != null) {
                            map.computeIfAbsent((ExecutableElement) e, k -> new ArrayList<>())
                               .add(new CallSite(caller, node.getArguments(), getCurrentPath()));
                        }
                    }
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return map;
    }

    /**
     * 识别 recv.setXxx(v) / recv.getXxx()（POJO/Record 字段访问），
     * 并合并（去重）到 table.field 键下。
     */
    private void collectPojoAccess(MethodInvocationTree node,
                                   TreePath currentPath, Map<String, List<JooqFieldAccess>> result) {
        if (!(node.getMethodSelect() instanceof MemberSelectTree)) return;
        MemberSelectTree methodSelect = (MemberSelectTree) node.getMethodSelect();
        String name = methodSelect.getIdentifier().toString();

        boolean isSet = name.startsWith("set") && name.length() > 3 && node.getArguments().size() == 1;
        boolean isGet = name.startsWith("get") && name.length() > 3 && node.getArguments().isEmpty();
        if (!isSet && !isGet) return;

        ExpressionTree recv = methodSelect.getExpression();
        TreePath recvPath = new TreePath(currentPath, recv);
        String table = tableForReceiver(recvPath);
        if (table == null) return;

        String fieldName = camelToSnake(name.substring(3)).toUpperCase();
        ExecutableElement method = enclosingMethod(currentPath);
        if (method == null) return;

        String op = isGet ? "READ" : classifySetter(recvPath);
        addAccess(result, table, fieldName, op, method);
    }

    /** 去重添加：同一 (field, operationType, method) 已存在（如 table.FIELD 路径）则不重复添加。 */
    private void addAccess(Map<String, List<JooqFieldAccess>> result, String table,
                           String fieldName, String op, ExecutableElement method) {
        List<JooqFieldAccess> list = result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>());
        for (JooqFieldAccess a : list) {
            if (a.fieldName.equals(fieldName) && a.operationType.equals(op) && a.method.equals(method)) {
                return;
            }
        }
        list.add(new JooqFieldAccess(fieldName, op, method));
    }

    /**
     * 由接收者表达式声明的类型简单名推导表名：去掉尾部 Record，camelToSnake 后转大写；
     * 命中 tables 中的键即认定为 JOOQ POJO/Record。
     */
    private String tableForReceiver(TreePath recvPath) {
        String simpleName = receiverTypeSimpleName(recvPath);
        if (simpleName == null || simpleName.isEmpty()) return null;
        if (simpleName.endsWith("Record")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Record".length());
        }
        String table = camelToSnake(simpleName).toUpperCase();
        return tables.containsKey(table) ? table : null;
    }

    /** 解析表达式类型简单名（优先类型镜像，退化到变量类型）。 */
    private String receiverTypeSimpleName(TreePath recvPath) {
        try {
            TypeMirror tm = ctx.trees.getTypeMirror(recvPath);
            if (tm != null && tm.getKind() != TypeKind.ERROR) {
                Element el = ctx.types.asElement(tm);
                if (el instanceof TypeElement) return ((TypeElement) el).getSimpleName().toString();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        Element e = ctx.trees.getElement(recvPath);
        if (e instanceof VariableElement) {
            Element typeEl = ctx.types.asElement(((VariableElement) e).asType());
            if (typeEl instanceof TypeElement) return ((TypeElement) typeEl).getSimpleName().toString();
        }
        return null;
    }

    /** setter 分类：沿来源（origin）追踪接收者，见 traceOrigin。 */
    private String classifySetter(TreePath recvPath) {
        return traceOrigin(recvPath, 0);
    }

    /**
     * 来源追踪（有界、确定性）：
     * <ul>
     *   <li>NewClassTree => WRITE</li>
     *   <li>MethodInvocationTree（查询等）=> UPDATE</li>
     *   <li>参数 => 沿调用图找调用点，递归追踪同下标实参</li>
     *   <li>局部变量 => 追踪其声明初始化表达式</li>
     *   <li>字段 => UNRESOLVED</li>
     * </ul>
     * 多个分支合并：任一 WRITE 则 WRITE；否则任一 UPDATE 则 UPDATE；否则 UNRESOLVED。
     */
    private String traceOrigin(TreePath exprPath, int depth) {
        Tree expr = exprPath.getLeaf();
        if (expr instanceof NewClassTree) return "WRITE";
        if (expr instanceof MethodInvocationTree) return "UPDATE";
        if (depth >= MAX_TRACE_DEPTH) return UNRESOLVED;
        if (expr instanceof IdentifierTree) {
            Element e = ctx.trees.getElement(exprPath);
            if (e instanceof VariableElement) {
                VariableElement var = (VariableElement) e;
                if (var.getKind() == ElementKind.PARAMETER) {
                    Element enclosing = var.getEnclosingElement();
                    if (!(enclosing instanceof ExecutableElement)) return UNRESOLVED;
                    ExecutableElement method = (ExecutableElement) enclosing;
                    int idx = paramIndex(method, var);
                    if (idx < 0) return UNRESOLVED;
                    return traceCallers(method, idx, depth + 1);
                }
                if (var.getKind() == ElementKind.LOCAL_VARIABLE) {
                    TreePath declPath = findLocalDeclaration(var);
                    if (declPath != null) {
                        VariableTree decl = (VariableTree) declPath.getLeaf();
                        if (decl.getInitializer() != null) {
                            return traceOrigin(new TreePath(declPath, decl.getInitializer()), depth + 1);
                        }
                    }
                    return UNRESOLVED;
                }
                return UNRESOLVED; // FIELD 成员
            }
        }
        return UNRESOLVED;
    }

    private String traceCallers(ExecutableElement callee, int paramIndex, int depth) {
        if (depth >= MAX_TRACE_DEPTH) return UNRESOLVED;
        boolean anyUpdate = false;
        for (CallSite cs : callSites.getOrDefault(callee, Collections.emptyList())) {
            if (cs.args.size() <= paramIndex) continue;
            ExpressionTree arg = cs.args.get(paramIndex);
            String r = traceOrigin(new TreePath(cs.path, arg), depth + 1);
            if ("WRITE".equals(r)) return "WRITE";
            if ("UPDATE".equals(r)) anyUpdate = true;
        }
        return anyUpdate ? "UPDATE" : UNRESOLVED;
    }

    /** 元素 -> TreePath（含 null 缓存），避免反复调用 ctx.trees.getPath(element)。 */
    private TreePath cachedPath(Element e) {
        if (e == null) return null;
        if (pathCache.containsKey(e)) return pathCache.get(e);
        TreePath p;
        try {
            p = ctx.trees.getPath(e);
        } catch (RuntimeException ignored) {
            p = null;
        }
        pathCache.put(e, p);
        return p;
    }

    /** 定位局部变量的 VariableTree 路径（优先 getPath 缓存，退化到扫描所在方法体）。 */
    private TreePath findLocalDeclaration(VariableElement var) {
        TreePath p = cachedPath(var);
        if (p != null && p.getLeaf() instanceof VariableTree) return p;
        Element enclosing = var.getEnclosingElement();
        if (!(enclosing instanceof ExecutableElement)) return null;
        TreePath mp = cachedPath((ExecutableElement) enclosing);
        if (mp == null) return null;
        Tree root = mp.getLeaf();
        if (!(root instanceof MethodTree)) return null;
        final TreePath[] found = {null};
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree node, Void p) {
                if (found[0] == null) {
                    Element e = ctx.trees.getElement(getCurrentPath());
                    if (e != null && e.equals(var)) found[0] = getCurrentPath();
                }
                return super.visitVariable(node, p);
            }
        }.scan(mp, null);
        return found[0];
    }

    private int paramIndex(ExecutableElement method, VariableElement param) {
        List<? extends VariableElement> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).equals(param)) return i;
        }
        return -1;
    }

    private String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) sb.append('_');
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 廉价前置判定：表达式是否可能解析为 JOOQ 表实例变量。
     * <ul>
     *   <li>IdentifierTree（局部变量/参数/静态导入的表常量）=> 是</li>
     *   <li>MemberSelectTree 且末段标识符为表名（Tables.T_X / T_X.T_X / DbSchema.T_X 静态实例模式）=> 是</li>
     *   <li>其余（MethodInvocationTree、LiteralTree、NewClassTree 及包裹它们的 ParenthesizedTree 等）=> 否</li>
     * </ul>
     * 用于避免对不可能为表变量的表达式调用元素解析。
     */
    private boolean possibleTableRef(ExpressionTree expr) {
        ExpressionTree e = expr;
        while (e instanceof ParenthesizedTree) {
            e = ((ParenthesizedTree) e).getExpression();
        }
        if (e instanceof IdentifierTree) return true;
        if (e instanceof MemberSelectTree) {
            return tables.containsKey(((MemberSelectTree) e).getIdentifier().toString());
        }
        return false;
    }

    /** 表达式解析为 JOOQ 表实例（本地变量/字段/静态字段）时返回表名，否则 null。 */
    private String resolveTable(TreePath exprPath) {
        Element e = ctx.trees.getElement(exprPath);
        if (!(e instanceof VariableElement)) return null;
        Element typeEl = ctx.types.asElement(((VariableElement) e).asType());
        if (!(typeEl instanceof TypeElement)) return null;
        return invertedTables.get(typeEl);
    }

    /** 从 table.FIELD 的 TreePath 向上判定操作类型；无更强信号时默认 READ。 */
    private String classify(TreePath path, MemberSelectTree mst) {
        TreePath parent = path.getParentPath();
        if (parent == null) return "READ";
        Tree leaf = parent.getLeaf();

        if (leaf instanceof MemberSelectTree) {
            // table.FIELD.xxx(...) —— table.FIELD 是方法接收者
            MemberSelectTree methodSelect = (MemberSelectTree) leaf;
            String name = methodSelect.getIdentifier().toString();
            TreePath gp = parent.getParentPath();
            if (gp != null && gp.getLeaf() instanceof MethodInvocationTree
                    && ((MethodInvocationTree) gp.getLeaf()).getMethodSelect() == methodSelect
                    && FIELD_READ_METHODS.contains(name)) {
                return "READ";
            }
            return "READ";
        }

        if (leaf instanceof MethodInvocationTree) {
            // table.FIELD 直接作为方法实参
            MethodInvocationTree call = (MethodInvocationTree) leaf;
            String name = methodName(call);
            int idx = argIndex(call, mst);
            if ("set".equals(name) && idx == 0) return "UPDATE";
            if ("insertInto".equals(name) && idx >= 1) return "WRITE";
            if ("select".equals(name) && idx == 0) return "READ";
            if (READ_CONTEXT_METHODS.contains(name)) return "READ";
        }

        return "READ";
    }

    private int argIndex(MethodInvocationTree call, Tree arg) {
        List<? extends ExpressionTree> args = call.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) == arg) return i;
        }
        return -1;
    }

    private String methodName(MethodInvocationTree call) {
        ExpressionTree ms = call.getMethodSelect();
        if (ms instanceof IdentifierTree) return ((IdentifierTree) ms).getName().toString();
        if (ms instanceof MemberSelectTree) return ((MemberSelectTree) ms).getIdentifier().toString();
        return "";
    }

    /** 向上查找最近的 MethodTree 并解析为 ExecutableElement。 */
    private ExecutableElement enclosingMethod(TreePath path) {
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                Element e = ctx.trees.getElement(path);
                if (e instanceof ExecutableElement) return (ExecutableElement) e;
            }
            path = path.getParentPath();
        }
        return null;
    }
}
