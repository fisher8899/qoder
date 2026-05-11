package com.ccerphr.assessment.controller;

import com.ccerphr.assessment.common.Result;
import com.ccerphr.assessment.entity.SysDict;
import com.ccerphr.assessment.service.SysDictService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dict")
public class SysDictController {

    private final SysDictService sysDictService;

    public SysDictController(SysDictService sysDictService) {
        this.sysDictService = sysDictService;
    }

    @GetMapping("/{dictType}")
    public Result<List<SysDict>> getDictByType(@PathVariable String dictType) {
        return Result.success(sysDictService.getDictByType(dictType));
    }
}
