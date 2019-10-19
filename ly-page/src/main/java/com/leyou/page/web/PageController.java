package com.leyou.page.web;

import com.leyou.page.pojo.User;
import com.leyou.page.service.PageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@Controller
public class PageController {
    @Autowired
    private PageService pageService;

    @GetMapping("hello")
    public String sayHello(Model model){
        User user = new User();
        user.setName("Tom and Jerry");
        model.addAttribute("user", user);

        return "hello";
    }
    @GetMapping("item/{id}.html")
    public String toItemPge(@PathVariable("id") Long spuId, Model model){
        // 查询数据模型
        Map<String, Object> attributes = pageService.loadModel(spuId);
        // 准备数据模型
        model.addAllAttributes(attributes);
        // 返回视图
        return "item";
    }
}
