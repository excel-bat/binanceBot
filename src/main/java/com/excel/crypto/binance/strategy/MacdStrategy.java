package com.excel.crypto.binance.strategy;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;

/**
 * @author shanyb
 */
public class MacdStrategy {
    
    
    public static Strategy buildMacdStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        
        MACDIndicator macd = new MACDIndicator(closePrice, 12, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 9);
        SMAIndicator shortTermSMA = new SMAIndicator(closePrice, 50);
        SMAIndicator longTermSMA = new SMAIndicator(closePrice, 100);
        // First signal
        Rule entryRule = new CrossedUpIndicatorRule(macd, emaMacd)
                // Second signal
                .and(new OverIndicatorRule(shortTermSMA, longTermSMA));
        
        Rule exitRule = new CrossedDownIndicatorRule(macd, emaMacd);
        
        return new BaseStrategy(entryRule, exitRule);
    }
    
}
