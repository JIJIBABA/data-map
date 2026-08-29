package com.datamap.scanner.output;

import com.datamap.scanner.callgraph.CallGraph;
import com.datamap.scanner.callgraph.CallGraphBuilder;
import com.datamap.scanner.entry.EntryPoint;
import com.datamap.scanner.entry.EntryPointResolver;
import com.datamap.scanner.entity.EntityResolver;
import com.datamap.scanner.entity.FieldExtractor;
import com.datamap.scanner.input.DiffFilter;
import com.datamap.scanner.javac.AnalysisContext;
import com.datamap.scanner.model.*;
import com.datamap.scanner.relation.MybatisXmlRelationExtractor;
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

        List<ScanTable> tables = new ArrayList<>();
        for (Map.Entry<String, TypeElement> en : entities.entrySet()) {
            String tableName = en.getKey();
            if (tableFilter != null && !tableName.equals(tableFilter)) continue;
            List<ScanField> fields = FieldExtractor.extract(en.getValue(), ctx);
            List<ScanRelation> relations = xmlRelations.getOrDefault(tableName, List.of());
            List<UsageScenario> scenarios = new ArrayList<>();
            for (ScanField f : fields) {
                for (FieldAccess a : accesses.getOrDefault(tableName + "." + f.fieldName, List.of())) {
                    String op = OperationTypeClassifier.classify(a, graph, ctx);
                    String[] desc = MethodDescriptionExtractor.extract(a.method, ctx);
                    List<List<ExecutableElement>> paths = ChainTraverser.traverse(a.method, graph, entryMethods, 10);
                    if (!DiffFilter.affected(a.method, paths, changedFiles, ctx)) continue;
                    List<CallChainStep> chain = paths.isEmpty() ? List.of() : steps(paths.get(0));
                    EntryInfo entry = paths.isEmpty() ? null : entryOf(paths.get(0).get(0), entries);
                    scenarios.add(new UsageScenario(f.fieldName, op, qualified(a.method), desc[0], desc[1],
                        "", entry == null ? "" : entry.apiName, chain, entry));
                }
            }
            tables.add(new ScanTable(tableName, "", "", "MYSQL", fields, relations, scenarios));
        }
        ScanProject project = new ScanProject(appName, "", "");
        return new ScanResult(project, scanType, tables);
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

    private static List<CallChainStep> steps(List<ExecutableElement> path) {
        List<CallChainStep> out = new ArrayList<>();
        for (ExecutableElement m : path) {
            out.add(new CallChainStep(m.getEnclosingElement().toString(),
                m.getSimpleName().toString(), m.toString(), "SERVICE"));
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
