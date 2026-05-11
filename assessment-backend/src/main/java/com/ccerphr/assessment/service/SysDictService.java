package com.ccerphr.assessment.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ccerphr.assessment.entity.SysDict;

import java.util.List;

public interface SysDictService extends IService<SysDict> {

    List<SysDict> getDictByType(String dictType);
}
