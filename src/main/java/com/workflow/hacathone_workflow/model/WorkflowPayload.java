package com.workflow.hacathone_workflow.model;

public class WorkflowPayload {
    private int creditScore;
    private long annualIncome;
    private long loanAmount;
    private int age;

    public WorkflowPayload() {}

    public WorkflowPayload(int creditScore, long annualIncome, long loanAmount, int age) {
        this.creditScore = creditScore;
        this.annualIncome = annualIncome;
        this.loanAmount = loanAmount;
        this.age = age;
    }

    public int getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(int creditScore) {
        this.creditScore = creditScore;
    }

    public long getAnnualIncome() {
        return annualIncome;
    }

    public void setAnnualIncome(long annualIncome) {
        this.annualIncome = annualIncome;
    }

    public long getLoanAmount() {
        return loanAmount;
    }

    public void setLoanAmount(long loanAmount) {
        this.loanAmount = loanAmount;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
