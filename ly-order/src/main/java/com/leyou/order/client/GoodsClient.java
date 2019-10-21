package com.leyou.order.client;

import com.leyou.common.vo.PageResult;
import com.leyou.item.api.GoodsApi;
import com.leyou.item.pojo.Sku;
import com.leyou.item.pojo.Spu;
import com.leyou.item.pojo.SpuDetail;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.List;

@FeignClient(value = "item-service")
public interface GoodsClient extends GoodsApi {

}
