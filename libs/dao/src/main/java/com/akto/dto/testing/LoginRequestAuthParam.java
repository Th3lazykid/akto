package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.util.JsonStringPayloadModifier;
import com.akto.util.TokenPayloadModifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LoginRequestAuthParam extends AuthParam {

    private Location where;

    private String key;
    private String value;
    private Boolean showHeader;

    public LoginRequestAuthParam() { }

    public LoginRequestAuthParam(Location where, String key, String value, Boolean showHeader) {
        this.key = key;
        this.value = value;
        this.where = where;
        this.showHeader = showHeader;
    }

    @Override
    public boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        return TokenPayloadModifier.tokenPayloadModifier(request, this.key, this.value, this.where);        
    }
    
    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        return TokenPayloadModifier.tokenPayloadModifier(request, this.key, null, this.where);        
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        if (this.key == null) return false;
        String k = this.key.toLowerCase().trim();
        Map<String, List<String>> headers = request.getHeaders();
        return headers.containsKey(k);
    }

    public Location getWhere() {
        return where;
    }

    public void setWhere(Location where) {
        this.where = where;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean getShowHeader() {
        return showHeader;
    }

    public void setShowHeader(Boolean showHeader) {
        this.showHeader = showHeader;
    }
}