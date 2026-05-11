package com.ccerphr.assessment.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ccerphr.assessment.entity.SysUserPermission;
import com.ccerphr.assessment.mapper.SysUserPermissionMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserPermissionMapper userPermissionMapper;

    public UserDetailsServiceImpl(SysUserPermissionMapper userPermissionMapper) {
        this.userPermissionMapper = userPermissionMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LambdaQueryWrapper<SysUserPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUserPermission::getUserName, username);
        SysUserPermission user = userPermissionMapper.selectOne(wrapper);
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + user.getRoleCode())
        );
        return new User(String.valueOf(user.getId()), "", authorities);
    }
}
