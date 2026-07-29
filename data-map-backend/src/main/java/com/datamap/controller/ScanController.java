package com.datamap.controller;

import com.datamap.common.Result;
import com.datamap.dto.ScanResultDTO;
import com.datamap.service.ScanService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/scan")
public class ScanController {

    @Resource
    private ScanService scanService;

    @PostMapping("/result")
    public Result<Void> receiveScanResult(@RequestBody ScanResultDTO dto) {
        scanService.receiveScanResult(dto);
        return Result.ok();
    }
}
