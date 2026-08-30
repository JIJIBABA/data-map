package com.datamap.scanner.jooq;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static final Set<String> FIELD_READ_METHODS = Set.of(
            "eq", "ne", "lt", "le", "gt", "ge", "in", "like", "notLike", "isNull", "isNotNull");

    /** table.FIELD 作为这些方法的实参 => READ。 */
    private static final Set<String> READ_CONTEXT_METHODS = Set.of(
            "where", "and", "or", "orderBy", "groupBy", "having");

    private static final String UNRESOLVED = "UNRESOLVED";
    private static final int MAX_TRACE_DEPTH = 3;

    public static Map<String, List<JooqFieldAccess>> collect(AnalysisContext ctx, Map<String, TypeElement> tables) {
        Map<String, List<JooqFieldAccess>> result = new HashMap<>();
        CallGraph callGraph = CallGraphBuilder.build(ctx);
        for (CompilationUnitTree cu : ctx.units) {
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitMemberSelect(MemberSelectTree mst, Void p) {
                    String table = resolveTable(ctx, cu, mst.getExpression(), tables);
                    if (table != null) {
                        String fieldName = mst.getIdentifier().toString();
                        String op = classify(getCurrentPath(), mst);
                        ExecutableElement method = enclosingMethod(ctx, cu, getCurrentPath());
                        if (method != null) {
                            result.computeIfAbsent(table + "." + fieldName, k -> new ArrayList<>())
                                  .add(new JooqFieldAccess(fieldName, op, method));
                        }
                    }
                    return super.visitMemberSelect(mst, p);
                }

                @Override
                public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                    String name = methodName(node);
                    if (("deleteFrom".equals(name) || "delete".equals(name)) && !node.getArguments().isEmpty()) {
                        String table = resolveTable(ctx, cu, node.getArguments().get(0), tables);
                        if (table != null) {
                            ExecutableElement method = enclosingMethod(ctx, cu, getCurrentPath());
                            if (method != null) {
                                result.computeIfAbsent(table + ".", k -> new ArrayList<>())
                                      .add(new JooqFieldAccess("", "DELETE", method));
                            }
                        }
                    }
                    collectPojoAccess(ctx, cu, node, getCurrentPath(), tables, callGraph, result);
                    return super.visitMethodInvocation(node, p);
                }
            }.scan(cu, null);
        }
        return result;
    }

    /**
     * 识别 recv.setXxx(v) / recv.getXxx()（POJO/Record 字段访问），
     * 并合并（去重）到 table.field 键下。
     */
    private static void collectPojoAccess(AnalysisContext ctx, CompilationUnitTree cu,
                                          MethodInvocationTree node, TreePath currentPath,
                                          Map<String, TypeElement> tables,
                                          CallGraph callGraph, Map<String, List<JooqFieldAccess>> result) {
        if (!(node.getMethodSelect() instanceof MemberSelectTree)) return;
        MemberSelectTree methodSelect = (MemberSelectTree) node.getMethodSelect();
        String name = methodSelect.getIdentifier().toString();

        boolean isSet = name.startsWith("set") && name.length() > 3 && node.getArguments().size() == 1;
        boolean isGet = name.startsWith("get") && name.length() > 3 && node.getArguments().isEmpty();
        if (!isSet && !isGet) return;

        ExpressionTree recv = methodSelect.getExpression();
        String table = tableForReceiver(ctx, cu, recv, tables);
        if (table == null) return;

        String fieldName = camelToSnake(name.substring(3)).toUpperCase();
        ExecutableElement method = enclosingMethod(ctx, cu, currentPath);
        if (method == null) return;

        String op = isGet ? "READ" : classifySetter(ctx, cu, recv, callGraph);
        addAccess(result, table, fieldName, op, method);
    }

    /** 去重添加：同一 (field, operationType, method) 已存在（如 table.FIELD 路径）则不重复添加。 */
    private static void addAccess(Map<String, List<JooqFieldAccess>> result, String table,
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
    private static String tableForReceiver(AnalysisContext ctx, CompilationUnitTree cu,
                                           ExpressionTree recv, Map<String, TypeElement> tables) {
        String simpleName = receiverTypeSimpleName(ctx, cu, recv);
        if (simpleName == null || simpleName.isEmpty()) return null;
        if (simpleName.endsWith("Record")) {
            simpleName = simpleName.substring(0, simpleName.length() - "Record".length());
        }
        String table = camelToSnake(simpleName).toUpperCase();
        return tables.containsKey(table) ? table : null;
    }

    /** 解析表达式类型简单名（优先类型镜像，退化到变量类型）。 */
    private static String receiverTypeSimpleName(AnalysisContext ctx, CompilationUnitTree cu, ExpressionTree recv) {
        try {
            TypeMirror tm = ctx.trees.getTypeMirror(TreePath.getPath(cu, recv));
            if (tm != null && tm.getKind() != TypeKind.ERROR) {
                Element el = ctx.types.asElement(tm);
                if (el instanceof TypeElement) return ((TypeElement) el).getSimpleName().toString();
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        Element e = ctx.resolve(cu, recv);
        if (e instanceof VariableElement) {
            Element typeEl = ctx.types.asElement(((VariableElement) e).asType());
            if (typeEl instanceof TypeElement) return ((TypeElement) typeEl).getSimpleName().toString();
        }
        return null;
    }

    /** setter 分类：沿来源（origin）追踪接收者，见 traceOrigin。 */
    private static String classifySetter(AnalysisContext ctx, CompilationUnitTree cu,
                                         ExpressionTree recv, CallGraph callGraph) {
        return traceOrigin(ctx, cu, recv, callGraph, 0);
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
    private static String traceOrigin(AnalysisContext ctx, CompilationUnitTree cu, ExpressionTree expr,
                                      CallGraph callGraph, int depth) {
        if (expr instanceof NewClassTree) return "WRITE";
        if (expr instanceof MethodInvocationTree) return "UPDATE";
        if (depth >= MAX_TRACE_DEPTH) return UNRESOLVED;
        if (expr instanceof IdentifierTree) {
            Element e = ctx.resolve(cu, expr);
            if (e instanceof VariableElement) {
                VariableElement var = (VariableElement) e;
                if (var.getKind() == ElementKind.PARAMETER) {
                    Element enclosing = var.getEnclosingElement();
                    if (!(enclosing instanceof ExecutableElement)) return UNRESOLVED;
                    ExecutableElement method = (ExecutableElement) enclosing;
                    int idx = paramIndex(method, var);
                    if (idx < 0) return UNRESOLVED;
                    return traceCallers(ctx, cu, method, idx, callGraph, depth + 1);
                }
                if (var.getKind() == ElementKind.LOCAL_VARIABLE) {
                    VariableTree decl = findLocalDeclaration(ctx, var);
                    if (decl != null && decl.getInitializer() != null) {
                        return traceOrigin(ctx, cu, decl.getInitializer(), callGraph, depth + 1);
                    }
                    return UNRESOLVED;
                }
                return UNRESOLVED; // FIELD 成员
            }
        }
        return UNRESOLVED;
    }

    private static String traceCallers(AnalysisContext ctx, CompilationUnitTree calleeCu, ExecutableElement callee,
                                       int paramIndex, CallGraph callGraph, int depth) {
        if (depth >= MAX_TRACE_DEPTH) return UNRESOLVED;
        boolean anyUpdate = false;
        for (ExecutableElement caller : callGraph.callers(callee)) {
            CompilationUnitTree callerCu = methodCu(ctx, caller);
            if (callerCu == null) callerCu = calleeCu;
            for (MethodInvocationTree call : findCalls(ctx, callerCu, caller, callee)) {
                if (call.getArguments().size() <= paramIndex) continue;
                ExpressionTree arg = call.getArguments().get(paramIndex);
                String r = traceOrigin(ctx, callerCu, arg, callGraph, depth + 1);
                if ("WRITE".equals(r)) return "WRITE";
                if ("UPDATE".equals(r)) anyUpdate = true;
            }
        }
        return anyUpdate ? "UPDATE" : UNRESOLVED;
    }

    /** 定位 caller 方法体内调用 callee 的 MethodInvocationTree。 */
    private static List<MethodInvocationTree> findCalls(AnalysisContext ctx, CompilationUnitTree cu,
                                                        ExecutableElement caller, ExecutableElement callee) {
        List<MethodInvocationTree> calls = new ArrayList<>();
        Tree root = methodTree(ctx, caller);
        if (!(root instanceof MethodTree)) root = findMethodTree(ctx, cu, caller);
        if (!(root instanceof MethodTree)) return calls;
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                Element e = ctx.resolve(cu, node);
                if (e instanceof ExecutableElement && ((ExecutableElement) e).equals(callee)) {
                    calls.add(node);
                }
                return super.visitMethodInvocation(node, p);
            }
        }.scan(root, null);
        return calls;
    }

    private static Tree methodTree(AnalysisContext ctx, ExecutableElement method) {
        TreePath p = methodPath(ctx, method);
        return p == null ? null : p.getLeaf();
    }

    private static CompilationUnitTree methodCu(AnalysisContext ctx, ExecutableElement method) {
        TreePath p = methodPath(ctx, method);
        return p == null ? null : p.getCompilationUnit();
    }

    private static TreePath methodPath(AnalysisContext ctx, ExecutableElement method) {
        try {
            return ctx.trees.getPath(method);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static MethodTree findMethodTree(AnalysisContext ctx, CompilationUnitTree cu, ExecutableElement target) {
        final MethodTree[] found = {null};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitMethod(MethodTree node, Void p) {
                if (found[0] == null) {
                    Element e = ctx.resolve(cu, node);
                    if (e instanceof ExecutableElement && ((ExecutableElement) e).equals(target)) found[0] = node;
                }
                return super.visitMethod(node, p);
            }
        }.scan(cu, null);
        return found[0];
    }

    /** 定位局部变量的 VariableTree（优先 getPath，退化到扫描所在方法体）。 */
    private static VariableTree findLocalDeclaration(AnalysisContext ctx, VariableElement var) {
        try {
            TreePath p = ctx.trees.getPath(var);
            if (p != null && p.getLeaf() instanceof VariableTree) return (VariableTree) p.getLeaf();
        } catch (RuntimeException ignored) {
            // fall through
        }
        Element enclosing = var.getEnclosingElement();
        if (!(enclosing instanceof ExecutableElement)) return null;
        Tree root = methodTree(ctx, (ExecutableElement) enclosing);
        if (!(root instanceof MethodTree)) return null;
        TreePath mp = methodPath(ctx, (ExecutableElement) enclosing);
        if (mp == null) return null;
        CompilationUnitTree cu = mp.getCompilationUnit();
        final VariableTree[] found = {null};
        new TreeScanner<Void, Void>() {
            @Override
            public Void visitVariable(VariableTree node, Void p) {
                if (found[0] == null) {
                    Element e = ctx.resolve(cu, node);
                    if (e != null && e.equals(var)) found[0] = node;
                }
                return super.visitVariable(node, p);
            }
        }.scan(root, null);
        return found[0];
    }

    private static int paramIndex(ExecutableElement method, VariableElement param) {
        List<? extends VariableElement> params = method.getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (params.get(i).equals(param)) return i;
        }
        return -1;
    }

    private static String camelToSnake(String camel) {
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

    /** 表达式解析为 JOOQ 表实例（本地变量/字段/静态字段）时返回表名，否则 null。 */
    private static String resolveTable(AnalysisContext ctx, CompilationUnitTree cu,
                                       ExpressionTree expr, Map<String, TypeElement> tables) {
        Element e = ctx.resolve(cu, expr);
        if (!(e instanceof VariableElement)) return null;
        Element typeEl = ctx.types.asElement(((VariableElement) e).asType());
        if (!(typeEl instanceof TypeElement)) return null;
        for (Map.Entry<String, TypeElement> entry : tables.entrySet()) {
            if (entry.getValue().equals(typeEl)) return entry.getKey();
        }
        return null;
    }

    /** 从 table.FIELD 的 TreePath 向上判定操作类型；无更强信号时默认 READ。 */
    private static String classify(TreePath path, MemberSelectTree mst) {
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

    private static int argIndex(MethodInvocationTree call, Tree arg) {
        List<? extends ExpressionTree> args = call.getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) == arg) return i;
        }
        return -1;
    }

    private static String methodName(MethodInvocationTree call) {
        ExpressionTree ms = call.getMethodSelect();
        if (ms instanceof IdentifierTree) return ((IdentifierTree) ms).getName().toString();
        if (ms instanceof MemberSelectTree) return ((MemberSelectTree) ms).getIdentifier().toString();
        return "";
    }

    /** 向上查找最近的 MethodTree 并解析为 ExecutableElement。 */
    private static ExecutableElement enclosingMethod(AnalysisContext ctx, CompilationUnitTree cu, TreePath path) {
        while (path != null) {
            if (path.getLeaf() instanceof MethodTree) {
                Element e = ctx.resolve(cu, path.getLeaf());
                if (e instanceof ExecutableElement) return (ExecutableElement) e;
            }
            path = path.getParentPath();
        }
        return null;
    }
}
