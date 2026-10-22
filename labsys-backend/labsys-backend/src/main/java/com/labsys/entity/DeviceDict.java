package com.labsys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 设备分类 / 存放位置的字典。
 * 之前这两个字段是自由文本，"网络设备" 和 "网络" 会变成两类，筛选就废了。
 * 没有单独做字典管理页：新增设备时用可输入的下来框，提交时后端顺手把新值记进来，
 * 字典跟着实际使用自然长大，不用再维护一个页面。
 */
@TableName("device_dict")
public class DeviceDict {

    public static final String CATEGORY = "CATEGORY";
    public static final String LOCATION = "LOCATION";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String type;
    private String value;

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getValue() { return value; }

    public void setId(Long id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setValue(String value) { this.value = value; }
}
