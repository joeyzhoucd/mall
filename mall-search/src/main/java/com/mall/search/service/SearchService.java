package com.mall.search.service;

import com.mall.search.vo.SearchParam;
import com.mall.search.vo.SearchResult;

import java.io.IOException;

public interface SearchService {

    SearchResult search(SearchParam param) throws IOException;
}

