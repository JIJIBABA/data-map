package com.example.service;

import com.example.entity.Order;
import com.example.entity.User;

public class OrderAssembler {
    public void assemble(Order order, User user) {
        order.setUserId(user.getId());
    }
}
