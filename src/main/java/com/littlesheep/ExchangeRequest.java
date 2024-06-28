package com.littlesheep;

public class ExchangeRequest {
    private final double currency;
    private final double points;
    private final boolean currencyToPoints;

    public ExchangeRequest(double currency, double points, boolean currencyToPoints) {
        this.currency = currency;
        this.points = points;
        this.currencyToPoints = currencyToPoints;
    }

    public double getCurrency() {
        return currency;
    }

    public double getPoints() {
        return points;
    }

    public boolean isCurrencyToPoints() {
        return currencyToPoints;
    }
}
