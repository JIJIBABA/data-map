package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.entity.Project;
import com.datamap.service.ProjectService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    @Resource
    private ProjectService projectService;

    @PostMapping
    public Result<Project> create(@RequestBody Project project) {
        return Result.ok(projectService.create(project));
    }

    @GetMapping
    public Result<List<Project>> list(@RequestParam(required = false) String keyword) {
        return Result.ok(projectService.list(keyword));
    }

    @GetMapping("/{id}")
    public Result<Project> getById(@PathVariable Long id) {
        return Result.ok(projectService.getById(id));
    }

    @PutMapping("/{id}")
    public Result<Project> update(@PathVariable Long id, @RequestBody Project project) {
        project.setId(id);
        return Result.ok(projectService.update(project));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.ok();
    }
}
