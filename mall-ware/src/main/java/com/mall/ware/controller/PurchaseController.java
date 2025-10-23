package com.mall.ware.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.ware.entity.PurchaseEntity;
import com.mall.ware.service.PurchaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

@RestController
@RequestMapping("ware/purchase")
public class PurchaseController {

    @Autowired
    private PurchaseService purchaseService;

    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = purchaseService.queryPage(params);
        return R.ok().put("page", page);
    }

    @RequestMapping("/save")
    public R save(@RequestBody PurchaseEntity purchase) {
        // Set creation and update time
        java.util.Date now = new java.util.Date();
        purchase.setCreateTime(now);
        purchase.setUpdateTime(now);
        // Default status: new(0)
        if (purchase.getStatus() == null) {
            purchase.setStatus(0);
        }
        purchaseService.save(purchase);
        return R.ok();
    }

    @RequestMapping("/update")
    public R update(@RequestBody PurchaseEntity purchase) {
        // Set update time
        purchase.setUpdateTime(new java.util.Date());
        purchaseService.updateById(purchase);
        return R.ok();
    }

    @RequestMapping("/delete")
    public R delete(@RequestBody List<Long> ids) {
        purchaseService.removeByIds(ids);
        return R.ok();
    }

    // Merge purchase orders
    @PostMapping("/merge")
    public R merge(@RequestBody Map<String, Object> body) {
        // Get purchase detail ID list
        List<Long> detailIds;
        Object detailIdsObj = body.get("detailIds");
        if (detailIdsObj instanceof List) {
            List<?> raw = (List<?>) detailIdsObj;
            detailIds = raw.stream()
                    .map(o -> Long.valueOf(String.valueOf(o)))
                    .collect(Collectors.toList());
        } else {
            detailIds = Collections.emptyList();
        }
        Long purchaseId = null;
        if (body.get("purchaseId") != null && !String.valueOf(body.get("purchaseId")).trim().isEmpty()) {
            purchaseId = Long.valueOf(String.valueOf(body.get("purchaseId")));
        }
        purchaseService.merge(detailIds, purchaseId);
        return R.ok();
    }

    @PostMapping("/assign")
    public R assign(@RequestBody Map<String, Object> body) {
        Long purchaseId = Long.valueOf(String.valueOf(body.get("purchaseId")));
        Long assigneeId = Long.valueOf(String.valueOf(body.get("assigneeId")));
        String assigneeName = String.valueOf(body.get("assigneeName"));
        String phone = String.valueOf(body.get("phone"));
        purchaseService.assign(purchaseId, assigneeId, assigneeName, phone);
        return R.ok();
    }

    @PostMapping("/receive")
    public R receive(@RequestBody Map<String, Object> body) {
        List<Long> purchaseIds = (List<Long>) body.get("purchaseIds");
        Long receiverId = Long.valueOf(String.valueOf(body.get("receiverId")));
        String receiverName = String.valueOf(body.get("receiverName"));
        purchaseService.receive(purchaseIds, receiverId, receiverName);
        return R.ok();
    }

    @PostMapping("/finish")
    public R finish(@RequestBody Map<String, Object> body) {
        Long purchaseId = Long.valueOf(String.valueOf(body.get("purchaseId")));
        List<Long> successDetailIds = (List<Long>) body.get("successDetailIds");
        List<Long> failedDetailIds = (List<Long>) body.get("failedDetailIds");
        purchaseService.finish(purchaseId, successDetailIds, failedDetailIds);
        return R.ok();
    }
}