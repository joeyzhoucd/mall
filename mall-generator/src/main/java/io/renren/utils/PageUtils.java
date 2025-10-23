package io.renren.utils;

import java.io.Serializable;
import java.util.List;

/**
 * Page utilities for pagination
 */
public class PageUtils implements Serializable {
	private static final long serialVersionUID = 1L;
	// Total count
	private int totalCount;
	// Page size
	private int pageSize;
	// Total pages
	private int totalPage;
	// Current page number
	private int currPage;
	// List data
	private List<?> list;
	
	/**
	 * Constructor
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