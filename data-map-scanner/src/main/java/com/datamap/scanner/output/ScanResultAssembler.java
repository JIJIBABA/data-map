package com.datamap.scanner.output;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.entry.EntryPoint;
import com.datamap.scanner.entry.EntryPointResolver;
import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.entity.FieldExtractor;
import com.datamap.scanner.input.DiffFilter;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.jooq.JooqFieldAccess;
import com.datamap.scanner.jooq.JooqFieldAccessCollector;
import com.datamap.scanner.jooq.JooqFieldExtractor;
import com.datamap.scanner.jooq.JooqRelationExtractor;
import com.datamap.scanner.jooq.JooqTableResolver;
import com.datamap.scanner.model.*;
import com.datamap.scanner.relation.CrossEntityRelationExtractor;
import com.datamap.scanner.relation.MybatisXmlRelationExtractor;
import com.datamap.scanner.relation.PkFkRelationExtractor;
import com.datamap.scanner.traverse.ChainTraverser;
import com.datamap.scanner.usage.FieldAccess;
import com.datamap.scanner.usage.FieldAccessCollector;
import com.datamap.scanner.usage.MethodDescriptionExtractor;
import com.datamap.scanner.usage.OperationTypeClassifier;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ScanResultAssembler {
    public static ScanResult assemble(AnalysisContext ctx, List<Path> xmlFiles,
                                      String appName, String scanType, String tableFilter,
                                      Set<String> changedFiles) throws Exception {
        Map<String, TypeElement> entities = EntityResolver.resolve(ctx);
        CallGraph graph = CallGraphBuilder.build(ctx);
        Set<EntryPoint> entries = EntryPointResolver.resolve(ctx);
        Set<ExecutableElement> entryMethods = entries.stream().map(ep -> ep.method).collect(Collectors.toSet());
        Map<String, List<FieldAccess>> accesses = FieldAccessCollector.collect(ctx, entities);
        Map<String, List<ScanRelation>> xmlRelations = relationsBySourceTable(xmlFiles);

        Map<String, TypeElement> jooqTables = JooqTableResolver.resolve(ctx);
        Map<String, List<JooqFieldAccess>> jooqAccesses = JooqFieldAccessCollector.collect(ctx, jooqTables);
        Map<String, List<ScanRelation>> jooqRelations = JooqRelationExtractor.extract(ctx, jooqTables);

        Map<String, List<ScanRelation>> assignmentRelations =
                CrossEntityRelationExtractor.extract(ctx, entities, jooqTables);

        Map<String, List<ScanField>> fieldsByName = new HashMap<>();
        Map<String, ScanTable> tablesByName = new LinkedHashMap<>();
        for (Map.Entry<String, TypeElement> en : entities.entrySet()) {
            String tableName = en.getKey();
            if (tableFilter != null && !tableName.equals(tableFilter)) continue;
            List<ScanField> fields = FieldExtractor.extract(en.getValue(), ctx);
            List<ScanRelation> relations = mergeRelations(xmlRelations, assignmentRelations, tableName);
            List<UsageScenario> scenarios = new ArrayList<>();
            for (ScanField f : fields) {
                for (FieldAccess a : accesses.getOrDefault(tableName + "." + f.fieldName, List.of())) {
                    String op = OperationTypeClassifier.classify(a, graph, ctx);
                    String[] desc = MethodDescriptionExtractor.extract(a.method, ctx);
                    List<List<ExecutableElement>> paths = ChainTraverser.traverse(a.method, graph, entryMethods, 10);
                    if (!DiffFilter.affected(a.method, paths, changedFiles, ctx)) continue;
                    EntryInfo entry = paths.isEmpty() ? null : entryOf(paths.get(0).get(0), entries);
                    List<CallChainStep> chain = paths.isEmpty() ? List.of()
                        : steps(paths.get(0), entry == null ? "OTHER" : entry.type);
                    scenarios.add(new UsageScenario(f.fieldName, op, qualified(a.method), desc[0], desc[1],
                        "", entry == null ? "" : entry.apiName, chain, entry));
                }
            }
            fieldsByName.put(tableName, fields);
            tablesByName.put(tableName, new ScanTable(tableName, "", "", "MYSQL", fields, relations, scenarios));
        }

        for (Map.Entry<String, TypeElement> en : jooqTables.entrySet()) {
            String tableName = en.getKey();
            if (tableFilter != null && !tableName.equals(tableFilter)) continue;
            List<ScanField> fields = JooqFieldExtractor.extract(en.getValue(), ctx);
            List<ScanRelation> relations = mergeRelations(jooqRelations, assignmentRelations, tableName);
            List<UsageScenario> scenarios = jooqScenarios(tableName, fields, jooqAccesses, graph, entries,
                entryMethods, changedFiles, ctx);
            fieldsByName.put(tableName, fields);
            tablesByName.put(tableName, new ScanTable(tableName, "", "", "MYSQL", fields, relations, scenarios));
        }

        // pk_/fk_ 命名约定关联（需要全量表字段，两轮构建后追加），随后按边去重
        Map<String, List<ScanRelation>> pkFkRelations = PkFkRelationExtractor.extract(fieldsByName);
        for (Map.Entry<String, ScanTable> e : tablesByName.entrySet()) {
            ScanTable t = e.getValue();
            List<ScanRelation> merged = new ArrayList<>(t.relations);
            merged.addAll(pkFkRelations.getOrDefault(t.tableName, List.of()));
            e.setValue(new ScanTable(t.tableName, t.tableComment, t.schemaName, t.dbType,
                t.fields, dedupeRelations(merged), t.usageScenarios));
        }

        ScanProject project = new ScanProject(appName, "", "");
        return new ScanResult(project, scanType, new ArrayList<>(tablesByName.values()));
    }

    /** 组装 JOOQ 字段级场景（含表级 DELETE），镜像 MyBatis 场景的调用链/入口映射。 */
    private static List<UsageScenario> jooqScenarios(String tableName, List<ScanField> fields,
            Map<String, List<JooqFieldAccess>> accesses, CallGraph graph, Set<EntryPoint> entries,
            Set<ExecutableElement> entryMethods, Set<String> changedFiles, AnalysisContext ctx) {
        List<UsageScenario> scenarios = new ArrayList<>();
        for (ScanField f : fields) {
            for (JooqFieldAccess a : accesses.getOrDefault(tableName + "." + f.fieldName, List.of())) {
                addJooqScenario(scenarios, a, graph, entries, entryMethods, changedFiles, ctx);
            }
        }
        // 表级 DELETE（fieldName=""，operationType=DELETE）
        for (JooqFieldAccess a : accesses.getOrDefault(tableName + ".", List.of())) {
            addJooqScenario(scenarios, a, graph, entries, entryMethods, changedFiles, ctx);
        }
        return scenarios;
    }

    private static void addJooqScenario(List<UsageScenario> scenarios, JooqFieldAccess a, CallGraph graph,
            Set<EntryPoint> entries, Set<ExecutableElement> entryMethods, Set<String> changedFiles,
            AnalysisContext ctx) {
        String[] desc = MethodDescriptionExtractor.extract(a.method, ctx);
        List<List<ExecutableElement>> paths = ChainTraverser.traverse(a.method, graph, entryMethods, 10);
        if (!DiffFilter.affected(a.method, paths, changedFiles, ctx)) return;
        EntryInfo entry = paths.isEmpty() ? null : entryOf(paths.get(0).get(0), entries);
        List<CallChainStep> chain = paths.isEmpty() ? List.of()
            : steps(paths.get(0), entry == null ? "OTHER" : entry.type);
        scenarios.add(new UsageScenario(a.fieldName, a.operationType, qualified(a.method), desc[0], desc[1],
            "", entry == null ? "" : entry.apiName, chain, entry));
    }

    /** 关联关系按 FROM 左表（源表）归属，避免把每张表的关联都重复挂到所有表上。 */
    private static Map<String, List<ScanRelation>> relationsBySourceTable(List<Path> xmlFiles) throws Exception {
        Map<String, List<ScanRelation>> out = new LinkedHashMap<>();
        for (Path xml : xmlFiles) {
            for (Map.Entry<String, List<ScanRelation>> e : MybatisXmlRelationExtractor.extractBySourceTable(xml).entrySet()) {
                out.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
            }
        }
        return out;
    }

    private static List<ScanRelation> mergeRelations(Map<String, List<ScanRelation>> primary,
            Map<String, List<ScanRelation>> secondary, String tableName) {
        List<ScanRelation> primaryRels = primary.getOrDefault(tableName, List.of());
        List<ScanRelation> secondaryRels = secondary.getOrDefault(tableName, List.of());
        if (secondaryRels.isEmpty()) return primaryRels;
        List<ScanRelation> merged = new ArrayList<>(primaryRels);
        merged.addAll(secondaryRels);
        return merged;
    }

    /** 按 (sourceField, targetTable, targetField, relationType) 去重，保留首次出现（优先带方法签名的关联）。 */
    private static List<ScanRelation> dedupeRelations(List<ScanRelation> relations) {
        if (relations.size() <= 1) return relations;
        List<ScanRelation> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ScanRelation r : relations) {
            String key = r.sourceFieldName + " " + r.targetTableName + " "
                    + r.targetFieldName + " " + r.relationType;
            if (seen.add(key)) out.add(r);
        }
        return out;
    }

    private static List<CallChainStep> steps(List<ExecutableElement> path, String entryLayer) {
        List<CallChainStep> out = new ArrayList<>();
        for (int i = 0; i < path.size(); i++) {
            ExecutableElement m = path.get(i);
            String layer = i == 0 ? entryLayer : "SERVICE";
            out.add(new CallChainStep(m.getEnclosingElement().toString(),
                m.getSimpleName().toString(), m.toString(), layer));
        }
        return out;
    }

    private static EntryInfo entryOf(ExecutableElement m, Set<EntryPoint> entries) {
        for (EntryPoint ep : entries) if (ep.method.equals(m)) return ep.info;
        return null;
    }

    private static String qualified(ExecutableElement m) {
        return m.getEnclosingElement().toString() + "." + m.getSimpleName();
    }
}
