package com.excel.crypto.binance.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;

/**
 * @author shanyb
 */
public class StrategyFactory {
    
    public static String MACD_STRATEGY = "MACD";
    
    /**
     * 构建不同的策略
     * @param series
     * @param strategyCode
     * @return
     */
    public static Strategy buildStrategy(BarSeries series, String strategyCode) {
        //macd 策略
        if (MACD_STRATEGY.equals(strategyCode)) {
            return MacdStrategy.buildMacdStrategy(series);
        }
        return null;
    }
    
}
