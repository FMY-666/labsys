package com.labsys.controller;

import com.labsys.common.Result;
import com.labsys.service.MenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/menus/tree")
    public Result tree(@RequestAttribute("userId") Long userId) {
        return new Result(200, "ok", menuService.treeOf(userId));
    }

    /** 全部菜单，只给角色管理页做授权勾选用（ADMIN） */
    @GetMapping("/menus/all")
    public Result all() {
        return new Result(200, "ok", menuService.allMenus());
    }
}
