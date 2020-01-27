package com.github.billyjulius.serenitycucumbertestlink;

import com.github.billyjulius.testlinkhelper.StepResult;
import com.github.billyjulius.testlinkhelper.TestLinkMain;
import net.thucydides.core.model.DataTable;
import net.thucydides.core.model.DataTableRow;
import net.thucydides.core.model.TestOutcome;
import net.thucydides.core.model.TestStep;
import net.thucydides.core.steps.StepEventBus;
import net.thucydides.core.util.EnvironmentVariables;
import net.thucydides.core.util.SystemEnvironmentVariables;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Base {

    protected TestOutcome testOutcome;
    protected String errorMessage = "";
    protected Boolean isSuccess = true;
    protected List<String> exampleValues = new ArrayList<String>();
    protected List<StepResult> stepResults =  new ArrayList<StepResult>();
    protected EnvironmentVariables variables = SystemEnvironmentVariables.createEnvironmentVariables();

    protected Pattern patternGherkin = Pattern.compile("^(Given|When|And|Then)");
    protected Pattern patternExample = Pattern.compile("^Example");

    Boolean TESTLINK_ENABLED;
    String TESTLINK_URL;
    String TESTLINK_KEY;

    String _PROJECTNAME;
    Integer _SUITEID;
    Integer _PROJECTID;
    Integer _VERSION;
    String _BUILDNAME;
    String _PLANNAME;
    String _USERNAME;
    String _TCSUMMARY = "";

    public Base() {
        this.TESTLINK_ENABLED = variables.getPropertyAsBoolean("testlink.enabled", false);
        this.TESTLINK_URL = variables.getProperty("testlink.url");
        this.TESTLINK_KEY = variables.getProperty("testlink.key");
        this._PROJECTID = Integer.parseInt(variables.getProperty("testlink.project.id"));
        this._PROJECTNAME = variables.getProperty("testlink.project.name");
        this._VERSION = variables.getPropertyAsInteger("testlink.project.version", 1);
        this._SUITEID = Integer.parseInt(variables.getProperty("testlink.project.suite.id"));
        this._BUILDNAME = variables.getProperty("testlink.build.name");
        this._PLANNAME = variables.getProperty("testlink.plan.name");
        this._USERNAME = variables.getProperty("testlink.username");
    }

    public void TestLinkIntegration() {
        if(this.TESTLINK_ENABLED) {
            this.testOutcome = GetTestOutcome();

            String TestCaseName =  this.testOutcome.getName();
            //  Suite ID from scenario name with pattern '[123456]'
            this._SUITEID = ParseSuiteID(TestCaseName);
            GetTestResult();
            GetTestErrorMessage();

            TestLinkMain testLinkMain = new TestLinkMain(TESTLINK_URL, TESTLINK_KEY);
            testLinkMain.Init(_PROJECTNAME, _PROJECTID, _VERSION, _BUILDNAME, _PLANNAME, _USERNAME);
            if(stepResults.size() > 0) {
                testLinkMain.Run(TestCaseName, _TCSUMMARY, this.isSuccess, this.errorMessage, this.stepResults, this._SUITEID);
            }
        }
    }

    private void GetTestResult() {
        List<TestStep> testStepList = this.testOutcome.getTestSteps();

        // Should contain string if scenario examples
        String outline = this.testOutcome.getDataDrivenSampleScenario();

        if(outline != "") {
            stepResults.clear();
            String[] outline_arr = outline.split("\n");

            TestStep lastStep = testStepList.get(testStepList.size()-1);
            List<TestStep> childrenSteps = lastStep.getChildren();

            for(Integer i=0; i<outline_arr.length; i++) {
                StepResult stepResult = new StepResult();
                stepResult.name = outline_arr[i];
                stepResult.status = childrenSteps.get(i).isSuccessful() ? "Success" : "Failed";
                stepResults.add(stepResult);
            }

            setSummaryFromDataTable();
            this.errorMessage = lastStep.getDescription() + System.lineSeparator() + System.lineSeparator();
        } else {
            // Non examples scenario
            for(TestStep testStep : testStepList) {
                Boolean storyStep = patternGherkin.matcher(testStep.getDescription()).find();

                // Normal Scenario
                if(storyStep) {
                    // If using rest-assured theres will be children contain request detail
                    // Should ignore every children with desc pattern not Given, When, Then
                    _TCSUMMARY = this.testOutcome.getName();

                    addStepResult(testStep);
                    addStepResultChidren(testStep);
                }
            }
        }
    }

    private TestOutcome GetTestOutcome() {
        List<TestOutcome> testOutcomeList= StepEventBus.getEventBus().getBaseStepListener().getTestOutcomes();

        TestOutcome testOutcome = testOutcomeList.get(testOutcomeList.size()-1);

        return testOutcome;
    }

    private void GetTestErrorMessage() {
        if(testOutcome.isFailure() || testOutcome.isError()) {
            errorMessage += testOutcome.getErrorMessage();
            isSuccess = false;
        }
    }

    private Integer ParseSuiteID(String TestCaseName) {
        Pattern pattern = Pattern.compile("\\[(\\d+)\\]");
        Matcher matcher = pattern.matcher(TestCaseName);

        if(matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return Integer.parseInt(variables.getProperty("testlink.project.suite.id"));
    }

    private void addStepResult(TestStep testStep) {
        StepResult stepResult = new StepResult();
        stepResult.name = testStep.getDescription();
        stepResult.status = testStep.isSuccessful() ? "Success" : "Failed";
        stepResults.add(stepResult);
    }

    private void addStepResultChidren(TestStep testStep) {
        // Should ignore every children with desc pattern not Given, When, Then
        // Because rest-assured will save request detail in children
        List<TestStep> childrenSteps = testStep.getChildren();
        for (TestStep testStep1 : childrenSteps) {
            Boolean childStoryStep = patternGherkin.matcher(testStep1.getDescription()).find();
            if(childStoryStep) {
                addStepResult(testStep1);
            }
        }
    }

    private void setSummaryFromDataTable() {
        // Set summary for examples scenario
        // Contain fields and value
        DataTable dataTable = this.testOutcome.getDataTable();
        List<String> exampleFields = dataTable.getHeaders();
        List<DataTableRow> exampleValuesRow = dataTable.getRows();

        if (this.exampleValues.size() == 0) {
            for (DataTableRow dataTableRow : exampleValuesRow) {
                List<String> values_temp = dataTableRow.getStringValues();
                for (String value : values_temp) {
                    this.exampleValues.add(value.toString());
                }
            }
        }

        if(_TCSUMMARY == "") {
            _TCSUMMARY += "fields = " + String.join(",", exampleFields) + "\n";
            _TCSUMMARY += "values = " + String.join(",", this.exampleValues) + "\n";
        }
    }
}