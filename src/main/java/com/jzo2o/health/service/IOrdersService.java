package com.jzo2o.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.health.model.domain.Orders;
import com.jzo2o.health.model.dto.request.OrdersCancelReqDTO;
import com.jzo2o.health.model.dto.request.OrdersPageQueryReqDTO;
import com.jzo2o.health.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.health.model.dto.response.*;

import java.util.List;

/**
 * @author zwy
 * @version 1.0
 * @description: TODO
 * @date 2024/5/22 16:38
 */
public interface IOrdersService extends IService<Orders> {

    /**
     * 用户预约下单
     * @param placeOrderReqDTO
     * @return
     */
    PlaceOrderResDTO place(PlaceOrderReqDTO placeOrderReqDTO);

    OrdersPayResDTO pay(Long id, PayChannelEnum tradingChannel);

    OrdersPayResDTO getPayResult(Long id);

    void cancel(OrdersCancelReqDTO ordersCancelReqDTO);

    void refund(OrdersCancelReqDTO ordersCancelReqDTO);

    List<Orders> queryOverTimePayOrdersListByCount(int i);

    List<OrdersResDTO> pageQuery(Integer ordersStatus, Long sortBy);

    OrdersDetailResDTO detail(Long id);

    PageResult<OrdersResDTO> pageQueryAdmin(OrdersPageQueryReqDTO ordersPageQueryReqDTO);

    OrdersCountResDTO countByStatus();

    AdminOrdersDetailResDTO getOrderById(Long id);
}
