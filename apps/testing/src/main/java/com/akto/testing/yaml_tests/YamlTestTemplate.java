package com.akto.testing.yaml_tests;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.test_editor.Auth;
import com.akto.dto.test_editor.ExecutionResult;
import com.akto.dto.test_editor.ExecutorNode;
import com.akto.dto.test_editor.FilterNode;
import com.akto.dto.testing.TestResult;
import com.akto.rules.TestPlugin;
import com.akto.test_editor.auth.AuthValidator;
import com.akto.test_editor.execution.Executor;
import com.akto.testing.StatusCodeAnalyser;
import com.akto.utils.RedactSampleData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class YamlTestTemplate extends SecurityTestTemplate {

    private static final LoggerMaker loggerMaker = new LoggerMaker(YamlTestTemplate.class);

    public YamlTestTemplate(ApiInfo.ApiInfoKey apiInfoKey, FilterNode filterNode, FilterNode validatorNode, ExecutorNode executorNode, RawApi rawApi, Map<String, Object> varMap, Auth auth, String logId) {
        super(apiInfoKey, filterNode, validatorNode, executorNode ,rawApi, varMap, auth, logId);
    }

    @Override
    public boolean filter() {
        loggerMaker.infoAndAddToDb("filter started" + logId, LogDb.TESTING);
        List<String> authHeaders = AuthValidator.getHeaders(this.auth);
        loggerMaker.infoAndAddToDb("found authHeaders " + authHeaders + " " + logId, LogDb.TESTING);
        if (authHeaders != null && authHeaders.size() > 0) {
            this.varMap.put("auth_headers", authHeaders);
        }
        if (this.auth != null && this.auth.getAuthenticated() != null) {
            loggerMaker.infoAndAddToDb("validating auth, authenticated value is " + this.auth.getAuthenticated() + " " + logId, LogDb.TESTING);
            boolean validAuthHeaders = AuthValidator.validate(this.auth, this.rawApi);
            if (!validAuthHeaders) {
                loggerMaker.infoAndAddToDb("invalid auth, skipping filter " + logId, LogDb.TESTING);
                return false;
            }
        }
        boolean isValid = TestPlugin.validateFilter(this.getFilterNode(),this.getRawApi(), this.getApiInfoKey(), this.varMap, this.logId);
        loggerMaker.infoAndAddToDb("filter status " + isValid + " " + logId, LogDb.TESTING);
        return isValid;
    }

    @Override
    public List<ExecutionResult>  executor() {
        loggerMaker.infoAndAddToDb("executor started" + logId, LogDb.TESTING);
        if (this.auth != null && this.auth.getAuthenticated() != null && this.auth.getAuthenticated() == true) {
            loggerMaker.infoAndAddToDb("running noAuth check " + logId, LogDb.TESTING);
            ExecutionResult res = AuthValidator.checkAuth(this.auth, this.rawApi.copy());
            if(res.getSuccess()) {
                OriginalHttpResponse resp = res.getResponse();
                int statusCode = StatusCodeAnalyser.getStatusCode(resp.getBody(), resp.getStatusCode());
                if (statusCode >= 200 && statusCode < 300) {
                    loggerMaker.infoAndAddToDb("noAuth check failed, skipping execution " + logId, LogDb.TESTING);
                    return null;
                }
            }
        }
        List<ExecutionResult> results = new Executor().execute(this.executorNode, this.rawApi, this.varMap, this.logId);
        loggerMaker.infoAndAddToDb("execution result size " + results.size() +  " " + logId, LogDb.TESTING);
        return results;
    }

    @Override
    public List<TestResult> validator(List<ExecutionResult> attempts) {
        List<TestResult> testResults = new ArrayList<>();
        loggerMaker.infoAndAddToDb("validation started " + logId, LogDb.TESTING);
        for (ExecutionResult attempt: attempts) {
            String msg = RedactSampleData.convertOriginalReqRespToString(attempt.getRequest(), attempt.getResponse());
            RawApi testRawApi = new RawApi(attempt.getRequest(), attempt.getResponse(), msg);
            boolean vulnerable = TestPlugin.validateValidator(this.getValidatorNode(), this.getRawApi(), testRawApi , this.getApiInfoKey(), this.varMap, this.logId);
            if (vulnerable) {
                loggerMaker.infoAndAddToDb("found vulnerable " + logId, LogDb.TESTING);
            }
            double percentageMatch = TestPlugin.compareWithOriginalResponse(
                    this.rawApi.getOriginalMessage(), testRawApi.getOriginalMessage(), new HashMap<>()
            );

            // todo: fix errors
            TestResult testResult = new TestResult(
                    msg, this.getRawApi().getOriginalMessage(), new ArrayList<>(), percentageMatch, vulnerable, TestResult.Confidence.HIGH, null
            );

            testResults.add(testResult);
        }

        loggerMaker.infoAndAddToDb("test results size " + testResults.size() + " " + logId, LogDb.TESTING);

        return testResults;
    }
}