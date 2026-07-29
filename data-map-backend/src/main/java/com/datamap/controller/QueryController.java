package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.dto.FieldSearchResult;
import com.datamap.dto.PathQueryRequest;
import com.datamap.dto.PathResult;
import com.datamap.service.QueryService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    @Resource
    private QueryService queryService;

    @GetMapping("/search-fields")
    public Result<List<FieldSearchResult>> searchFields(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long projectId) {
        return Result.ok(queryService.searchFields(keyword, projectId));
    }

    @PostMapping("/find-path")
    public Result<PathResult> findPath(@RequestBody PathQueryRequest request) {
        return Result.ok(queryService.findPath(request));
    }
}
