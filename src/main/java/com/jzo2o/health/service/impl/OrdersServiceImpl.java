package com.jzo2o.health.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.SelectById;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.api.trade.NativePayApi;
import com.jzo2o.api.trade.TradingApi;
import com.jzo2o.api.trade.dto.request.NativePayReqDTO;
import com.jzo2o.api.trade.dto.response.NativePayResDTO;
import com.jzo2o.api.trade.dto.response.TradingResDTO;
import com.jzo2o.api.trade.enums.PayChannelEnum;
import com.jzo2o.api.trade.enums.TradingStateEnum;
import com.jzo2o.common.expcetions.BadRequestException;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.model.CurrentUserInfo;
import com.jzo2o.common.model.msg.TradeStatusMsg;
import com.jzo2o.common.utils.BeanUtils;
import com.jzo2o.common.utils.ObjectUtils;
import com.jzo2o.health.enums.OrderPayStatusEnum;
import com.jzo2o.health.enums.OrderStatusEnum;
import com.jzo2o.health.mapper.OrdersMapper;
import com.jzo2o.health.model.UserThreadLocal;
import com.jzo2o.health.model.domain.*;
import com.jzo2o.health.model.dto.request.OrdersCancelReqDTO;
import com.jzo2o.health.model.dto.request.PlaceOrderReqDTO;
import com.jzo2o.health.model.dto.response.OrdersDetailResDTO;
import com.jzo2o.health.model.dto.response.OrdersPayResDTO;
import com.jzo2o.health.model.dto.response.OrdersResDTO;
import com.jzo2o.health.model.dto.response.PlaceOrderResDTO;
import com.jzo2o.health.properties.TradeProperties;
import com.jzo2o.health.service.*;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * @author zwy
 * @version 1.0
 * @description: TODO
 * @date 2024/5/22 16:39
 */
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper,Orders> implements IOrdersService {

    @Resource
    private IReservationSettingService reservationSettingService;
    @Resource
    private ISetmealService setmealService;
    @Resource
    private IMemberService memberService;
    @Resource
    private TradeProperties tradeProperties;
    @Resource
    private NativePayApi nativePayApi;
    @Resource
    private OrdersServiceImpl owner;
    @Resource
    private TradingApi tradingApi;
    @Resource
    private IOrdersCancelledService ordersCancelledService;

    @Resource
    private IOrdersRefundService ordersRefundService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PlaceOrderResDTO place(PlaceOrderReqDTO placeOrderReqDTO) {
        Integer number = reservationSettingService.getNumberByDate(placeOrderReqDTO.getReservationDate());
        if (number <= 0) {
            throw new BadRequestException("预约人数已满");
        }
        Orders orders = BeanUtils.toBean(placeOrderReqDTO, Orders.class);
        //订单状态和支付状态
        orders.setOrderStatus(OrderStatusEnum.NO_PAY.getStatus());
        orders.setPayStatus(OrderPayStatusEnum.NO_PAY.getStatus());
        //得到套餐信息未来保存到订单表中  -- id price name age img remark
        Setmeal setmeal = setmealService.getById(placeOrderReqDTO.getSetmealId());
        // 套餐校验
        if (ObjectUtils.isNull(setmeal)) {
            throw new BadRequestException("无当前套餐项，请选择其他套餐");
        }
        orders.setSetmealName(setmeal.getName());
        orders.setSetmealSex(setmeal.getSex());
        orders.setSetmealAge(setmeal.getAge());
        orders.setSetmealImg(setmeal.getImg());
        orders.setSetmealRemark(setmeal.getRemark());
        orders.setSetmealPrice(BigDecimal.valueOf(setmeal.getPrice()));

        //获取当前用户信息
        Member member = memberService.getById(UserThreadLocal.currentUserId());
        orders.setMemberId(member.getId());
        orders.setMemberPhone(member.getPhone());
        //根据时间戳排序
        orders.setSortBy(System.currentTimeMillis());
        //更新预约信息 可预约人数-1 已预约+1
        reservationSettingService.updateReservation(placeOrderReqDTO.getReservationDate());
        boolean save = save(orders);
        if (!save) {
            throw new CommonException("订单保存失败");
        }
        return new PlaceOrderResDTO(orders.getId());
    }

    @Override
    public OrdersPayResDTO pay(Long id, PayChannelEnum tradingChannel) {
        //查询订单是否存在
        Orders orders = getById(id);
        if (ObjectUtils.isNull(orders)) {
            throw new BadRequestException("订单不存在");
        }
        //查询是否已支付
        if (Objects.equals(orders.getPayStatus(), OrderPayStatusEnum.PAY_SUCCESS.getStatus())
                && ObjectUtils.isNotEmpty(orders.getTradingOrderNo())) {
            OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(orders, OrdersPayResDTO.class);
            //设置订单号 就是id
            ordersPayResDTO.setProductOrderNo(id);
            return ordersPayResDTO;
        } else {
            NativePayResDTO nativePayResDTO = generateQrCode(orders, tradingChannel);
            OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(nativePayResDTO, OrdersPayResDTO.class);
            return ordersPayResDTO;
        }


    }

    @Override
    public OrdersPayResDTO getPayResult(Long id) {
        // 校验Id是否存在
        Orders order = getById(id);
        if (ObjectUtils.isNull(order)) {
            throw new BadRequestException("该订单不存在");
        }
        //拿到交易单号
        Long tradingOrderNo = order.getTradingOrderNo();
        // 1.当支付状态为未支付且存在支付服务交易单号则远程调用支付服务进行查询
        if (Objects.equals(order.getPayStatus(), OrderPayStatusEnum.NO_PAY.getStatus())
            && ObjectUtils.isNotEmpty(tradingOrderNo)) {
            // 远程调用判断是否已支付
            TradingResDTO tradingResDTO = tradingApi.findTradResultByTradingOrderNo(tradingOrderNo);
            //如果已经支付，更新订单表
            if (ObjectUtils.isNotEmpty(tradingResDTO) &&
                    tradingResDTO.getTradingState() == TradingStateEnum.YJS) {
                TradeStatusMsg msg = TradeStatusMsg.builder()
                        .productOrderNo(order.getId())
                        .tradingChannel(tradingResDTO.getTradingChannel())
                        .statusCode(TradingStateEnum.YJS.getCode())
                        .tradingOrderNo(tradingResDTO.getTradingOrderNo())
                        .transactionId(tradingResDTO.getTransactionId())
                        .build();
                //更新订单成功支付信息
                owner.paySuccess(msg);
                // 返回数据支付成功
                OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(msg, OrdersPayResDTO.class);
                ordersPayResDTO.setPayStatus(OrderPayStatusEnum.PAY_SUCCESS.getStatus());
                return ordersPayResDTO;
            }
        }
        //已支付或者未付款则不需要更新订单表状态
        OrdersPayResDTO ordersPayResDTO = BeanUtils.toBean(order, OrdersPayResDTO.class);
        ordersPayResDTO.setProductOrderNo(id);//设置订单号
        return ordersPayResDTO;
    }

    @Override
    public void cancel(OrdersCancelReqDTO ordersCancelReqDTO) {
        Long id = ordersCancelReqDTO.getId();//获取订单id
        //查询订单
        Orders orders = baseMapper.selectById(id);
        if (ObjectUtils.isNull(orders)) {
            throw new CommonException("该订单不存在");
        }
        //校验订单状态
        if (!Objects.equals(orders.getOrderStatus(), OrderStatusEnum.NO_PAY.getStatus())) {
            throw new CommonException("该订单不可取消");
        }
        orders.setOrderStatus(OrderStatusEnum.CANCELLED.getStatus());
        int update = baseMapper.updateById(orders);
        if(update <= 0) {
            throw new BadRequestException("取消订单失败");
        }
        updateOrderCancelled(orders,ordersCancelReqDTO);
    }

    @Override
    public void refund(OrdersCancelReqDTO ordersCancelReqDTO) {
        //需要更新三个表的信息
        //判断支付状态并更新订单表
        Orders orders = baseMapper.selectById(ordersCancelReqDTO.getId());
        if (ObjectUtils.isNull(orders)) {
            throw new BadRequestException("该订单不存在");
        }
        //如果订单为待体检，支付状态为已支付则进行更新退款
        if (ObjectUtils.equal(orders.getOrderStatus(),OrderStatusEnum.WAITING_CHECKUP.getStatus())
                && ObjectUtils.equal(orders.getPayStatus(),OrderPayStatusEnum.PAY_SUCCESS.getStatus())) {
            //设置订单状态为已关闭
            orders.setOrderStatus(OrderStatusEnum.CLOSED.getStatus());
            //更新订单表支付状态为退款中
            orders.setPayStatus(OrderPayStatusEnum.REFUNDING.getStatus());
            boolean update = updateById(orders);
            if (!update) {
                throw new BadRequestException("更新订单支付状态失败");
            }
            //更新退款记录表
            updateOrdersRefund(orders);
            //更新订单取消表
            updateOrderCancelled(orders, ordersCancelReqDTO);
            //启动新线程退款

        }
    }

    @Override
    public List<Orders> queryOverTimePayOrdersListByCount(int i) {
        List<Orders> list = lambdaQuery()
                .eq(Orders::getPayStatus, OrderPayStatusEnum.NO_PAY.getStatus())
                .lt(Orders::getCreateTime, LocalDateTime.now().minusMinutes(15))
                .last("limit" + i)
                .list();
        return list;
    }

    @Override
    public List<OrdersResDTO> pageQuery(Integer ordersStatus, Long sortBy) {
        CurrentUserInfo currentUserInfo = UserThreadLocal.currentUser();
        Long id = currentUserInfo.getId();
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper<>();
        // 每次进行翻页时sortBy表示最后一个订单字段值
        queryWrapper.eq(Orders::getMemberId, id)
                .eq(ObjectUtils.isNotNull(ordersStatus), Orders::getOrderStatus, ordersStatus)
                .lt(ObjectUtils.isNull(sortBy), Orders::getSortBy, sortBy);
        Page<Orders> queryPage = new Page<>();
        // 按照sort字段进行降序排序
        queryPage.addOrder(OrderItem.desc("sort_by"));
        queryPage.setSearchCount(false);
        // 查询订单
        Page<Orders> ordersPage = baseMapper.selectPage(queryPage, queryWrapper);
        List<Orders> records = ordersPage.getRecords();
        List<OrdersResDTO> ordersResDTOS = BeanUtils.copyToList(records, OrdersResDTO.class);
        return ordersResDTOS;

    }

    @Override
    public OrdersDetailResDTO detail(Long id) {
        Orders orders = baseMapper.selectById(id);
        // 通过懒加载的方式来检查订单是否超时
        orders = cancelIfPayOvertime(orders);
        OrdersDetailResDTO ordersDetailResDTO = BeanUtils.toBean(orders, OrdersDetailResDTO.class);
        return ordersDetailResDTO;
    }

    private Orders cancelIfPayOvertime(Orders orders) {
        //超过15分钟不支付自动取消
        if(orders.getPayStatus() == OrderPayStatusEnum.NO_PAY.getStatus()
        && orders.getCreateTime().plusMinutes(15).isBefore(LocalDateTime.now())) {
            OrdersPayResDTO payResult = getPayResult(orders.getId());
            Integer payStatus = payResult.getPayStatus();
            //最新状态任然为未支付则取消订单
            if(Objects.equals(payStatus, OrderPayStatusEnum.NO_PAY.getStatus())) {
                //取消订单
                OrdersCancelReqDTO orderCancelDTO = BeanUtils.toBean(orders, OrdersCancelReqDTO.class);
                orderCancelDTO.setCancelReason("订单超时支付，自动取消");
                cancel(orderCancelDTO);
                orders = getById(orders.getId());
            }
        }
        return orders;
    }

    private void updateOrdersRefund(Orders orders) {
        OrdersRefund ordersRefund = BeanUtils.toBean(orders, OrdersRefund.class);
        ordersRefund.setRealPayAmount(orders.getSetmealPrice());
        boolean save = ordersRefundService.save(ordersRefund);
        if (!save) {
            throw new BadRequestException("更新退款记录失败");
        }
    }

    private void updateOrderCancelled(Orders orders, OrdersCancelReqDTO ordersCancelReqDTO) {
        //只要下单 不管是否支付都要-1人数，所以需要恢复这个人数
        reservationSettingService.recoverReservaton(orders.getReservationDate());
        //插入订单取消表的信息
        Long memberId = orders.getMemberId();
        Member member = memberService.getById(memberId);
        String memberName = member.getNickname();
        OrdersCancelled ordersCancelled = BeanUtils.toBean(orders, OrdersCancelled.class);
        //其中订单id可以转换， 取消人id和名称为orders中的member的信息 取消类型为1 用户取消
        ordersCancelled.setCancellerName(memberName);
        ordersCancelled.setCancellerId(memberId);
        ordersCancelled.setCancellerType(1);
        ordersCancelled.setCancelTime(LocalDateTime.now());
        ordersCancelled.setCancelReason(ordersCancelReqDTO.getCancelReason());
        boolean save = ordersCancelledService.saveOrUpdate(ordersCancelled);
        if(!save) {
            throw new BadRequestException("取消订单失败");
        }
    }

    /**
     * 更新订单表支付状态
     * @param msg
     */
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(TradeStatusMsg msg) {
        // 查询订单
        Orders orders = baseMapper.selectById(msg.getProductOrderNo());
        if (ObjectUtils.isNull(orders)) {
            throw new CommonException("订单不存在");
        }
        // 校验支付状态：如果不是待支付状态则不做处理
        if (ObjectUtils.notEqual(orders.getPayStatus(), OrderPayStatusEnum.NO_PAY.getStatus())) {
            return;
        }
        // 校验订单状态：如果不是待支付状态则不做处理
        if (ObjectUtils.notEqual(orders.getOrderStatus(), OrderStatusEnum.NO_PAY.getStatus())) {
            return;
        }
        // 校验第三方支付单号
        if (ObjectUtils.isEmpty(msg.getTransactionId())) {
            throw new CommonException("支付成功通知缺少第三方支付单号");
        }
        // 确定为未支付状态则更新订单的支付状态等信息
        boolean update = lambdaUpdate()
                .eq(Orders::getId, orders.getId())
                .set(Orders::getOrderStatus, OrderStatusEnum.WAITING_CHECKUP.getStatus())
                .set(Orders::getPayStatus, OrderPayStatusEnum.PAY_SUCCESS.getStatus())
                .set(Orders::getPayTime, LocalDateTime.now())
                .set(Orders::getTradingChannel, msg.getTradingChannel())
                .set(Orders::getTradingOrderNo, msg.getTradingOrderNo())
                .set(Orders::getTransactionId, msg.getTransactionId())
                .update();
        if (!update) {
//            log.info("更新订单:{}支付成功失败", orders.getId());
            throw new CommonException("更新订单" + orders.getId() + "支付成功失败");
        }
    }

    private NativePayResDTO generateQrCode(Orders orders, PayChannelEnum tradingChannel) {
        NativePayReqDTO nativePayReqDTO = new NativePayReqDTO();
        // 1.获取商户号  -> 根据请求的支付渠道判断需要哪个商户号
        Long enterpriseId = ObjectUtils.equal(tradingChannel, PayChannelEnum.WECHAT_PAY) ?
                tradeProperties.getWechatEnterpriseId() : tradeProperties.getAliEnterpriseId();
        nativePayReqDTO.setEnterpriseId(enterpriseId);
        // 2.业务系统标识
        nativePayReqDTO.setProductAppId("jzo2o.health");
        // 3.业务系统订单号
        nativePayReqDTO.setProductOrderNo(orders.getId());
        // 4.支付渠道
        nativePayReqDTO.setTradingChannel(tradingChannel);
        // 5.交易金额
        nativePayReqDTO.setTradingAmount(orders.getSetmealPrice());
        // 6.备注
        nativePayReqDTO.setMemo(orders.getSetmealName());
        // 7.是否切换支付渠道
        //当前订单中有支付渠道，并且和传入的支付渠道不同时需要改变
        if (ObjectUtil.isNotEmpty(orders.getTradingChannel())
                && ObjectUtil.notEqual(orders.getTradingChannel(), tradingChannel.toString())) {
            nativePayReqDTO.setChangeChannel(true);
        }
        // 8.远程调用请求生成二维码
        NativePayResDTO downLineTrading = nativePayApi.createDownLineTrading(nativePayReqDTO);
        // 9.如果生成二维码成功，需要拿到交易服务返回的信息，更新订单表
        if (ObjectUtils.isNotEmpty(downLineTrading)) {
            boolean update = owner.updateTradingData(downLineTrading);
            if (!update) {
                throw new CommonException("订单:" + orders.getId() + "请求支付更新交易单号失败");
            }
        }
        return downLineTrading;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateTradingData(NativePayResDTO downLineTrading) {
        //设置订单表交易单号、交易渠道
        boolean update = lambdaUpdate()
                .eq(Orders::getId, downLineTrading.getProductOrderNo())
                .set(Orders::getTradingOrderNo, downLineTrading.getTradingOrderNo())
                .set(Orders::getTradingChannel, downLineTrading.getTradingChannel())
                .update();
        return update;
    }
}
