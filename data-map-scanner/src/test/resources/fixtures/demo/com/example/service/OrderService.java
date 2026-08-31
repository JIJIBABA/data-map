package com.example.service;

import com.example.entity.Order;

public interface OrderService {
    void createOrder(Order order);
    void cancelOrder(Long orderId);
}
