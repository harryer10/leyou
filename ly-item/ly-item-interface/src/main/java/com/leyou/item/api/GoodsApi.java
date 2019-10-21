package com.leyou.item.api;

import com.leyou.common.vo.PageResult;
import com.leyou.common.dto.CartDTO;
import com.leyou.item.pojo.Sku;
import com.leyou.item.pojo.Spu;
import com.leyou.item.pojo.SpuDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public interface GoodsApi {

    @GetMapping("spu/page")
    PageResult<Spu> querySpuByPage(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "rows", defaultValue = "5") Integer rows,
            @RequestParam(value = "saleable", required = false) Boolean saleable,
            @RequestParam(value = "key", required = false) String key);

    @GetMapping("spu/detail/{id}")
    SpuDetail queryDetailById(@PathVariable("id") Long id);

    @GetMapping("sku/list")
    List<Sku> querySkuBySpuId(@RequestParam("id") Long spuId);

    @GetMapping("sku/list/ids")
    public List<Sku> querySkuByIds(@RequestParam("ids") List<Long> ids);

    @GetMapping("spu/{id}")
    public Spu querySpuById(@PathVariable("id") Long id);

    @PostMapping("stock/decrease")
    public Void decreaseStock(@RequestBody List<CartDTO> carts);
}
