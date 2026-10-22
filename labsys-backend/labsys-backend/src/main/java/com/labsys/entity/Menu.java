package com.labsys.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("sys_menu")
public class Menu {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String menuName;
    private String path;
    private Integer sortOrder;
    private Integer visible;

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getMenuName() { return menuName; }
    public String getPath() { return path; }
    public Integer getSortOrder() { return sortOrder; }
    public Integer getVisible() { return visible; }

    public void setId(Long id) { this.id = id; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public void setMenuName(String menuName) { this.menuName = menuName; }
    public void setPath(String path) { this.path = path; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void setVisible(Integer visible) { this.visible = visible; }
}
