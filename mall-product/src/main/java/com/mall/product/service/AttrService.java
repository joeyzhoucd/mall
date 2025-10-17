package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.AttrEntity;
import com.mall.product.vo.AttrSaveRequestVO;

import java.util.List;
import java.util.Map;

/**
 * å•†å“å±žæ€§æœåŠ¡æŽ¥å£
 * ä¸“é—¨å¤„ç†è§„æ ¼å‚æ•°ç›¸å…³çš„ä¸šåŠ¡é€»è¾‘
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface AttrService extends IService<AttrEntity> {

    /**
     * èŽ·å–è§„æ ¼å‚æ•°åˆ†é¡µåˆ—è¡¨
     */
    PageUtils querySpecAttrPage(Map<String, Object> params);

    /**
     * æ–°å¢žè§„æ ¼å‚æ•°
     */
    void saveBaseAttr(AttrSaveRequestVO req);

    /**
     * ä¿®æ”¹è§„æ ¼å‚æ•°
     */
    void updateBaseAttr(AttrSaveRequestVO req);

    /**
     * èŽ·å–æœªå…³è”çš„å±žæ€§åˆ—è¡¨
     */
    List<AttrEntity> queryUnRelatedAttr(Long attrgroupId);

    // ==================== é”€å”®å±žæ€§ç›¸å…³æ–¹æ³• ====================

    /**
     * èŽ·å–é”€å”®å±žæ€§åˆ†é¡µåˆ—è¡¨
     */
    PageUtils querySaleAttrPage(Map<String, Object> params);

    /**
     * æ–°å¢žé”€å”®å±žæ€§
     */
    void saveSaleAttr(AttrSaveRequestVO req);

    /**
     * ä¿®æ”¹é”€å”®å±žæ€§
     */
    void updateSaleAttr(AttrSaveRequestVO req);

    /**
     * åˆ é™¤å±žæ€§åŠå…¶å…³è”å…³ç³»
     */
    void deleteAttrWithRelations(Long attrId);

    /**
     * æ‰¹é‡åˆ é™¤å±žæ€§åŠå…¶å…³è”å…³ç³»
     */
    void deleteAttrsWithRelations(Long[] attrIds);
}

