package com.leyou.order.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.leyou.auth.pojo.UserInfo;
import com.leyou.common.dto.CartDTO;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.utils.IdWorker;
import com.leyou.common.vo.PageResult;
import com.leyou.item.pojo.Sku;
import com.leyou.order.client.AddressClient;
import com.leyou.order.client.GoodsClient;
import com.leyou.order.dto.AddressDTO;
import com.leyou.order.dto.OrderDTO;
import com.leyou.order.enums.OrderStatusEnum;
import com.leyou.order.enums.PayStateEnum;
import com.leyou.order.interceptor.UserInterceptor;
import com.leyou.order.mapper.OrderDetailMapper;
import com.leyou.order.mapper.OrderMapper;
import com.leyou.order.mapper.OrderStatusMapper;
import com.leyou.order.pojo.Order;
import com.leyou.order.pojo.OrderDetail;
import com.leyou.order.pojo.OrderStatus;
import com.leyou.order.utils.PayHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderService {
    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper detailMapper;

    @Autowired
    private OrderStatusMapper statusMapper;

    @Autowired
    private IdWorker idWorker;

    @Autowired
    private GoodsClient goodsClient;

    @Autowired
    private PayHelper payHelper;

    @Transactional
    public Long createOrder(OrderDTO orderDTO) {
         // 新增订单
         Order order = new Order();
         // 1.1 订单编号，基本信息
         long orderId = idWorker.nextId();
         order.setOrderId(orderId);
         order.setCreateTime(new Date());
         order.setPaymentType(orderDTO.getPaymentType());

         // 1.2 用户信息
         UserInfo user = UserInterceptor.getUser();
         order.setUserId(user.getId());
         order.setBuyerNick(user.getUsername());
         order.setBuyerRate(false);

         // 1.3 收货地址
         // 获取收货人地址
        Long id = orderDTO.getAddressId();
        AddressDTO addr = AddressClient.findById(id);
         order.setReceiver(addr.getName());
         order.setReceiverAddress(addr.getAddress());
         order.setReceiverCity(addr.getCity());
         order.setReceiverDistrict(addr.getDistrict());
         order.setReceiverMobile(addr.getPhone());
         order.setReceiverState(addr.getState());
         order.setReceiverZip(addr.getZipCode());

         // 1.4 金额
         // 把CartDTO转化为一个map，key是sku的id，值是num
         Map<Long, Integer> numMap = orderDTO.getCarts().stream()
                 .collect(Collectors.toMap(CartDTO::getSkuId, CartDTO::getNum));
         // 获取所有的sku的id
         Set<Long> ids = numMap.keySet();
         // 根据id查询
         List<Sku> skus = goodsClient.querySkuByIds(new ArrayList<>(ids));

         // 准备orerDetail集合
         List<OrderDetail> details = new ArrayList<>();

         long totalPay = 0L;
         for (Sku sku : skus) {
             // 计算商品总价
             totalPay += sku.getPrice() * numMap.get(sku.getId());

             // 封装orderDetail
             OrderDetail detail = new OrderDetail();
             detail.setImage(StringUtils.substringBefore(sku.getImages(), ","));
             detail.setNum(numMap.get(sku.getId()));
             detail.setOrderId(orderId);
             detail.setOwnSpec(sku.getOwnSpec());
             detail.setPrice(sku.getPrice());
             detail.setSkuId(sku.getId());
             detail.setTitle(sku.getTitle());
             details.add(detail);
         }

         order.setTotalPay(totalPay);
         // 实付金额 = 总金额 + 邮费 - 优惠金额
         order.setActualPay(totalPay + order.getPostFee() -0);

         // 1.5 order写入数据库
         int count = this.orderMapper.insertSelective(order);
         if (count != 1) {
             log.error("[创建订单] 创建订单失败，orderId:{}", orderId);
             throw new LyException(ExceptionEnum.CREATE_ORDER_ERROR);
         }

         // 2 新增订单详情
         count = detailMapper.insertList(details);
         if (count != details.size()){
             log.error("[创建订单] 创建订单失败，orderId:{}", orderId);
             throw new LyException(ExceptionEnum.CREATE_ORDER_ERROR);
         }

         //3新增订单状态
         OrderStatus orderStatus = new OrderStatus();
         orderStatus.setCreateTime(order.getCreateTime());
         orderStatus.setOrderId(orderId);
         orderStatus.setStatus(OrderStatusEnum.UN_PAY.value());
         count = statusMapper.insertSelective(orderStatus);
         if (count != 1) {
             log.error("[创建订单] 创建订单失败，orderId:{}", orderId);
             throw new LyException(ExceptionEnum.CREATE_ORDER_ERROR);
         }
         //4减库存
         List<CartDTO> cartDtos = orderDTO.getCarts();
         goodsClient.decreaseStock(cartDtos);
         log.info("订单号：{}", String.valueOf(orderId));
         return orderId;
    }

    public Order queryOrderById(Long id) {
        //查询订单
        Order order = orderMapper.selectByPrimaryKey(id);
        if (order == null) {
            throw new LyException(ExceptionEnum.ORDER_NOT_FOUND);
        }
        //查询订单详情
        OrderDetail detail = new OrderDetail();
        detail.setOrderId(id);
        List<OrderDetail> details = detailMapper.select(detail);
        if (CollectionUtils.isEmpty(details)) {
            throw new LyException(ExceptionEnum.ORDER_DETAIL_NOT_FOUND);
        }
        order.setOrderDetails(details);
        //查询订单状态
        OrderStatus orderStatus = statusMapper.selectByPrimaryKey(id);
        if (orderStatus == null) {
            // 不存在
            throw new LyException(ExceptionEnum.ORDER_STATUS_NOT_FOUND);
        }
        order.setOrderStatus(orderStatus);
        return order;
    }

    public String createPayUrl(Long orderId) {
        // 查询订单
        Order order = queryOrderById(orderId);
        // 判断订单状态
        Integer status = order.getOrderStatus().getStatus();
        if (status != OrderStatusEnum.UN_PAY.value()) {
            // 订单状态异常
            throw new LyException(ExceptionEnum.ORDER_STATUS_ERROR);
        }
        // 支付金额
        Long actualPay = 1L;//order.getActualPay();  //测试用，付款  1  分钱
        // 商品描述
        OrderDetail detail = order.getOrderDetails().get(0);
        String desc = detail.getTitle();
        return payHelper.createOrder(orderId, actualPay, desc);
    }

    public void handleNotify(Map<String, String> result) {
        // 1 数据校验
        payHelper.isSuccess(result);
        // 2 校验签名
        payHelper.isValidSign(result);
        // 3 校验金额
        String totalFeeStr = result.get("total_fee");
        String tradeNo = result.get("out_trade_no");
        if (StringUtils.isEmpty(totalFeeStr)||StringUtils.isEmpty(tradeNo)) {
            throw new LyException(ExceptionEnum.INVALID_ORDER_PARAM);
        }
        // 3.1 获取结果中的金额
        Long totalFee = Long.valueOf(totalFeeStr);
        // 3.2 获取订单金额
        Long orderId = Long.valueOf(tradeNo);
        Order order = orderMapper.selectByPrimaryKey(orderId);
        if (totalFee != /*order.getActualPay()*/1) {
            //金额不符
            throw new LyException(ExceptionEnum.INVALID_ORDER_PARAM);
        }
        // 4 修改订单状态
        OrderStatus status = new OrderStatus();
        status.setStatus(OrderStatusEnum.PAYED.value());
        status.setOrderId(orderId);
        status.setPaymentTime(new Date());
        int count = statusMapper.updateByPrimaryKeySelective(status);
        if (count != 1) {
            throw new LyException(ExceptionEnum.UPDATE_ORDER_STATUS_ERROR);
        }
        log.info("[订单回调]，订单支付成功！订单编号:{}", orderId);
    }

    public PayStateEnum queryOrderState(Long orderId) {
        // 查询订单状态
        OrderStatus orderStatus = statusMapper.selectByPrimaryKey(orderId);
        Integer status = orderStatus.getStatus();
        // 判断是否支付
        if (status != OrderStatusEnum.UN_PAY.value()) {
            // 如果已支付，真的是已支付
            return PayStateEnum.SUCCESS;
        }
        // 如果未支付，但其实不一定是未支付，必须去微信查询支付状态
        return payHelper.queryPayState(orderId);
    }
}
