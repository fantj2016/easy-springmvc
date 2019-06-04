package com.fantj.mvc.sample.controller;

import com.fantj.mvc.framework.annotation.Autowired;
import com.fantj.mvc.framework.annotation.Controller;
import com.fantj.mvc.framework.annotation.RequestMapping;
import com.fantj.mvc.framework.servlet.ModelAndView;
import com.fantj.mvc.sample.pojo.User;
import com.fantj.mvc.sample.service.UserService;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/web")
public class UserController {

    @Autowired
    private UserService userService;

    @RequestMapping("/hello.json")
    public ModelAndView hello(){
        User user = userService.getUser();
        ModelAndView mv = new ModelAndView();
        Map<String, Object> map = new HashMap<>();
        map.put("name", user.getName());
        map.put("addr", user.getAddr());
        mv.setView("template.fantj");
        mv.setModel(map);
        return mv;
    }
}
