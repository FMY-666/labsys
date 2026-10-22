package com.labsys.dto;

import com.labsys.entity.Menu;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点。children 一开始就是空列表，前端拿到的叶子节点是 []，不用再判 null。
 */
public class MenuVO {

    private Long id;
    private Long parentId;
    private String menuName;
    private String path;
    private Integer sortOrder;
    private List<MenuVO> children = new ArrayList<>();

    public MenuVO(Menu menu) {
        this.id = menu.getId();
        this.parentId = menu.getParentId();
        this.menuName = menu.getMenuName();
        this.path = menu.getPath();
        this.sortOrder = menu.getSortOrder();
    }

    public Long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getMenuName() { return menuName; }
    public String getPath() { return path; }
    public Integer getSortOrder() { return sortOrder; }
    public List<MenuVO> getChildren() { return children; }
}
