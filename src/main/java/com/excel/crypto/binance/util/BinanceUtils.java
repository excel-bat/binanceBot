package com.excel.crypto.binance.util;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Spliterators;
import java.util.stream.Collectors;

import com.binance.api.client.constant.BinanceKeyUtil;
import org.apache.commons.lang3.StringUtils;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.market.Candlestick;
import com.binance.api.client.domain.market.CandlestickInterval;
import com.binance.api.client.domain.market.TickerPrice;
import com.excel.crypto.binance.exceptions.GeneralException;

public class BinanceUtils {

	private static String API_KEY;
	private static String API_SECRET;

	private static BinanceApiRestClient client = null;
	private static BinanceApiWebSocketClient liveClient = null;
	


	public static List<String> getBitcoinSymbols() throws GeneralException {
		List<String> symbols = new LinkedList<String>();
		BinanceApiRestClient client = getRestClient();
		List<TickerPrice> prices = client.getAllPrices();
		String[] targetSymbol = ConfigUtils
				.readPropertyValue(ConfigUtils.CONFIG_BINANCE_SYMBOL).trim().split("\\s*,\\s*");;
		
		for (TickerPrice tickerPrice : prices) {
			if (StringUtils.endsWith(tickerPrice.getSymbol(), "USDT")) {
				if(targetSymbol.length > 0 ){
					if(Arrays.asList(targetSymbol)
							.stream()
							.filter(s -> tickerPrice.getSymbol().contains(s)).collect(Collectors.toList()).size() >0){
						symbols.add(tickerPrice.getSymbol());
					}
				}else{
					symbols.add(tickerPrice.getSymbol());
				}
			}
		}
		return symbols;
	}

	public static List<Candlestick> getCandlestickBars(String symbol,
			CandlestickInterval interval) throws GeneralException {
		try {
			return getRestClient().getCandlestickBars(symbol, interval);
		} catch (Exception e) {
			throw new GeneralException(e);
		}
	}

	public static List<Candlestick> getLatestCandlestickBars(String symbol,
			CandlestickInterval interval) throws GeneralException {
		try {
			return getRestClient().getCandlestickBars(symbol, interval, 2,
					null, null);
		} catch (Exception e) {
			throw new GeneralException(e);
		}
	}

	public static BinanceApiRestClient getRestClient() throws GeneralException {
		if (client == null) {
			try {
				BinanceApiClientFactory factory = BinanceApiClientFactory
						.newInstance(API_KEY, API_SECRET);
				client = factory.newRestClient();
			} catch (Exception e) {
				throw new GeneralException(e);
			}
		}
		return client;

	}
	
	public static BinanceApiWebSocketClient getWebSocketClient() throws GeneralException {
		if(liveClient == null) {
			try {
				BinanceApiClientFactory factory = BinanceApiClientFactory
						.newInstance(API_KEY, API_SECRET);
				liveClient = factory.newWebSocketClient();
			} catch (Exception e) {
				throw new GeneralException(e);
			}
		}
		return liveClient;		
	}
	
	public static void init(String binanceApiKey, String binanceApiSecret) throws GeneralException {
		if(StringUtils.isEmpty(binanceApiKey) || StringUtils.isEmpty(binanceApiSecret)) {
			throw new GeneralException("Binance API params cannot be empty; please check the config properties file");
		}
		
		API_KEY = BinanceKeyUtil.fetchKey();
		API_SECRET = BinanceKeyUtil.fetchSecKey();
	}

}
