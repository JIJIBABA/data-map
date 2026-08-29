package com.datamap.scanner.relation;

import com.datamap.scanner.model.ScanRelation;
import java.util.ArrayList;
import java.util.List;

public class RelationAssembler {
    public static List<ScanRelation> assemble(String sourceTableName, List<ScanRelation> raw) {
        List<ScanRelation> out = new ArrayList<>();
        for (ScanRelation r : raw) {
            out.add(new ScanRelation(r.sourceFieldName, r.targetTableName, r.targetFieldName,
                    r.relationType, r.methodSignature));
        }
        return out;
    }
}
