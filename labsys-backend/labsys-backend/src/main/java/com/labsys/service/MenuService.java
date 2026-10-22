package com.labsys.service;

import com.labsys.dto.MenuVO;
import com.labsys.entity.Menu;
import com.labsys.mapper.MenuMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MenuService {

    private final MenuMapper menuMapper;

    public MenuService(MenuMapper menuMapper) {
        this.menuMapper = menuMapper;
    }

    public List<MenuVO> treeOf(Long userId) {
        List<Menu> flat = menuMapper.findByUserId(userId);

        Map<Long, MenuVO> nodeById = new HashMap<>();
        List<MenuVO> roots = new ArrayList<>();

        // 第一遍：每条菜单先变成一个节点，按 id 登记到 Map 里
        for (Menu menu : flat) {
            nodeById.put(menu.getId(), new MenuVO(menu));
        }

        // 第二遍：顶级进结果列表，其余的挂到父节点的 children 下
        for (Menu menu : flat) {
            MenuVO node = nodeById.get(menu.getId());
            if (menu.getParentId() == 0) {
                roots.add(node);
            } else {
                MenuVO parent = nodeById.get(menu.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }

        return roots;
    }

    /** 全部菜单（不按用户过滤），给角色管理页的授权勾选框用 */
    public List<Menu> allMenus() {
        return menuMapper.findAll();
    }
}
