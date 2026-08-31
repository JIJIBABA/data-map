package com.example.service.impl;

import com.example.entity.Order;
import com.example.mapper.OrderMapper;
import com.example.service.OrderService;
import javax.annotation.Resource;

public class OrderServiceImpl implements OrderService {
    @Resource
    private OrderMapper orderMapper;

    @Override
    public void createOrder(Order order) {
        order.setOrderStatus("CREATED");
        orderMapper.insert(order);
    }

    @Override
    public void cancelOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        order.setOrderStatus("CANCELLED");
        orderMapper.updateById(order);
    }
}
