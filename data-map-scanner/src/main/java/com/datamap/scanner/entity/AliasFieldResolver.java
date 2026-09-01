package com.datamap.scanner.entity;

import com.datamap.scanner.javac.AnalysisContext;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实体演变别名解析器：识别表实体字段被复制/赋值到 DTO/VO/Req/Resp 等非表实体上的场景，
 * 构建传递闭合的别名映射。
 *
 * <p>识别的模式：
 * <ol>
 *   <li>{@code dto.setXxx(src.getYyy())}：单字段 setter←getter 复制。</li>
 *   <li>{@code dto.setXxx(src.isYyy())}：boolean is-前缀。</li>
 *   <li>{@code BeanUtils.copyProperties(src, dst)} / {@code BeanUtil.copyProperties(...)} 等批量拷贝：
 *       按 src、dst 同名字段建立别名（仅 src 上真实存在的字段）。</li>
 *   <li>{@code dst.setXxx(src.field)} 等参数为字段直接访问（非 getter）的场景：src 为表实体时，
 *       用 src.field 反推所属表字段，建立别名。</li>
 * </ol>
 *
 * <p>传递闭合：DTO→DTO 的二次赋值同样纳入。别名只允许从「表实体字段」或「已别名化的 DTO 字段」
 * 为根派生，避免两个无关实体同名字段误连。框架/基础类型（java.*、集合等）不作为别名 DTO。
 *
 * <p>输出：{@code aliasMap} 的 key 为 {@code "全限定DTO类型#snake字段名"}，value 为
 * {@code "表名.snake字段名"}，供 {@link com.datamap.scanner.usage.FieldAccessCollector} 在
 * 接收者非表实体时回退查表。
 */
public class AliasFieldResolver {

    /** key: 全限定类型名#snake字段名 ; value: 表名.snake字段名 */
    private final Map<String, String> aliasMap = new HashMap<>();

    /** DTO 全限定类型 → 已识别的别名字段集合（snake），用于传递闭合时快速判定根是否已别名化 */
    private final Map<String, Set<String>> dtoFields = new HashMap<>();

    /** DTO 全限定类型名 → 反查实体 TypeElement，用于解析批量拷贝时枚举字段 */
    private final Map<String, TypeElement> typeIndex = new HashMap<>();

    /** 表实体 TypeElement → 表名（MyBatis @TableName） */
    private final Map<TypeElement, String> entityTableByType;

    /** JOOQ 表名集合（大写），用于判断是否 JOOQ 派生类型；此处仅在字段命名上区分大小写 */
    private final Set<String> jooqTableNames;

    private final AnalysisContext ctx;

    private AliasFieldResolver(AnalysisContext ctx, Map<String, java.util.List<TypeElement>> entities,
                               Map<String, TypeElement> jooqTables) {
        this.ctx = ctx;
        this.entityTableByType = EntityResolver.reverseIndex(entities);
        this.jooqTableNames = new HashSet<>(jooqTables.keySet());
    }

    public static Map<String, String> resolve(AnalysisContext ctx,
            Map<String, java.util.List<TypeElement>> entities, Map<String, TypeElement> jooqTables) {
        AliasFieldResolver r = new AliasFieldResolver(ctx, entities, jooqTables);
        r.run();
        return r.aliasMap;
    }

    private void run() {
        // 索引所有项目内类型（非 java/集合），供接收者类型反查 TypeElement
        for (CompilationUnitTree cu : ctx.units) {
            for (Tree decl : cu.getTypeDecls()) {
                if (!(decl instanceof Tree)) continue;
                Element el = ctx.resolve(cu, decl);
                if (el instanceof TypeElement) {
                    TypeElement te = (TypeElement) el;
                    typeIndex.put(te.getQualifiedName().toString(), te);
                }
            }
        }

        // 不动点迭代：每轮扫描全部 CU 收集新别名，直到不再增长
        int prev = -1;
        while (aliasMap.size() > prev) {
            prev = aliasMap.size();
            for (CompilationUnitTree cu : ctx.units) {
                scanUnit(cu);
            }
        }
    }

    private void scanUnit(CompilationUnitTree cu) {
        new TreePathScanner<Void, Void>() {
            @Override public Void visitMethodInvocation(MethodInvocationTree node, Void p) {
                handleCopy(node, cu, getCurrentPath());
                return super.visitMethodInvocation(node, p);
            }
        }.scan(cu, null);
    }

    private void handleCopy(MethodInvocationTree call, CompilationUnitTree cu, TreePath callPath) {
        String name = methodName(call);
        if (name == null) return;

        // 模式 2：批量拷贝 BeanUtils.copyProperties(src, dst) / BeanUtil.copyProperties(src, dst, ...)
        if (isCopyProperties(name)) {
            List<? extends ExpressionTree> args = call.getArguments();
            if (args.size() >= 2) {
                TypeElement srcType = receiverOrArgType(args.get(0), cu, callPath);
                TypeElement dstType = receiverOrArgType(args.get(1), cu, callPath);
                if (srcType != null && dstType != null) {
                    bulkAlias(srcType, dstType);
                }
            }
            return;
        }

        // 模式 1/4：setter←getter 或 setter←字段访问
        if (!name.startsWith("set") || name.length() <= 3) return;
        if (call.getArguments().size() != 1) return;

        ExpressionTree arg = call.getArguments().get(0);
        // 接收者 dst 必须是 DTO（非表实体）
        TypeElement dstType = receiverType(call, cu, callPath);
        if (dstType == null || entityTableByType.containsKey(dstType)) return;
        if (!isProjectDto(dstType)) return;
        String dstField = snake(name.substring(3));

        // 情况 A：参数是 getter 调用 src.getXxx()
        if (arg instanceof MethodInvocationTree) {
            MethodInvocationTree getCall = (MethodInvocationTree) arg;
            String getter = methodName(getCall);
            String srcField = getterField(getter);
            if (srcField != null) {
                TypeElement srcType = receiverType(getCall, cu, new TreePath(callPath, getCall));
                if (srcType != null) {
                    addAlias(dstType, dstField, srcType, srcField);
                }
            }
            return;
        }

        // 情况 B：参数是字段直接访问 src.field —— 仅当 src 接收者或被访问字段来自表实体时
        String directField = directAccessField(arg);
        if (directField != null) {
            TypeElement srcType = directAccessType(arg, cu, callPath);
            if (srcType != null) {
                addAlias(dstType, dstField, srcType, directField);
            }
        }
    }

    // ---- 别名登记与传递 ----

    private void addAlias(TypeElement dstType, String dstField, TypeElement srcType, String srcField) {
        if (srcType == null) return;
        // 解析 src（表实体 或 已别名 DTO）对应的表字段
        String tableField = resolveSource(srcType, srcField);
        if (tableField == null) return; // src 既不是表实体也不是已知别名 DTO：不能派生

        String dstQn = dstType.getQualifiedName().toString();
        String key = dstQn + "#" + dstField;
        if (aliasMap.putIfAbsent(key, tableField) != null) return; // 已存在
        dtoFields.computeIfAbsent(dstQn, k -> new HashSet<>()).add(dstField);
    }

    private void bulkAlias(TypeElement srcType, TypeElement dstType) {
        // src 是表实体：按实体字段同名字段建别名
        if (entityTableByType.containsKey(srcType)) {
            String table = entityTableByType.get(srcType);
            for (VariableElement fe : fieldsOf(srcType)) {
                String snake = snake(fe.getSimpleName().toString());
                registerAlias(dstType, snake, table + "." + snake);
            }
            return;
        }
        // src 是已别名 DTO：把 src 的别名传递给 dst 同名字段
        String srcQn = srcType.getQualifiedName().toString();
        Set<String> aliased = dtoFields.get(srcQn);
        if (aliased == null) return;
        for (String snake : aliased) {
            String tableField = aliasMap.get(srcQn + "#" + snake);
            if (tableField != null) registerAlias(dstType, snake, tableField);
        }
    }

    private void registerAlias(TypeElement dstType, String dstField, String tableField) {
        String dstQn = dstType.getQualifiedName().toString();
        String key = dstQn + "#" + dstField;
        if (aliasMap.putIfAbsent(key, tableField) != null) return;
        dtoFields.computeIfAbsent(dstQn, k -> new HashSet<>()).add(dstField);
    }

    /** src 类型为表实体 → 返回 table.field；src 为已别名 DTO → 回溯其别名指向的 table.field；否则 null。 */
    private String resolveSource(TypeElement srcType, String srcField) {
        String table = entityTableByType.get(srcType);
        if (table != null) {
            return table + "." + srcField;
        }
        String srcQn = srcType.getQualifiedName().toString();
        String key = srcQn + "#" + srcField;
        return aliasMap.get(key); // 传递闭合：DTO 字段已别名化
    }

    // ---- 工具 ----

    private boolean isProjectDto(TypeElement te) {
        String qn = te.getQualifiedName().toString();
        if (qn.startsWith("java.") || qn.startsWith("javax.") || qn.startsWith("org.springframework.")) return false;
        if (qn.startsWith("java.util.") || qn.startsWith("java.lang.")) return false;
        return true;
    }

    private boolean isCopyProperties(String name) {
        return "copyProperties".equals(name) || "copyProperties".equals(name);
    }

    private String getterField(String getter) {
        if (getter == null) return null;
        if (getter.startsWith("get") && getter.length() > 3) return snake(getter.substring(3));
        if (getter.startsWith("is") && getter.length() > 2) return snake(getter.substring(2));
        return null;
    }

    private String directAccessField(ExpressionTree expr) {
        if (expr instanceof MemberSelectTree) {
            ExpressionTree sel = ((MemberSelectTree) expr).getExpression();
            String id = ((MemberSelectTree) expr).getIdentifier().toString();
            // 排除方法调用链残留（形如 a.b().c）
            if (sel instanceof IdentifierTree || sel instanceof MemberSelectTree) {
                // 仅接受字段直接访问 a.field 或 this.field
                return snake(id);
            }
        }
        return null;
    }

    private TypeElement directAccessType(ExpressionTree expr, CompilationUnitTree cu, TreePath parentPath) {
        if (!(expr instanceof MemberSelectTree)) return null;
        ExpressionTree recv = ((MemberSelectTree) expr).getExpression();
        if (recv instanceof IdentifierTree) {
            Element e = ctx.resolve(cu, recv);
            if (e instanceof VariableElement) {
                return asTypeElement(((VariableElement) e).asType());
            }
            if (e instanceof TypeElement) return (TypeElement) e;
        }
        return null;
    }

    private TypeElement receiverType(MethodInvocationTree call, CompilationUnitTree cu, TreePath callPath) {
        ExpressionTree ms = call.getMethodSelect();
        if (!(ms instanceof MemberSelectTree)) return null;
        ExpressionTree recv = ((MemberSelectTree) ms).getExpression();
        if (recv == null) return null;
        TreePath recvPath = new TreePath(callPath, recv);
        return receiverType(ctx, recvPath);
    }

    private TypeElement receiverOrArgType(ExpressionTree arg, CompilationUnitTree cu, TreePath parentPath) {
        TreePath p = new TreePath(parentPath, arg);
        TypeElement t = receiverType(ctx, p);
        if (t != null) return t;
        // 参数是变量引用
        Element e = ctx.resolve(cu, arg);
        if (e instanceof VariableElement) return asTypeElement(((VariableElement) e).asType());
        if (e instanceof TypeElement) return (TypeElement) e;
        return null;
    }

    public static TypeElement receiverType(AnalysisContext ctx, TreePath recvPath) {
        try {
            TypeMirror tm = ctx.trees.getTypeMirror(recvPath);
            if (tm != null && tm.getKind() != TypeKind.ERROR) {
                Element el = ctx.types.asElement(tm);
                if (el instanceof TypeElement) return (TypeElement) el;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        Element e = ctx.trees.getElement(recvPath);
        if (e instanceof VariableElement) {
            Element typeEl = ctx.types.asElement(((VariableElement) e).asType());
            if (typeEl instanceof TypeElement) return (TypeElement) typeEl;
        }
        if (e instanceof TypeElement) return (TypeElement) e;
        return null;
    }

    private TypeElement asTypeElement(TypeMirror t) {
        if (!(t instanceof DeclaredType)) return null;
        Element el = ((DeclaredType) t).asElement();
        return el instanceof TypeElement ? (TypeElement) el : null;
    }

    private List<VariableElement> fieldsOf(TypeElement te) {
        List<VariableElement> list = new ArrayList<>();
        for (Element e : ctx.elements.getAllMembers(te)) {
            if (e instanceof VariableElement) list.add((VariableElement) e);
        }
        return list;
    }

    private String methodName(MethodInvocationTree call) {
        ExpressionTree ms = call.getMethodSelect();
        if (ms instanceof MemberSelectTree) return ((MemberSelectTree) ms).getIdentifier().toString();
        if (ms instanceof IdentifierTree) return ((IdentifierTree) ms).getName().toString();
        return null;
    }

    private static String snake(String camel) {
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
}
