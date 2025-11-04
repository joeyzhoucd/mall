package com.mall.search.service;

import java.io.IOException;
import java.util.List;

public interface ProductSaveService {
    boolean productUp(List<Object> skuEsModels) throws IOException;
}

