package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.entity.FieldUsageScenario;
import com.datamap.entity.TableField;
import com.datamap.service.FieldService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FieldController {

    @Resource
    private FieldService fieldService;

    @GetMapping("/tables/{tableId}/fields")
    public Result<List<TableField>> list(@PathVariable Long tableId) {
        return Result.ok(fieldService.listByTableId(tableId));
    }

    @PutMapping("/fields/{id}")
    public Result<TableField> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(fieldService.updateComment(id, body.get("fieldComment")));
    }

    @GetMapping("/fields/{id}/usage-scenarios")
    public Result<List<FieldUsageScenario>> getUsageScenarios(@PathVariable Long id) {
        return Result.ok(fieldService.getUsageScenarios(id));
    }
}
