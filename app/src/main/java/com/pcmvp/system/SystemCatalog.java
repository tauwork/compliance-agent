package com.pcmvp.system;

import java.util.List;

/** 菜单目录。角色的菜单权限只认这里的 key。 */
public final class SystemCatalog {

    public record Item(String key, String name) {}

    public record Group(String name, List<Item> items) {}

    public static final List<Group> GROUPS = List.of(
            new Group("工作台", List.of(new Item("/", "合规驾驶舱"))),
            new Group("政策管理", List.of(
                    new Item("/policies", "政策法规库"),
                    new Item("/rules", "AI规则库"))),
            new Group("合规运行", List.of(
                    new Item("/precheck", "前置预审"),
                    new Item("/inspection", "全量巡检"),
                    new Item("/risks", "风险预警"),
                    new Item("/tickets", "整改闭环"))),
            new Group("智能助手", List.of(new Item("/assistant", "对话助手"))),
            new Group("系统管理", List.of(
                    new Item("/system/users", "用户管理"),
                    new Item("/system/roles", "角色权限")))
    );

    public static final List<String> KEYS = GROUPS.stream().flatMap(g -> g.items().stream()).map(Item::key).toList();

    private SystemCatalog() {}
}
