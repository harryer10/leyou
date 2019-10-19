package com.leyou.item.web;

import com.leyou.item.pojo.SpecGroup;
import com.leyou.item.pojo.SpecParam;
import com.leyou.item.service.SpecificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("spec")
public class SpecificationController {
    @Autowired
    private SpecificationService specService;

    /**
     * 根据分类Id查询规格组
     * @param cid
     * @return
     */
    @GetMapping("groups/{cid}")
    public ResponseEntity<List<SpecGroup>> queryByCid(@PathVariable("cid") Long cid) {
        List<SpecGroup> list = specService.queryByCid(cid);
        return ResponseEntity.ok(list);
    }

    /**
     * 根据规格组Id，查询参数
     * @param gid
     * @return
     */
    @GetMapping("params/{gid}")
    public ResponseEntity<List<SpecParam>> queryParamByGid(@PathVariable("gid") Long gid) {
        return ResponseEntity.ok(specService.queryParamByGid(gid));
    }

    /**
     * 根据分类Id，查询参数
     * @param gid
     * @param cid
     * @param searching
     * @return
     */
    @GetMapping("params")
    public ResponseEntity<List<SpecParam>> queryParamList(
            @RequestParam(value = "gid", required = false) Long gid,
            @RequestParam(value = "cid", required = false) Long cid,
            @RequestParam(value = "searching", required = false) Boolean searching) {
        return ResponseEntity.ok(specService.queryParamList(gid, cid, searching));
    }
    @GetMapping("group")
    public  ResponseEntity<List<SpecGroup>> queryGroupByCid(@RequestParam("cid") Long cid) {
        return ResponseEntity.ok(specService.queryGroupByCid(cid));
    }
}
