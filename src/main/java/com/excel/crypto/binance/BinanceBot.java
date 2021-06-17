package com.excel.crypto.binance;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.excel.crypto.binance.strategy.StrategyFactory;
import org.apache.commons.lang3.StringUtils;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;

import org.ta4j.core.Strategy;

import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.excel.crypto.binance.exceptions.GeneralException;
import com.excel.crypto.binance.trading.TradeTask;
import com.excel.crypto.binance.util.BinanceTa4jUtils;
import com.excel.crypto.binance.util.BinanceUtils;
import com.excel.crypto.binance.util.ConfigUtils;
import org.ta4j.core.num.Num;

public class BinanceBot {

	// Config params
	private static Integer PAUSE_TIME_MINUTES = 5;
	private static Boolean DO_TRADES = true;
	private static Integer MAX_SIMULTANEOUS_TRADES = 0;
	private static Double TRADE_SIZE_BTC;
	// 止损百分比
	private static Double STOPLOSS_PERCENTAGE = 5.00;
	private static Boolean DO_TRAILING_STOP = false;
	private static String TRADING_STRATEGY;

	// We will store time series for every symbol
	// 我们将为每个标的存储时间序列
	private static Map<String, BarSeries> timeSeriesCache = new HashMap<String, BarSeries>();

	private static Map<String, TradeTask> openTrades = new HashMap<String, TradeTask>();
	private static List<String> ordersToBeClosed = new LinkedList<String>();

	private static BinanceApiRestClient client;
	private static BinanceApiWebSocketClient liveClient;

	private static Integer closedTrades = 0;
	private static Double totalProfit = 0.0;

	private static CandlestickInterval interval = null;

	public static void main(String[] args) {
		System.setProperty("https.protocols", "TLSv1.1,TLSv1.2");
		Log.info(BinanceBot.class, "Initializing Binance bot");
		String configFilePath = System.getProperty("CONFIG_FILE_PATH");
		Log.info(BinanceBot.class,
				"=== Detected config file path (VM argument, optional): "
						+ configFilePath + " ===");
		if (StringUtils.isNotEmpty(configFilePath)) {
			ConfigUtils.setSystemConfigFilePath(configFilePath);
		}
		init();
		process();
	}
	
	private static void init() {
		// Pause time
		String strPauseTimeMinutes = ConfigUtils
				.readPropertyValue(ConfigUtils.CONFIG_PAUSE_TIME_MINUTES);
		if (StringUtils.isNotEmpty(strPauseTimeMinutes)
				&& StringUtils.isNumeric(strPauseTimeMinutes)) {
			PAUSE_TIME_MINUTES = Integer.valueOf(strPauseTimeMinutes);
		}

		// Candle time frame
		String candleInterval = ConfigUtils
				.readPropertyValue(ConfigUtils.CONFIG_BINANCE_TICK_INTERVAL);
		CandlestickInterval[] intervals = CandlestickInterval.values();
		for (CandlestickInterval _interval : intervals) {
			if (_interval.getIntervalId().equalsIgnoreCase(candleInterval)) {
				Log.info(BinanceBot.class, "Setting candlestick interval to: "
						+ candleInterval);
				interval = _interval;
			}
		}
		if (interval == null) {
			interval = CandlestickInterval.FOUR_HOURLY;
			Log.info(BinanceBot.class, "Using default candlestick interval: "
					+ CandlestickInterval.FOUR_HOURLY.getIntervalId());
		}

		// Trading settings
		String strDoTrades = ConfigUtils
				.readPropertyValue(ConfigUtils.CONFIG_TRADING_DO_TRADES);
		if ("false".equalsIgnoreCase(strDoTrades) || "0".equals(strDoTrades)) {
			DO_TRADES = false;
		}
		if (DO_TRADES) {
			MAX_SIMULTANEOUS_TRADES = Integer
					.valueOf(ConfigUtils
							.readPropertyValue(ConfigUtils.CONFIG_TRADING_MAX_SIMULTANEOUS_TRADES));
			STOPLOSS_PERCENTAGE = Double
					.valueOf(ConfigUtils
							.readPropertyValue(ConfigUtils.CONFIG_TRADING_STOPLOSS_PERCENTAGE));
			TRADE_SIZE_BTC = Double
					.valueOf(ConfigUtils
							.readPropertyValue(ConfigUtils.CONFIG_TRADING_TRADE_SIZE_BTC));
			String strDoTrailingStop = ConfigUtils
					.readPropertyValue(ConfigUtils.CONFIG_TRADING_DO_TRAILING_STOP);
			if ("true".equalsIgnoreCase(strDoTrailingStop)
					|| "1".equals(strDoTrailingStop)) {
				DO_TRAILING_STOP = true;
			}
			TRADING_STRATEGY = ConfigUtils
					.readPropertyValue(ConfigUtils.CONFIG_TRADING_STRATEGY);
		}

		try {
			BinanceUtils.init(ConfigUtils.readPropertyValue(ConfigUtils.CONFIG_BINANCE_API_KEY),
							ConfigUtils.readPropertyValue(ConfigUtils.CONFIG_BINANCE_API_SECRET));
			client = BinanceUtils.getRestClient();
			liveClient = BinanceUtils.getWebSocketClient();
		} catch (GeneralException e) {
			Log.severe(BinanceBot.class, "Unable to generate Binance clients!", e);
		}
	}
	
	private static void process() {
		try {
			List<String> symbols = BinanceUtils.getBitcoinSymbols();
			// 1.- Get ticks for every symbol and generate TimeSeries -> cache
			generateTimeSeriesCache(symbols);
			Long timeToWait = PAUSE_TIME_MINUTES * 60 * 1000L;
			if (timeToWait < 0) {
				timeToWait = 5 * 60 * 1000L;
			}
			while (true) {
				if (DO_TRADES) {
					Log.info(BinanceBot.class, "Open trades: "
							+ openTrades.keySet().size() + ". Symbols: "
							+ openTrades.keySet());
					if (DO_TRADES && closedTrades > 0) {
						Log.info(
								BinanceBot.class,
								"Closed trades: " + closedTrades
										+ ", total profit: "
										+ String.format("%.8f", totalProfit));
					}
					if (openTrades.keySet().size() >= MAX_SIMULTANEOUS_TRADES) {
						// We will not continue trading... avoid checking
						// symbols
						try {
							Thread.sleep(timeToWait);
						} catch (InterruptedException e) {
							Log.severe(BinanceBot.class, "Error sleeping", e);
						}
						continue;
					}
				}
				Long t0 = System.currentTimeMillis();
				// 2.- Get two last ticks for symbol and update cache.
				for (String symbol : symbols) {
					try {
						checkSymbol(symbol);
					} catch (Exception e) {
						Log.severe(BinanceBot.class, "Error checking symbol "
								+ symbol, e);
					}
				}
				Long t1 = System.currentTimeMillis() - t0;
				Log.info(BinanceBot.class, "All symbols analyzed, time elapsed: "
						+ (t1 / 1000.0) + " seconds.");

				try {
					Thread.sleep(timeToWait);
				} catch (InterruptedException e) {
					Log.severe(BinanceBot.class, "Error sleeping", e);
				}
			}
		} catch (Exception e) {
			Log.severe(BinanceBot.class, "Unable to get symbols", e);
		}
	}
	
	private static void checkSymbol(String symbol) {
		Log.debug(BinanceBot.class, "Checking symbol: " + symbol);
		Long t0 = System.currentTimeMillis();
		try {
			List<Candlestick> latestCandlesticks = BinanceUtils.getLatestCandlestickBars(symbol, interval);
			BarSeries series = timeSeriesCache.get(symbol);
			if (BinanceTa4jUtils.isSameTick(latestCandlesticks.get(1), series.getLastBar())) {
				// We are still in the same tick - just update the last tick with the fresh data
				updateLastTick(symbol, latestCandlesticks.get(1));
			} else {
				// We have just got a new tick - update the previous one and include the new tick
				updateLastTick(symbol, latestCandlesticks.get(0));
				series.addBar(BinanceTa4jUtils.convertToTa4jTick(latestCandlesticks.get(1)));
			}
			// Now check the TA strategy with the refreshed time series
			// 现在用刷新的时间序列检查 TA 策略
			int endIndex = series.getEndIndex();
			Strategy strategy = StrategyFactory.buildStrategy(series, TRADING_STRATEGY);
			if (strategy.shouldEnter(endIndex)) {
				// If we have an open trade for the symbol, we do not create a new one
				// 如果我们有交易品种的未平仓交易，我们不会创建新交易
				if (DO_TRADES && openTrades.get(symbol) == null) {					
					Num currentPrice = series.getLastBar().getClosePrice();
					Log.info(BinanceBot.class, "Bullish(看涨) signal for symbol: " + symbol + ", price: " + currentPrice);
					if (openTrades.keySet().size() < MAX_SIMULTANEOUS_TRADES) {
						// We create a new thread to trade with the symbol
						TradeTask tradeTask = new TradeTask(client, liveClient, symbol, currentPrice.doubleValue(),
								TRADE_SIZE_BTC, STOPLOSS_PERCENTAGE, DO_TRAILING_STOP);
						new Thread(tradeTask).start();
						openTrades.put(symbol, tradeTask);
						ordersToBeClosed.remove(symbol); // I know... just in case
					} else {
						Log.info(BinanceBot.class, "Skipping bullish signal for symbol  " + symbol + ", too many open trades");
					}
				}
			} else if (strategy.shouldExit(endIndex) && openTrades.get(symbol) != null && !DO_TRAILING_STOP) {
				// If we use trailing stop, the order will be closed when the moving stoploss is hit
				Log.info(BinanceBot.class, "Bearish signal for symbol: " + symbol + ", price: " + series.getLastBar().getClosePrice()
						+ "; marking it as closable.");
				// This object is scanned by the symbol trading thread
				ordersToBeClosed.add(symbol);
			}

			Log.debug(BinanceBot.class, "Symbol " + symbol + " checked in " + ((System.currentTimeMillis() - t0) / 1000.0) + " seconds");
		} catch (Exception e) {
			Log.severe(BinanceBot.class, "Unable to check symbol " + symbol, e);
		}
	}
	
	private static void updateLastTick(String symbol, Candlestick candlestick) {
		BarSeries series = timeSeriesCache.get(symbol);
		List<Bar> seriesTick = series.getBarData();
		seriesTick.remove(series.getEndIndex());
		seriesTick.add(BinanceTa4jUtils.convertToTa4jTick(candlestick));
	}
	
	private static void generateTimeSeriesCache(List<String> symbols) {
		for (String symbol : symbols) {
			Log.info(BinanceBot.class, "Generating time series for " + symbol);
			try {
				List<Candlestick> candlesticks = BinanceUtils.getCandlestickBars(symbol, interval);
				BarSeries series = BinanceTa4jUtils.convertToTimeSeries(candlesticks, symbol, interval.getIntervalId());
				timeSeriesCache.put(symbol, series);
			} catch (Exception e) {
				Log.severe(BinanceBot.class, "Unable to generate time series / strategy for " + symbol, e);
			}
		}
	}

	/**
	 * The open thread invokes this method to mark an order as closed
	 * 打开的线程调用此方法将订单标记为已关闭
	 * @param symbol
	 * @param profit
	 * @param errorMessage
	 */
	public static void closeOrder(String symbol, Double profit, String errorMessage) {
		if (StringUtils.isNotEmpty(errorMessage)) {
			Log.info(BinanceBot.class, "Trade " + symbol + " is closed due to error: " + errorMessage);
		} else {
			Log.info(BinanceBot.class, "Trade " + symbol + " is closed with profit: " + String.format("%.8f", profit));
			closedTrades++;
			totalProfit += profit;
		}
		openTrades.remove(symbol);
		ordersToBeClosed.remove(symbol);
	}

	/**
	 * The open thread invokes this method to check if an order should be closed
	 * 打开的线程调用此方法来检查是否应关闭订单
	 * @param symbol
	 * @return if it should be closed or not
	 */
	public static boolean shouldCloseOrder(String symbol) {
		if (ordersToBeClosed.contains(symbol)) {
			return true;
		}
		return false;
	}

}
