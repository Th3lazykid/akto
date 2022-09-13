package com.akto.dto;

public class RawApi {

    private OriginalHttpRequest request;
    private OriginalHttpResponse response;
    private String originalMessage;

    public RawApi(OriginalHttpRequest request, OriginalHttpResponse response, String originalMessage) {
        this.request = request;
        this.response = response;
        this.originalMessage = originalMessage;
    }

    public RawApi() { }

    public OriginalHttpRequest getRequest() {
        return request;
    }

    public void setRequest(OriginalHttpRequest request) {
        this.request = request;
    }

    public OriginalHttpResponse getResponse() {
        return response;
    }

    public void setResponse(OriginalHttpResponse response) {
        this.response = response;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }
}
