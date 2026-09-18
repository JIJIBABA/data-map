package com.datamap.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由兜底：前端是单页应用，浏览器直接访问 /tables、/tables/list 这类路由时
 * 没有对应静态文件，会被当 404。这里把这类请求 forward 给 index.html，由前端路由处理。
 *
 * <p><b>匹配范围刻意收窄</b>：只匹配「单段不含点的路径」及其子路径（如 /tables、/tables/list），
 * 不匹配带扩展名的请求（如 /assets/index.js、/favicon.ico），这些静态资源交给
 * ResourceHandler 直接返回文件。若放任通配 /**，会拦截掉静态资源导致页面白屏。
 * 优先级低于 @RestController 的 /api/**，不影响后端接口。
 */
@Controller
public class SpaForwardController {

    @GetMapping(value = {
        "/",
        "/tables", "/tables/**",
        "/query", "/query/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
