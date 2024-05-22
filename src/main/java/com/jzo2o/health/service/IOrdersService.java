package com.jzo2o.health.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.health.model.domain.Orders;
import com.jzo2o.health.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.health.model.dto.response.OrdersPayResDTO;
import com.jzo2o.health.model.dto.response.PlaceOrderResDTO;

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
}
