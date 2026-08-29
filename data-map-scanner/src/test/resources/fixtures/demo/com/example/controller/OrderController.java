package com.example.controller;

import com.example.service.OrderService;
import javax.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order")
public class OrderController {
    @Resource
    private OrderService orderService;

    @PostMapping("/cancel")
    public void cancel(@RequestParam Long orderId) {
        orderService.cancelOrder(orderId);
    }
}
