package com.fantj.mvc.framework.servlet;

import lombok.Data;

import java.util.Map;

@Data
public class ModelAndView {
    /**
     * 页面模板
     */
    private String view;
    /**
     * 传输的数据
     */
    private Map<String, Object> model;

    public ModelAndView(String view, Map<String, Object> model) {
        this.view = view;
        this.model = model;
    }

    public ModelAndView() {
    }
}
