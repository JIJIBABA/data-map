package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.entity.TableInfo;
import com.datamap.service.TableService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tables")
public class TableController {

    @Resource
    private TableService tableService;

    @GetMapping
    public Result<List<TableInfo>> list(@RequestParam(required = false) Long projectId,
                                         @RequestParam(required = false) String tableName) {
        return Result.ok(tableService.list(projectId, tableName));
    }

    @GetMapping("/{id}")
    public Result<TableInfo> getById(@PathVariable Long id) {
        return Result.ok(tableService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<TableInfo> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return Result.ok(tableService.updateComment(id, body.get("tableComment")));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        tableService.delete(id);
        return Result.ok();
    }
}
