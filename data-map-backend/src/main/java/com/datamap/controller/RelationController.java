package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.dto.TableRelationVO;
import com.datamap.service.RelationService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/relations")
public class RelationController {

    @Resource
    private RelationService relationService;

    @GetMapping("/{tableId}")
    public Result<TableRelationVO> getRelations(
            @PathVariable Long tableId,
            @RequestParam(defaultValue = "all") String direction) {
        return Result.ok(relationService.getRelations(tableId, direction));
    }
}
