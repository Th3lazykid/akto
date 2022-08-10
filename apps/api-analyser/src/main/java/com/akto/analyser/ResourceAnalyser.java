package com.akto.analyser;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.dto.type.*;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.URLAggregator;
import com.akto.types.CappedSet;
import com.akto.util.JSONUtils;
import com.google.common.base.Charsets;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.*;
import org.bson.conversions.Bson;

import java.util.*;

public class ResourceAnalyser {
    BloomFilter<CharSequence> duplicateCheckerBF;
    BloomFilter<CharSequence> valuesBF;
    Map<String, SingleTypeInfo> countMap = new HashMap<>();

    int last_sync = 0;

    public ResourceAnalyser(int duplicateCheckerBfSize, double duplicateCheckerBfFpp, int valuesBfSize, double valuesBfFpp) {
        duplicateCheckerBF = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), duplicateCheckerBfSize, duplicateCheckerBfFpp
        );

        valuesBF = BloomFilter.create(
                Funnels.stringFunnel(Charsets.UTF_8), valuesBfSize, valuesBfFpp
        );

        syncWithDb();
    }

    private static final String X_FORWARDED_FOR = "x-forwarded-for";

    public URLTemplate matchWithUrlTemplate(int apiCollectionId, String url, String method) {
        APICatalog catalog = catalogMap.get(apiCollectionId);
        if (catalog == null) return null;
        URLStatic urlStatic = new URLStatic(url, URLMethods.Method.valueOf(method));
        for (URLTemplate urlTemplate: catalog.getTemplateURLToMethods().keySet()) {
            if (urlTemplate.match(urlStatic)) return urlTemplate;
        }
        return null;
    }


    public void analyse(HttpResponseParams responseParams) {
        if (responseParams.statusCode < 200 || responseParams.statusCode >= 300) return;

        if (countMap.keySet().size() > 200_000 || (Context.now() - last_sync) > 120) {
            syncWithDb();
        }


        HttpRequestParams requestParams = responseParams.getRequestParams();
        String urlWithParams = requestParams.getURL();

        // user id
        List<String> ipList = responseParams.getRequestParams().getHeaders().get(X_FORWARDED_FOR);
        if (ipList == null || ipList.isEmpty()) return;
        String userId = ipList.get(0);

        // get actual api collection id
        Integer apiCollectionId = requestParams.getApiCollectionId();
        String hostName = HttpCallParser.getHostName(requestParams.getHeaders());
        apiCollectionId = findTrueApiCollectionId(apiCollectionId, hostName, responseParams.getSource());

        if (apiCollectionId == null) return;

        String method = requestParams.getMethod();

        // get actual url (without any query params)
        URLStatic urlStatic = URLAggregator.getBaseURL(requestParams.getURL(), method);
        String baseUrl = urlStatic.getUrl();
        String url = baseUrl;

        // URLs received by api analyser are raw urls (i.e. not templatised)
        // So checking if it can be merged with any existing template URLs from db
        URLTemplate urlTemplate = matchWithUrlTemplate(apiCollectionId, url, method);
        if (urlTemplate != null) {
            url = urlTemplate.getTemplateString();
        }

        String combinedUrl = apiCollectionId + "#" + url + "#" + method;

        // different URL variables and corresponding examples. Use accordingly
        // urlWithParams : /api/books/2?user=User1
        // baseUrl: /api/books/2
        // url: api/books/INTEGER

        // analyse url params
        if (urlTemplate != null) {
            String[] tokens = APICatalogSync.tokenize(baseUrl); // tokenize only the base url
            SingleTypeInfo.SuperType[] types = urlTemplate.getTypes();
            int size = tokens.length;
            for (int idx=0; idx < size; idx++) {
                SingleTypeInfo.SuperType type = types[idx];
                String value = tokens[idx];
                if (type != null) { // only analyse the INTEGER/STRING part of the url
                    analysePayload(value, idx+"", combinedUrl, userId, url, method, -1,
                            apiCollectionId, false, true);
                }
            }
        }

        // analyse request payload
        BasicDBObject payload = RequestTemplate.parseRequestPayload(requestParams, urlWithParams); // using urlWithParams to extract any query parameters
        Map<String, Set<Object>> flattened = JSONUtils.flatten(payload);
        for (String param: flattened.keySet()) {
            for (Object val: flattened.get(param) ) {
                analysePayload(val, param, combinedUrl, userId, url,
                        method, -1, apiCollectionId, false, false);
            }
        }

        // analyse request headers
//        Map<String, List<String>> requestHeaders = requestParams.getHeaders();
//        for (String headerName: requestHeaders.keySet()) {
//            if (StandardHeaders.isStandardHeader(headerName)) continue;
//            List<String> headerValues = requestHeaders.get(headerName);
//            if (headerValues == null) {
//                headerValues = Collections.singletonList("null");
//            }
//            for (String headerValue: headerValues) {
//                analysePayload(headerValue, headerName, combinedUrl, userId, url,
//                        method, -1, apiCollectionId, true, false);
//            }
//        }
    }


    public void analysePayload(Object paramObject, String param, String combinedUrl, String userId,
                               String url, String method, int statusCode, int apiCollectionId, boolean isHeader,
                               boolean isUrlParam) {
        String paramValue = convertToParamValue(paramObject);
        if (paramValue == null) return ;

        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                url, method, statusCode, isHeader, param, SingleTypeInfo.GENERIC, apiCollectionId, isUrlParam
        );
        SingleTypeInfo singleTypeInfo = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0 , 0, 0, new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);

        // check if moved
        boolean moved = checkIfMoved(combinedUrl, param, paramValue);
        if (moved) return;

        // check if duplicate
        boolean isNew = checkDuplicate(userId, combinedUrl,param, paramValue);
        if (!isNew) return;

        // check if present
        boolean present = checkIfPresent(combinedUrl, param, paramValue);
        SingleTypeInfo singleTypeInfo1 = countMap.computeIfAbsent(singleTypeInfo.composeKey(), k -> singleTypeInfo);
        if (present) {
            markMoved(combinedUrl, param, paramValue);
            singleTypeInfo1.incPublicCount(1);
        } else {
            addToValueBF(combinedUrl, param, paramValue);
            singleTypeInfo1.incUniqueCount(1);
        }
    }



    public Map<Integer, APICatalog> catalogMap = new HashMap<>();

    public void buildCatalog() {
        List<ApiInfo.ApiInfoKey> apis = SingleTypeInfoDao.instance.fetchEndpointsInCollection(null);
        for (ApiInfo.ApiInfoKey apiInfoKey: apis) {

            int apiCollectionId = apiInfoKey.getApiCollectionId();
            String url = apiInfoKey.getUrl();
            String method = apiInfoKey.getMethod().name();

            APICatalog catalog = catalogMap.get(apiCollectionId);
            if (catalog == null) {
                catalog = new APICatalog(0, new HashMap<>(), new HashMap<>());
                catalogMap.put(apiCollectionId, catalog);
            }

            Map<URLTemplate,RequestTemplate> urlTemplates = catalog.getTemplateURLToMethods();
            Map<URLStatic, RequestTemplate> strictUrls = catalog.getStrictURLToMethods();

            if (APICatalog.isTemplateUrl(url)) {
                URLTemplate urlTemplate = APICatalogSync.createUrlTemplate(url, URLMethods.Method.valueOf(method));
                urlTemplates.put(urlTemplate, null);
            } else {
                URLStatic urlStatic = new URLStatic(url, URLMethods.Method.valueOf(method));
                strictUrls.put(urlStatic, null);
            }
        }

    }


    public void syncWithDb() {
        buildCatalog();
        populateHostNameToIdMap();

        List<WriteModel<SingleTypeInfo>> dbUpdates = getDbUpdatesForParamTypeInfo();
        System.out.println("total count: " + dbUpdates.size());
        countMap = new HashMap<>();
        last_sync = Context.now();
        if (dbUpdates.size() > 0) {
            SingleTypeInfoDao.instance.getMCollection().bulkWrite(dbUpdates);
        }
    }

    public List<WriteModel<SingleTypeInfo>> getDbUpdatesForParamTypeInfo() {
        List<WriteModel<SingleTypeInfo>> bulkUpdates = new ArrayList<>();
        for (SingleTypeInfo singleTypeInfo: countMap.values()) {
            if (singleTypeInfo.getUniqueCount() == 0 && singleTypeInfo.getPublicCount() == 0) continue;
            Bson filter = SingleTypeInfoDao.createFilters(singleTypeInfo);
            Bson update = Updates.combine(
                    Updates.inc(SingleTypeInfo._UNIQUE_COUNT, singleTypeInfo.getUniqueCount()),
                    Updates.inc(SingleTypeInfo._PUBLIC_COUNT, singleTypeInfo.getPublicCount())
            );
            bulkUpdates.add(new UpdateOneModel<>(filter, update, new UpdateOptions().upsert(true)));
        }

        return bulkUpdates;
    }

    public boolean checkDuplicate(String userId, String combinedUrl, String paramName, String paramValue) {
        String a = userId + "$" + combinedUrl + "$" + paramName + "$" + paramValue;
        return duplicateCheckerBF.put(a);
    }

    public boolean checkIfMoved(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue + "$moved";
        return valuesBF.mightContain(a);
    }

    public void markMoved(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue + "$moved";
        valuesBF.put(a);
    }

    public boolean checkIfPresent(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue;
        return valuesBF.mightContain(a);
    }

    public void addToValueBF(String combinedUrl, String paramName, String paramValue) {
        String a = combinedUrl + "$" + paramName + "$" + paramValue;
        valuesBF.put(a);
    }

    public String convertToParamValue(Object value) {
        if (value == null) return "null";
        return value.toString();
    }

    private Map<String, Integer> hostNameToIdMap = new HashMap<>();

    public Integer findTrueApiCollectionId(int originalApiCollectionId, String hostName, HttpResponseParams.Source source) {
        if (!HttpCallParser.useHostCondition(hostName, source)) {
            return originalApiCollectionId;
        }

        String key = hostName + "$" + originalApiCollectionId;
        Integer trueApiCollectionId = null;

        if (hostNameToIdMap.containsKey(key)) {
            trueApiCollectionId = hostNameToIdMap.get(key);
        }

        // todo: what if we don't find because of cycles

        return trueApiCollectionId;
    }

    public void populateHostNameToIdMap() {
        hostNameToIdMap = new HashMap<>();
        List<ApiCollection> apiCollectionList = ApiCollectionsDao.instance.findAll(new BasicDBObject());
        for (ApiCollection apiCollection: apiCollectionList) {
            String key = apiCollection.getHostName() + "$" + apiCollection.getVxlanId();
            hostNameToIdMap.put(key, apiCollection.getId());
        }
    }


}



