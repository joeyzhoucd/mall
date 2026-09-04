package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("product/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    
    @RequestMapping("/list/tree")
    public R list(@RequestParam Map<String, Object> params) {
        List<CategoryEntity> entities = categoryService.listAsTree();

        return R.ok().put("data", entities);
    }


    
    @RequestMapping("/info/{catId}")
    public R info(@PathVariable("catId") Long catId) {
        CategoryEntity category = categoryService.getById(catId);

        return R.ok().put("data", category);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody CategoryEntity category) {
        categoryService.save(category);

        return R.ok();
    }

    /**
     * 拖拽调整分类层级/排序后的批量保存。
     *
     * <h3>原来这里是 saveBatch，也就是 INSERT —— 这个接口从来没成功过</h3>
     * 调用方（旧后台的 category.vue 批量保存）发的是<b>已存在</b>的分类，
     * 每条都带着自己的 catId。而 saveBatch 是插入语义，
     * CategoryEntity 的 @TableId 没指定策略、项目也没配 id-type，
     * 走的是 MyBatis-Plus 默认的 ASSIGN_ID —— 它会带上传入的 id 去 INSERT，
     * 于是必然主键冲突。换句话说：拖拽排序点「批量保存」一定报错。
     *
     * 改成 updateBatchById（UPDATE 语义），这才是这个接口本来要做的事。
     *
     * <h3>为什么要显式拒绝没有 catId 的元素</h3>
     * updateBatchById 遇到 id 为空的元素会抛异常，但那个异常信息是
     * MyBatis-Plus 的内部报错，看不出是「调用方传错了」。
     * 更重要的是：这是一个<b>只应该改动已有分类</b>的接口，
     * 让它有任何创建行为都是越界的 —— 建分类走 /save。
     */
    @RequestMapping("/save/drag")
    public R saveDrag(@RequestBody List<CategoryEntity> categories) {
        if (categories == null || categories.isEmpty()) {
            return R.ok();
        }
        boolean missingId = categories.stream().anyMatch(c -> c.getCatId() == null);
        if (missingId) {
            return R.error("拖拽保存只能修改已有分类，请求里有元素缺少 catId");
        }

        categoryService.updateBatchById(categories);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody CategoryEntity category) {
        categoryService.updateById(category);

        return R.ok();
    }

    
    /**
     * 批量删除分类（逻辑删除，CategoryEntity 上有 @TableLogic）。
     *
     * <h3>为什么要拦住「还有子分类」的删除</h3>
     * 原实现直接 removeByIds。因为是逻辑删除，父分类只是被标记 deleted=1，
     * 而它的子分类<b>一条都没动</b> —— 它们的 parentCid 仍然指向那个已删除的父节点。
     * 树是从 parentCid=0 递归拼出来的，所以这些子分类会<b>从界面上整个消失</b>，
     * 但数据还在、商品仍然挂在上面。
     *
     * 这就是那种最难查的故障形状：没有任何报错，只是「分类不见了」，
     * 而且删父分类和子分类消失之间没有任何提示把两者联系起来。
     *
     * 所以要拒绝，并把挡路的子分类数量说出来 ——
     * 「有 4 个子分类」比「删除失败」有用得多。
     *
     * <h3>校验放在 service 里而不是这里</h3>
     * removeByIds 是在一把分布式锁里做的。如果在控制器里先查一遍再调它，
     * 「查」和「删」之间是没有锁保护的 —— 这中间有人新建一个子分类，
     * 检查就白做了。所以真正的把关在 CategoryServiceImpl.removeByIds 内部、
     * 锁拿到之后才做，这里只负责把异常翻译成给人看的响应。
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] catIds) {
        if (catIds == null || catIds.length == 0) {
            return R.ok();
        }

        try {
            categoryService.removeByIds(Arrays.asList(catIds));
        } catch (IllegalStateException e) {
            return R.error(e.getMessage());
        }
        return R.ok();
    }

}
