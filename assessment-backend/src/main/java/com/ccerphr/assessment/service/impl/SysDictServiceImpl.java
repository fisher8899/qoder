package com.ccerphr.assessment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ccerphr.assessment.entity.SysDict;
import com.ccerphr.assessment.mapper.SysDictMapper;
import com.ccerphr.assessment.service.SysDictService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements SysDictService {

    @Override
    public List<SysDict> getDictByType(String dictType) {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDict::getDictType, dictType);
        wrapper.eq(SysDict::getIsEnabled, 1);
        wrapper.orderByAsc(SysDict::getSortCode);
        return this.list(wrapper);
    }
}
