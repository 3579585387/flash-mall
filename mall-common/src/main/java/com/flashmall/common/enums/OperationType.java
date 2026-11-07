package com.flashmall.common.enums;

/**
 * 数据库操作类型：决定自动填充哪些公共字段
 */
public enum OperationType {

    /** 新增：填充创建人、创建时间、更新人、更新时间 */
    INSERT,

    /** 修改：只填充更新人、更新时间 */
    UPDATE
}
