package com.akto.dto.testing;


import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiInfo;

import java.util.List;

public class CollectionWiseTestingEndpoints extends TestingEndpoints {

    private int apiCollectionId;

    public CollectionWiseTestingEndpoints() {
        super(Type.COLLECTION_WISE);
    }

    public CollectionWiseTestingEndpoints(int apiCollectionId) {
        super(Type.COLLECTION_WISE);
        this.apiCollectionId = apiCollectionId;
    }

    @Override
    public List<ApiInfo.ApiInfoKey> returnApis() {
        return SingleTypeInfoDao.instance.fetchEndpointsInCollection(apiCollectionId);
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

}