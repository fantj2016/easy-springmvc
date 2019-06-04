package com.fantj.mvc.sample.service;


import com.fantj.mvc.framework.annotation.Service;
import com.fantj.mvc.sample.pojo.User;

@Service
public class UserServiceImpl implements UserService {

    public User getUser(){
        return new User("fantj","yyy");
    }
}
