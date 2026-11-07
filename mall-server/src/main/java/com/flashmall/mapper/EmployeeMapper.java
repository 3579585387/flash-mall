package com.flashmall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashmall.pojo.entity.Employee;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商家账号
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {
}
