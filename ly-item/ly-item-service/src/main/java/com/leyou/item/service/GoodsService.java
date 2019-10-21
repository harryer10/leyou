package com.leyou.item.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.leyou.common.dto.CartDTO;
import com.leyou.common.enums.ExceptionEnum;
import com.leyou.common.exception.LyException;
import com.leyou.common.vo.PageResult;
import com.leyou.item.mapper.SkuMapper;
import com.leyou.item.mapper.SpuDetailMapper;
import com.leyou.item.mapper.SpuMapper;
import com.leyou.item.mapper.StockMapper;
import com.leyou.item.pojo.Sku;
import com.leyou.item.pojo.Spu;
import com.leyou.item.pojo.SpuDetail;
import com.leyou.item.pojo.Stock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GoodsService {
    @Autowired
    private SpuMapper spuMapper;

    @Autowired
    private SpuDetailMapper detailMapper;

    @Autowired
    private SkuMapper skuMapper;

    @Autowired
    private StockMapper stockMapper;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    public PageResult<Spu> querySpuByPage(Integer page, Integer rows, Boolean saleable, String key) {
        //分页
        PageHelper.startPage(page, rows);

        //过滤
        Example example = new Example(Spu.class);
        Example.Criteria criteria = example.createCriteria();

        //搜索字段过滤
        if (StringUtils.isNotBlank(key)){
            criteria.andLike("title", "%" + key + "%");
        }
        //上下架过滤
        if (saleable != null){
            criteria.andEqualTo("saleable", saleable);
        }
        //默认排序
        example.setOrderByClause("last_update_time DESC");

        //查询
        List<Spu> spus = spuMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(spus)){
            throw new LyException(ExceptionEnum.GOODS_NOT_FOUND);
        }

        //解析分类和品牌名称
        loadCategoryAndBrandName(spus);

        //解析分页结果
        PageInfo<Spu> info = new PageInfo<>(spus);

        return new PageResult<>(info.getTotal(), spus);
    }

    private void loadCategoryAndBrandName(List<Spu> spus) {
        for (Spu spu : spus) {
            //处理分类名称
            List<String> names = categoryService.queryByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()))
                    .stream().map(c -> c.getName()).collect(Collectors.toList());
            spu.setCname(StringUtils.join(names, "/"));
            //处理品牌名称
            spu.setBname(brandService.queryById(spu.getBrandId()).getName());
        }
    }

    @Transactional
    public void saveGoods(Spu spu) {
        //新增spu
        spu.setId(null);
        spu.setCreateTime(new Date());
        spu.setLastUpdateTime(spu.getCreateTime());
        spu.setSaleable(true);
        spu.setValid(false);

        int count = spuMapper.insert(spu);
        if (count != 1){
            throw new LyException(ExceptionEnum.GOODS_SAVE_ERROR);
        }

        // 新增Detail
        SpuDetail detail = spu.getSpuDetail();
        detail.setSpuId(spu.getId());
        detailMapper.insert(detail);

        // 新增sku和库存
        saveSkuAndStock(spu);

        // 发送mq消息
        rabbitTemplate.convertAndSend("item.insert", spu.getId());
    }

    public SpuDetail queryDetailById(Long id) {
        SpuDetail detail = detailMapper.selectByPrimaryKey(id);
        if (detail == null){
            throw new LyException(ExceptionEnum.GOODS_DETAIL_NOT_FOUND);
        }
        return detail;
    }

    public List<Sku> querySkuBySpuId(Long spuId) {
        Sku sku = new Sku();
        sku.setSpuId(spuId);
        List<Sku> skuList = skuMapper.select(sku);
        if (CollectionUtils.isEmpty(skuList)){
            throw new LyException(ExceptionEnum.GOODS_SKU_NOT_FOUND);
        }

        List<Long> ids = skuList.stream().map(Sku::getId).collect(Collectors.toList());
        loadStockInSku(ids, skuList);
        return skuList;
    }

    @Transactional
    public void updateGoods(Spu spu) {
        if (spu.getId() == null){
            throw new LyException(ExceptionEnum.GOODS_ID_CANNOT_BE_NULL);
        }
        Sku sku = new Sku();
        sku.setSpuId(spu.getId());
        //查询sku
        List<Sku> skuList = skuMapper.select(sku);
        if (!CollectionUtils.isEmpty(skuList)){
            //删除sku
            skuMapper.delete(sku);
            //删除stock
            List<Long> ids = skuList.stream().map(Sku::getId).collect(Collectors.toList());
            stockMapper.deleteByIdList(ids);
        }
        //修改spu
        spu.setValid(null);
        spu.setSaleable(null);
        spu.setLastUpdateTime(new Date());
        spu.setCreateTime(null);

        int count = spuMapper.updateByPrimaryKeySelective(spu);
        if (count != 1){
            throw new LyException(ExceptionEnum.GOODS_UPDATE_ERROR);
        }
        // 修改detail
        count = detailMapper.updateByPrimaryKeySelective(spu.getSpuDetail());
        if (count != 1){
            throw new LyException(ExceptionEnum.GOODS_UPDATE_ERROR);
        }
        // 新增sku和stock
        saveSkuAndStock(spu);

        // 发送mq消息
        rabbitTemplate.convertAndSend("item.update", spu.getId());
    }

    private void saveSkuAndStock(Spu spu) {
        //定义库存集合
        List<Stock> stockList = new ArrayList<>();
        //新增sku
        int count;
        List<Sku> skus = spu.getSkus();
        for (Sku sku : skus) {
            sku.setCreateTime(new Date());
            sku.setLastUpdateTime(sku.getCreateTime());
            sku.setSpuId(spu.getId());

            count = skuMapper.insert(sku);
            if (count != 1){
                throw new LyException(ExceptionEnum.GOODS_SAVE_ERROR);
            }

            //新增库存
            Stock stock = new Stock();
            stock.setSkuId(sku.getId());
            stock.setStock(sku.getStock());

            stockList.add(stock);
        }

        //批量新增库存
        count = stockMapper.insertList(stockList);
        if (count != stockList.size()){
            throw new LyException(ExceptionEnum.GOODS_SAVE_ERROR);
        }
    }

    public Spu querySpuById(Long id) {
        // 查询spu
        Spu spu =  this.spuMapper.selectByPrimaryKey(id);
        if (spu == null){
            throw new LyException(ExceptionEnum.GOODS_NOT_FOUND);
        }
        // 查询sku
        spu.setSkus(querySkuBySpuId(id));
        // 查询detail
        spu.setSpuDetail(queryDetailById(id));
        return spu;
    }

    public List<Sku> querySkuByIds(List<Long> ids) {
        List<Sku> skus = this.skuMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(skus)) {
            throw new LyException(ExceptionEnum.GOODS_SKU_NOT_FOUND);
        }

        loadStockInSku(ids, skus);
        return skus;
    }

    private void loadStockInSku(List<Long> ids, List<Sku> skus) {
        //查询库存
        List<Stock> stockList = stockMapper.selectByIdList(ids);
        if (CollectionUtils.isEmpty(stockList)) {
            throw new LyException(ExceptionEnum.GOODS_STOCK_NOT_FOUND);
        }
        //我们把stock做成一个map，key：sku的Id，值是库存值
        Map<Long, Integer> stockMap = stockList.stream().
                collect(Collectors.toMap(Stock::getSkuId, Stock::getStock));
        skus.forEach(s -> s.setStock(stockMap.get(s.getId())));
    }

    @Transactional
    public void decreaseStock(List<CartDTO> carts) {
        for (CartDTO cart : carts) {
            //件库存
            int count = stockMapper.decreaseStock(cart.getSkuId(), cart.getNum());
            if (count != 1) {
                throw new LyException(ExceptionEnum.STOCK_NOT_ENOUGH);
            }
        }
    }
}
