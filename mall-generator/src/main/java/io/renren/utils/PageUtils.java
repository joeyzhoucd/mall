package io.renren.utils;

import java.io.Serializable;
import java.util.List;

/**
 * åˆ†é¡µå·¥å…·ç±»
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016å¹´11æœˆ4æ—¥ ä¸‹åˆ12:59:00
 */
public class PageUtils implements Serializable {
	private static final long serialVersionUID = 1L;
	//æ€»è®°å½•æ•°
	private int totalCount;
	//æ¯é¡µè®°å½•æ•°
	private int pageSize;
	//æ€»é¡µæ•°
	private int totalPage;
	//å½“å‰é¡µæ•°
	private int currPage;
	//åˆ—è¡¨æ•°æ®
	private List<?> list;
	
	/**
	 * åˆ†é¡µ
	 * @param list        åˆ—è¡¨æ•°æ®
	 * @param totalCount  æ€»è®°å½•æ•°
	 * @param pageSize    æ¯é¡µè®°å½•æ•°
	 * @param currPage    å½“å‰é¡µæ•°
	 */
	public PageUtils(List<?> list, int totalCount, int pageSize, int currPage) {
		this.list = list;
		this.totalCount = totalCount;
		this.pageSize = pageSize;
		this.currPage = currPage;
		this.totalPage = (int)Math.ceil((double)totalCount/pageSize);
	}

	public int getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(int totalCount) {
		this.totalCount = totalCount;
	}

	public int getPageSize() {
		return pageSize;
	}

	public void setPageSize(int pageSize) {
		this.pageSize = pageSize;
	}

	public int getTotalPage() {
		return totalPage;
	}

	public void setTotalPage(int totalPage) {
		this.totalPage = totalPage;
	}

	public int getCurrPage() {
		return currPage;
	}

	public void setCurrPage(int currPage) {
		this.currPage = currPage;
	}

	public List<?> getList() {
		return list;
	}

	public void setList(List<?> list) {
		this.list = list;
	}
	
}
