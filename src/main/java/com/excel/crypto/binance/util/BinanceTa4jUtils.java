package com.excel.crypto.binance.util;


import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;

import org.ta4j.core.*;

import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;


import com.binance.api.client.domain.market.Candlestick;

public class BinanceTa4jUtils {



	public static BarSeries convertToTimeSeries(
			List<Candlestick> candlesticks, String symbol, String period) {
		List<Bar> ticks = new LinkedList<Bar>();
		for (Candlestick candlestick : candlesticks) {
			ticks.add(convertToTa4jTick(candlestick));
		}
		return new BaseBarSeries(symbol + "_" + period, ticks);
	}

	public static Bar convertToTa4jTick(Candlestick candlestick) {
		ZonedDateTime closeTime = getZonedDateTime(candlestick.getCloseTime());
		Duration candleDuration = Duration.ofMillis(candlestick.getCloseTime()
				- candlestick.getOpenTime());
		
		return new BaseBar(candleDuration, closeTime, candlestick.getOpen(), candlestick.getHigh(), candlestick.getLow(), candlestick.getClose(), candlestick.getVolume());
	}

	public static ZonedDateTime getZonedDateTime(Long timestamp) {
		return ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp),
				ZoneId.systemDefault());
	}

	public static boolean isSameTick(Candlestick candlestick, Bar tick) {
		if (tick.getEndTime().equals(
				getZonedDateTime(candlestick.getCloseTime()))) {
			return true;
		}
		return false;
	}
	


}
