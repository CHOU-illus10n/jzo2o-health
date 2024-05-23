package com.jzo2o.health.controller.user;

import com.jzo2o.health.model.dto.request.OrdersCancelReqDTO;
import com.jzo2o.health.service.IOrdersService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author itcast
 */
@RestController("userOrdersRefundController")
@RequestMapping("/user/orders")
@Api(tags = "用户端 - 取消订单退款相关接口")
public class OrdersRefundController {

    @Resource
    private IOrdersService ordersService;

    @PutMapping("/cancel")
    @ApiOperation("订单取消")
    public void cancel(@RequestBody OrdersCancelReqDTO ordersCancelReqDTO) {
        ordersService.cancel(ordersCancelReqDTO);
    }

    @PutMapping("/refund")
    @ApiOperation("订单退款")
    public void refund(@RequestBody OrdersCancelReqDTO ordersCancelReqDTO) {
        ordersService.refund(ordersCancelReqDTO);

    }
}
