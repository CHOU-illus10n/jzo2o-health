package com.jzo2o.health.handler;

import cn.hutool.core.collection.CollUtil;
import com.jzo2o.api.trade.RefundRecordApi;
import com.jzo2o.api.trade.dto.response.ExecutionResultResDTO;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.health.model.domain.Orders;
import com.jzo2o.health.model.dto.request.OrdersCancelReqDTO;
import com.jzo2o.health.properties.OrdersJobProperties;
import com.jzo2o.health.service.IOrdersService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 订单相关定时任务
 *
 * @author itcast
 * @create 2023/9/2 16:44
 **/
@Slf4j
@Component
public class OrdersHandler {

    @Resource
    private RefundRecordApi refundRecordApi;
    //解决同级方法调用，事务失效问题
    @Resource
    private OrdersHandler orderHandler;
    @Resource
    private OrdersJobProperties ordersJobProperties;
    @Resource
    private IOrdersService ordersService;

    /**
     * 支付超时取消订单
     * 每分钟执行一次
     */
    @XxlJob(value = "cancelOverTimePayOrder")
    public void cancelOverTimePayOrder() {
        List<Orders> orders =  ordersService.queryOverTimePayOrdersListByCount(100);
        if(CollUtil.isEmpty(orders)){
            XxlJobHelper.log("查询超时订单列表为空！");
            return;
        }
        for (Orders order : orders) {
            OrdersCancelReqDTO ordersCancelReqDTO = BeanUtils.toBean(orders, OrdersCancelReqDTO.class);
            ordersCancelReqDTO.setCancelReason("超过15分钟未支付，自动取消订单");
            ordersService.cancel(ordersCancelReqDTO);
        }
    }

    /**
     * 订单退款异步任务
     */
    @XxlJob(value = "handleRefundOrders")
    public void handleRefundOrders() {

    }

    /**
     * 订单退款处理
     *
     * @param id                    订单id
     * @param executionResultResDTO 第三方退款信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(Long id, ExecutionResultResDTO executionResultResDTO) {

    }
}
