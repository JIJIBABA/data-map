package com.demo.service;

import com.demo.dao.OrderMapper;
import com.demo.entity.Order;
import com.demo.entity.OrderItem;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class OrderService {

    @Resource
    private OrderMapper orderMapper;

    /**
     * 用户下单时创建订单
     */
    public void createOrder(Order order, List<OrderItem> items) {
        orderMapper.insertOrder(order);
        for (OrderItem item : items) {
            orderMapper.insertOrderItem(item);
        }
    }

    /**
     * 用户取消订单时更新订单状态
     */
    public void cancelOrder(Long orderId) {
        Order order = orderMapper.selectOrderWithUser(orderId);
        if (order != null) {
            orderMapper.cancelOrder(orderId);
        }
    }
}
